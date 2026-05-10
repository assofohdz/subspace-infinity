---
name: lvl-format
description: Subspace/Continuum .lvl map file format — binary layout, embedded tileset, eLVL metadata, tile encoding, and how the existing loaders work.
---

# Subspace .lvl File Format

Source of truth: `infinity-server/src/main/java/infinity/map/` — `BitMap.java`, `LevelFile.java`, `LevelLoader.java`.

## File Layout (in order)

```
[BMP tileset]       optional — 304×160 px, 8-bit indexed, RLE-8 compressed
[eLVL metadata]     optional — only present when BMP reserved field == 49720
[tile data]         required — 4 bytes per non-empty tile, until EOF
```

### Detecting what's present

`BitMap.readBitMap()` reads the first 14-byte file header:
- Starts with `BM` → file has an embedded BMP tileset
- Starts with `elvl` → file has ONLY eLVL data (no BMP); `ELvlOffset = 0`
- BMP `m_size == 49718` (normal BMP, no eLVL)
- BMP `reserved` field == `49720` → eLVL follows the BMP; `ELvlOffset = 49720`

### Embedded Tileset (BMP section)

Standard Windows BMP, 8-bit color, RLE-8 compression:
- Width: 304 px (19 tiles × 16 px)
- Height: 160 px (10 tiles × 16 px)  
- 190 tiles total (tile IDs 1–190)
- **Black (0x000000) is the transparency color** — make alpha=0 when rendering

Tile layout in atlas:
- Tile ID `n` (1-indexed) → `tileIndex = n - 1`
- `col = tileIndex % 19`, `row = tileIndex / 19`
- Top-left pixel = `(col * 16, row * 16)`

`BitMap.getImageData()` returns `int[]` in ARGB format, stored **top-to-bottom**
(index 0 = top row of image, index `(height-1)*width` = bottom row).

### eLVL Metadata Section

Starts at offset `ELvlOffset` with a 12-byte header:
```
"elvl"       4 bytes  magic tag
total_size   4 bytes  LE int — total metadata section size in bytes
reserved     4 bytes  (0)
```
Followed by variable-length chunks until `total_size` bytes consumed:
```
type         4 bytes  ASCII tag ("ATTR", "REGN", or unknown)
chunk_len    4 bytes  LE int
data         chunk_len bytes
padding      0–3 bytes to align to 4-byte boundary
```

**ATTR chunk** — key=value string, e.g. `NAME=Trench Wars`
**REGN chunk** — RLE-encoded region bitmask (see `Region.java`)

### Tile Data Section

After the BMP (and optional eLVL), remaining bytes are tile entries:
```
4 bytes per tile (little-endian int):
  bits 31–24  tile ID (1–190; 0 = empty, skip)
  bits 23–12  y coordinate (0–1023)
  bits 11–0   x coordinate (0–1023)
```

Decode:
```java
int i = readLittleEndianInt();
int tile = (i >> 24) & 0xFF;
int y    = (i >> 12) & 0x3FF;
int x    =  i        & 0x3FF;
```

Map is 1024×1024 cells. Cells with tile == 0 are empty space.

## Key Classes

| Class | Role |
|-------|------|
| `BitMap` | Parses BMP header + RLE-8 pixel data; `getImageData()` → `int[] ARGB`; `readBitMap(trans)` — pass `true` to make black transparent |
| `LevelLoader` | JME3 `AssetLoader` for `.lvl`; returns `LevelFile`; falls back to `Textures/Tilesets/default.bmp` if no embedded BMP |
| `LevelFile` | Holds parsed map: `getMap()` → `short[1024][1024]`, `getTileSet()` → `java.awt.Image`, `getTileSetPixels()` → `int[]` ARGB, tile write/save support |
| `BitMapLoader` | JME3 `AssetLoader` for standalone `.bmp` files |

## Loading a Level (server side)

