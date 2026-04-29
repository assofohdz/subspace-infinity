# Radar block silhouettes via RadarLeafSilhouetteIndex

Status: done
Parent: [../PRD.md](../PRD.md)
Labels: area:client

## What to build

Show the world's solid blocks on the radar as flat white silhouettes inside the radar's circle. Blocks are not entities, so this slice mirrors `LocalViewState`'s leaf paging but produces a stripped-down 2D mesh.

New class `infinity.client.view.RadarLeafSilhouetteIndex`, sibling to `BlockGeometryIndex`. For each leaf's cell data, generates a single flat `Mesh` of solid cells using `Unshaded.j3md` with `Color = ColorRGBA.White` — no neighbor lighting, no per-face mesh, no transparency.

A new `RadarLeafView` paging job inside `RadarState` (mirroring `LocalViewState.LeafView`) loads / unloads silhouette nodes under `radarBlockRoot` as the player crosses leaves. The radar paging radius is **independent** of `LocalViewState.viewRadius` and is derived from `RadarRange` (converted via `WorldGrids.LEAF_GRID` and `TileId` APIs — no hand-rolled `* 1024` arithmetic, per `world-coordinates.md`).

When `RadarRange` changes (ship swap), the radar paging radius adjusts and silhouette nodes outside the new radius unload.

## Acceptance criteria

- [x] `infinity.client.view.RadarLeafSilhouetteIndex` exists, sibling of `BlockGeometryIndex`
- [x] Silhouette mesh per leaf uses `Unshaded.j3md` (later tuned to grey, not white, on top of a muddy-green radar background)
- [x] `RadarLeafView` pages silhouette nodes in / out under `radarBlockRoot` driven by player position
- [x] Paging radius derived from local avatar's `RadarRange` via `WorldGrids.LEAF_GRID` spacing (no `* 1024` literals)
- [x] Paging radius updates when `RadarRange` changes
- [x] Manual verification: walls / solid tiles visible as a grey silhouette inside the radar circle; extent grows / shrinks correctly on ship swap (confirmed in-game)

## Blocked by

- [02-radarstate-scaffold-circle-gui](02-radarstate-scaffold-circle-gui.md)

## Comments
