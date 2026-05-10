---
name: moss-world-grid
description: Moss world/cell/leaf/column/tile grid system. Use when placing/loading maps, working with world coordinates, paging voxel data, or bulk-writing cells across map boundaries.
---

# Moss World Grid System

Moss addresses the voxel world through **four nested grids**. Subspace Infinity's 1024×1024 map concept aligns natively with Moss's `TileId` — do not reinvent the math with `* 1024` multiplications.

## The four grids (WorldGrids.java)

Sources: [moss/mworld/src/main/java/com/simsilica/mworld/WorldGrids.java](../../../moss/mworld/src/main/java/com/simsilica/mworld/WorldGrids.java)

| ID type | Grid dims (x, y, z) | Size constant | Purpose |
|---|---|---|---|
| `LeafId` | 32 × 32 × 32 | `LeafInfo.SIZE = 32` | **Paging unit.** One `.col` file on disk; loaded/cached together. |
| `ColumnId` | 32 × 1024 × 32 | — | Vertical stack of 32 leafs at one (x,z); lets an app manage two 1024-tall layers if ever needed. |
| `TileId` | **1024 × 1024 × 1024** | `WorldGrids.TILE_SIZE = 1024` | **One Subspace map.** Natural boundary for .lvl placement. |
| `SedectileId` | 16384 × 1024 × 16384 | `WorldGrids.SEDECTTILE_SIZE` | Coarse region, used for terrain pyramids. Rarely touched directly. |

Every grid ID is a `long` packing `(cellX, cellY, cellZ)` with 21 bits per component → world bounds ±33,554,432 cells.

## Coordinate spaces

Three distinct spaces — do not mix them:

1. **World coords** (`Vec3d world`) — what physics, entities, rendering use. Doubles.
2. **Cell coords** (`int x, y, z`) — `Math.floor(world)`. One integer per world cell. Use `Coordinates.worldToCell(double)`.
3. **Grid coords** (also `int x, y, z`, but relative to a grid's cell size) — e.g. on `TILE_GRID`, cell `(0,0,0)` covers world X∈[0,1023]; cell `(1,0,0)` covers X∈[1024,2047].

Conversion is always via the grid: `grid.worldToId(...)`, `grid.cellToId(...)`, `grid.idToCell(id, store)`, `grid.cellToWorld(cell, store)`.

## `TileId` — the primitive for map placement

Source: [moss/mworld/src/main/java/com/simsilica/mworld/TileId.java](../../../moss/mworld/src/main/java/com/simsilica/mworld/TileId.java)

```java
// Construct from grid coordinates (NO * 1024 needed — this IS the grid)
TileId tile = TileId.fromCell(gx, gy, gz);

// Construct from any world point
TileId tile = TileId.fromWorld(new Vec3d(512, 0, 512));  // → tile (0,0,0)

// Get the min-corner world location of this tile
Vec3i corner = tile.getWorld(null);  // e.g. (1024, 0, 0) for tile (1,0,0)

// Get grid-address of this tile
Vec3i gridAddr = tile.getCell(null);  // e.g. (1, 0, 0)

// Iterate a (2*radius+1)^2 neighborhood in the XZ plane
origin.visitNeighbors(1, t -> { /* 9 tiles for a 3×3 */ });
origin.visitNeighbors(2, t -> { /* 25 tiles for a 5×5 */ });
```

**When loading a multi-map grid, `visitNeighbors` replaces any hand-rolled spiral or nested loop.**

## `LeafId` — paging unit

Source: [moss/mworld/src/main/java/com/simsilica/mworld/LeafId.java](../../../moss/mworld/src/main/java/com/simsilica/mworld/LeafId.java)

```java
LeafId leaf = LeafId.fromWorld(worldVec);     // hash a world point to its leaf
Vec3i corner = leaf.getWorld(null);           // min-corner of the 32³ leaf
leaf.visitNeighbors(xzRadius, yRadius, v -> {});
```

One `TileId` = **32×32 = 1024 leafs** in its XZ footprint (at one Y layer). Each leaf serializes to one `.col` file under `world.db/`. The hundreds of dirty `.col` files you see in `git status` after running the game are exactly these leaf payloads.

## `ColumnId` — rarely needed directly

Bridges leaf ↔ tile. Useful API: `ColumnId.getTileId()`, `LeafId.getColumnId()`. Ignore unless doing vertical-layer work.

## How cells reach disk

`world.setWorldCell(Vec3d, int type)` ([infinity-server/src/main/java/infinity/sim/InfinityDefaultLeafWorld.java:153](../../../infinity-server/src/main/java/infinity/sim/InfinityDefaultLeafWorld.java#L153)):

1. `LeafId.fromWorld(world)` — hash into the owning leaf
2. `getLeaf(id)` — pull from `DefaultColumnDb` cache (backed by `SpoolingObjectDb` async I/O)
3. `Coordinates.worldToCell(world.x/y/z)` — reduce to integer cell coords
4. `data.setCell(x,y,z,type)` — mutate the leaf's cell array
5. `MaskUtils.recalculateSideMasks(...)` — recompute visibility side masks (expensive; TODO in source notes that batch API would help)
6. `leafDb.storeLeaf(mod)` for each modified leaf (neighbors may also change due to mask recalc)
7. Fire `CellChangeEvent` to listeners

**Writing to a world cell automatically creates/loads the owning leaf.** You never manually manage paging.

## Subspace Infinity configuration

- `MapSystem.MAP_SIZE = 1024` matches `WorldGrids.TILE_SIZE = 1024` by design — each `.lvl` is exactly one `TileId`.
- World instance: `InfinityDefaultLeafWorld(leafDb, yMax=10)` created in [GameServer.java:221-228](../../../infinity-server/src/main/java/infinity/server/GameServer.java#L221-L228).
- Persistence: `DefaultColumnDb(new File("world.db"))` → per-leaf `.col` files at `infinity/world.db/0/-1/-1/*.col`.

## Common patterns

### Place a map at an explicit grid slot

```java
TileId tile = TileId.fromCell(gx, 0, gz);
Vec3i corner = tile.getWorld(null);
Vec3d worldOffset = new Vec3d(corner.x, corner.y, corner.z);
// then iterate the .lvl's 1024x1024 tile array and call world.setWorldCell(worldOffset + (x,y,z), tileType)
```

### Load a 3×3 grid around origin

```java
TileId.fromCell(0, 0, 0).visitNeighbors(1, t -> loadMap(mapFor(t), t));
```

### Find which map contains a ship

```java
TileId tile = TileId.fromWorld(ship.getWorldPosition());
String mapName = tileToMapName.get(tile);  // keyed by TileId, not String
```

## Anti-patterns

- **Don't multiply `gridCoord * MAP_SIZE`** — call `TileId.getWorld(null)` instead. Same result, self-documenting, survives any future change to `TILE_SIZE`.
- **Don't key map metadata by `Vec3d`** — use `TileId` (proper equals/hashCode, no floating-point drift).
- **Don't roll your own neighbor spiral** — `visitNeighbors(radius, consumer)` exists on both `TileId` and `LeafId`.
- **Don't pre-create leafs** — `setWorldCell` materializes them lazily. Generating empty leafs ahead of time just bloats `world.db/`.

## When this skill applies

- Placing multiple maps in a grid
- Translating between ship positions and "which map am I in"
- Debugging `.col` file proliferation / paging issues
- Anything with `* 1024` or `* MAP_SIZE` in new code (probably wrong — use `TileId`)
- Bulk cell writes that span leaf or tile boundaries