```java
// In MapSystem.initialize():
assetLoader.registerLoader(LevelLoader.class, "lvl", "lvz");
LevelFile res = (LevelFile) assetLoader.loadAsset("Maps/trench.lvl");
short[][] map = res.getMap();  // [x][y] tile IDs
```

## Extracting Tileset for JME3 Rendering (client side)

```java
assets.registerLoader(LevelLoader.class, "lvl");
LevelFile lvl = (LevelFile) assets.loadAsset("Maps/trench.lvl");

int[] pixels = lvl.getTileSetPixels();  // ARGB, top-to-bottom
int width = lvl.getTileSetImageWidth(); // 304
int height = lvl.getTileSetImageHeight(); // 160

// Flip vertically (JME3 V=0 is bottom; pixels[0] is top row)
// Treat black as transparent
ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
for (int row = height - 1; row >= 0; row--) {
    for (int col = 0; col < width; col++) {
        int argb = pixels[row * width + col];
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        int a = (r == 0 && g == 0 && b == 0) ? 0 : ((argb >> 24) & 0xFF);
        buf.put((byte)r).put((byte)g).put((byte)b).put((byte)a);
    }
}
buf.flip();
Image jmeImage = new Image(Image.Format.RGBA8, width, height, buf, ColorSpace.sRGB);
Texture2D tex = new Texture2D(jmeImage);
tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
tex.setMagFilter(Texture.MagFilter.Nearest);
```

See `BlockGeometryIndex.registerTileMaterialFromLevel()` for the live implementation.

## Tile Coordinate System

- Map origin: the `.lvl` stores coordinates as `(x, y)` both 0–1023
- In-world: loaded at a `worldOffset` (multiple of 1024) by `MapSystem`
- Tile `(x, y)` in the lvl → single world cell at `(worldOffset.x + x, 1, worldOffset.z + y)`
- Single Y layer: the block type carries both rendering and collision. Flyover/flyunder tile types get null colliders in `GameServer.expandCollidersForTiles` so ships pass through them.

## Tile ID Categories

Constants live in [`infinity.map.MapTypes`](../../infinity-server/src/main/java/infinity/map/MapTypes.java). Each category is handled in `MapSystem.createBlocksFromLegacyMap`.

| IDs | Category | How it's placed |
|-----|----------|-----------------|
| 0 | empty | skipped |
| 1–161 (except 20) | normal visible tile | cell: `TILE_TYPE_BASE + id - 1`, solid |
| 20 | border | same cell treatment as normal tiles (rarely present in .lvl data) |
| 162–165 | vertical door | entity: `MapFactory.createDoor` |
| 166–169 | horizontal door | entity: `MapFactory.createDoor` |
| 170 | turf flag | entity: `MapFactory.createTurfStationaryFlag` |
| 171 | safe zone | cell, visible, solid |
| 172 | goal area | cell, visible, solid |
| 173–175 | flyover | cell, visible, passthrough (null collider) |
| 176–190 | flyunder | cell, visible, passthrough (null collider) |
| 216 | small asteroid | entity: `MapFactory.createAsteroidSmall` |
| 217 | medium asteroid | entity: `MapFactory.createAsteroidMedium` |
| 218 | asteroid end / wormhole2 | entity: `MapFactory.createWormhole2` |
| 219 | space station | cell, visible, solid |
| 220 | wormhole | entity: `MapFactory.createWormhole` |
| 221–228 | team/enemy bricks, goals, flags, prizes | SSB runtime-only — not in .lvl data |
| 240–255 | invisible / special behavior tiles | cell: `INVISIBLE_BLOCK_TYPE`, collidable but no visuals |

**Entity-backed tiles** don't write a world cell — the entity owns its shape, rendering, and behavior. Their placement `Vec3d` is still tracked in the returned `coordinates` set so `removeBlocksFromLegacyMap` can zero the slot regardless of what happens to be there.

## Maps Location

`infinity/assets/Maps/` — maps are on the JME3 asset path, loadable by both server (`AssetLoaderService`) and client (`app.getAssetManager()`).
