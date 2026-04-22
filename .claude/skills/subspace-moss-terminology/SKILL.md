---
name: subspace-moss-terminology
description: Disambiguate overloaded terms between Subspace/Continuum, MOSS, and Subspace Infinity. Use when code or discussion mentions cell, tile, region, arena, leaf, coordinate, grid, or other terms that mean different things in each system.
---

# Subspace ↔ MOSS ↔ Infinity Terminology

Several nouns collide across these three systems and mean very different things. Always ask "whose vocabulary?" before assuming what a word refers to.

## Quick cheat sheet

| Term | Subspace / Continuum | MOSS | Subspace Infinity (this project) |
|---|---|---|---|
| **Tile** | One of 1024×1024 cells in a `.lvl` map (empty, wall, door, flag, safe, …) | `TileId` — a **1024×1024×1024** volume (one whole Subspace map) | A Subspace tile. MOSS `TileId` appears only in grid/placement code. |
| **Cell** | Informal: a minimap square (A1, B5, …) | The smallest world unit. `Coordinates.worldToCell(double)` = `floor(world)` | Usually MOSS sense. `GridCell` is a typed MOSS concept. |
| **Map** | A `.lvl` file: 1024×1024 tiles + optional BMP tileset + eLVL metadata | — | Same as Subspace. One map fills one MOSS `TileId`. |
| **Arena** | A named game space: one map + one settings bundle + its players | — | An Arena **entity** with components `ArenaId`, `ArenaMap`, `ArenaSettings`. Occupies one `TileId`. |
| **Zone** | The whole server — a collection of arenas + zone-wide config | — | Same concept. `infinity/zone/` is the on-disk root. |
| **Settings** | INI config: ship stats, weapon rules, prize tables, etc. | — | `arena.conf` loaded by `SettingsSystem`, with `(default)` fallback. |
| **Region** | eLVL **REGN chunk**: named polygon inside a map (bases, no-weapon zones, autowarps). RLE-encoded, irregular shape. | — | **Overloaded:** `RegionSystem` uses "region" to mean a 32×32 leaf-aligned grid cell (A1–AF32), not an eLVL polygon. |
| **Leaf** | — | `LeafId` — 32×32×32 paging unit; one `.col` file on disk. | Same as MOSS. |
| **Column** | — | `ColumnId` — 32×1024×32, vertical stack of 32 leafs. | Same as MOSS, rarely referenced. |
| **Grid** | Informal: the minimap overlay | `Grid` object: `TILE_GRID`, `LEAF_GRID`, `COLUMN_GRID`, `SEDECTILE_GRID`. | MOSS grids. The classic Subspace minimap is our new `RegionSystem` labelling. |
| **Ship** | One of 8 classes (Warbird, Javelin, Spider, Leviathan, Terrier, Weasel, Lancaster, Shark) — also INI section names | — | Mapped via `ShapeNames.SHIP_*` in `SettingsSystem.updateShipSettings`. |
| **Player / Avatar** | "Player" = the human | — | Two entities: the **player** entity (identity, login) and the **avatar** entity (the ship in the world). Commands get both IDs. |

## The two "Tile" and "Cell" traps

**`TileId` is not a Subspace tile.** A Subspace tile is a 1×1 square inside a `.lvl`. A MOSS `TileId` is the 1024×1024×1024 volume that *contains* that entire map. Off by six orders of magnitude — easy to confuse in discussion, impossible to confuse once you remember:

> One Subspace map = one MOSS `TileId`. A Subspace tile is one cell inside it.

**"Cell" has three meanings in play:**

1. MOSS world cell — one integer unit in world space (`Coordinates.worldToCell`).
2. Leaf-internal cell — index into a 32×32×32 leaf array.
3. Subspace minimap cell — informal name for an A5-style grid square (now handled by `RegionSystem`).

Default reading in code: **MOSS world cell**. Anything else is spelled out.

## The "Region" name collision

Two unrelated things use the word:

- **eLVL REGN** — polygonal named zones defined *inside a `.lvl`* (bases, safe areas, autowarp sources/destinations). Irregular, arbitrary shapes, loaded from the map file.
- **`RegionSystem`** — our runtime 32×32 leaf-aligned grid across each arena; produces labels like `A5`, `AF12`. Purely a coordinate lookup; has nothing to do with map data.

If a map file talks about regions (REGN/rNAM/rAWP chunks), it is the polygon kind. If Java code references `RegionSystem` or region *labels*, it is the grid kind. Do not interchange them.

## Coordinate spaces

Three distinct integer/float spaces in MOSS; the Subspace .lvl format adds a fourth:

| Space | Type | Example | Produced by |
|---|---|---|---|
| **World** | `Vec3d` (doubles) | `(1536.4, 0.0, 1536.4)` | Physics, entities, rendering |
| **World cell** | `int (x,y,z)` | `(1536, 0, 1536)` | `Coordinates.worldToCell(double)` |
| **Grid cell** | `int (x,y,z)` relative to a grid's cell size | `(1, 0, 1)` on `TILE_GRID` | `grid.worldToId(...)`, `tileId.getCell(null)` |
| **Map tile** | `int (x, y)` in `[0, 1023]²` | `(512, 512)` | Reading a `.lvl` |

Never multiply by `1024` or `MAP_SIZE` to convert — always round-trip through `TileId` or `LeafId`. See [moss-world-grid](../moss-world-grid/SKILL.md) for the full grid math.

## Axis conventions

Subspace is 2D (X, Y). Infinity is 3D (X, Y, Z) with **Y = up**, so:

| Subspace axis | Infinity axis | What it means |
|---|---|---|
| X | X | East / west (column on minimap) |
| Y | Z | North / south (row on minimap) |
| — | Y | Vertical (up/down), ~0 for gameplay |

When porting Subspace math, **swap Y↔Z**. `RegionSystem.getRegionLabel` reads `pos.x` and `pos.z` for exactly this reason — `pos.y` is height, not a Subspace axis.

## Grid / cell sizes — who decides

**`InfinityConstants` is the project's source of truth.** MOSS provides defaults; the project is free to override them.

| Constant | Where | Meaning |
|---|---|---|
| `InfinityConstants.GRID_CELL_SIZE` | [InfinityConstants.java](../../../infinity/src/main/java/infinity/InfinityConstants.java) | Leaf cell size for this project (default: `WorldGrids.LEAF_SIZE` = 32) |
| `InfinityConstants.TILE_SIZE` | [InfinityConstants.java](../../../infinity/src/main/java/infinity/InfinityConstants.java) | Tile / map footprint for this project (default: `WorldGrids.TILE_SIZE` = 1024) |
| `MapSystem.MAP_SIZE` | [MapSystem.java](../../../infinity/src/main/java/infinity/systems/MapSystem.java) | Alias of `InfinityConstants.TILE_SIZE` kept for existing call sites |
| `WorldGrids.LEAF_SIZE` / `TILE_SIZE` | MOSS | **Defaults only.** Do not read from in-project code — go through `InfinityConstants`. |
| `LeafInfo.SIZE`, `Coordinates.LEAF_SIZE`/`NODE_SIZE` | MOSS | Deep-MOSS constants. Not configurable from Infinity. |

Rule of thumb: **in-project code reads `InfinityConstants.*`**. Referencing `WorldGrids.*` sizes from `infinity/` is a bypass of the override point. (Referencing `WorldGrids.TILE_GRID` / `LEAF_GRID` as `Grid` objects is fine — those are structural, not sizing.)

## File extensions

| Extension | Meaning |
|---|---|
| `.lvl` | Subspace map — binary tile data (+ optional BMP tileset + eLVL chunks) |
| `.lvz` | Zone graphics/overlays bundle (LVZ format; objects, images). Not gameplay tiles. |
| `.conf` / `.cfg` / `.ini` | Settings. Loaded via `IniLoader`, supports `#include`. |
| `.sss` / `.set` | Setting-metadata sidecar files; loaded by `SSSLoader`. |
| `.col` | One MOSS leaf serialized to disk under `world.db/`. |

## When this skill applies

- Reading code that mentions cells or tiles without qualifying which system.
- Writing new code that crosses the Subspace-map / MOSS-world boundary (tile types, region lookups, cell coordinates).
- Reviewing or writing docs — pick the precise term and qualify if needed.
- Debugging position/coord bugs — check whether the confusion is "which space am I in."
