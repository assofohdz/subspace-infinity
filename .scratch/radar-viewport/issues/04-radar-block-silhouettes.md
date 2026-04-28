# Radar block silhouettes via RadarLeafSilhouetteIndex

Status: needs-triage
Parent: [../PRD.md](../PRD.md)
Labels: area:client

## What to build

Show the world's solid blocks on the radar as flat white silhouettes inside the radar's circle. Blocks are not entities, so this slice mirrors `LocalViewState`'s leaf paging but produces a stripped-down 2D mesh.

New class `infinity.client.view.RadarLeafSilhouetteIndex`, sibling to `BlockGeometryIndex`. For each leaf's cell data, generates a single flat `Mesh` of solid cells using `Unshaded.j3md` with `Color = ColorRGBA.White` — no neighbor lighting, no per-face mesh, no transparency.

A new `RadarLeafView` paging job inside `RadarState` (mirroring `LocalViewState.LeafView`) loads / unloads silhouette nodes under `radarBlockRoot` as the player crosses leaves. The radar paging radius is **independent** of `LocalViewState.viewRadius` and is derived from `RadarRange` (converted via `WorldGrids.LEAF_GRID` and `TileId` APIs — no hand-rolled `* 1024` arithmetic, per `world-coordinates.md`).

When `RadarRange` changes (ship swap), the radar paging radius adjusts and silhouette nodes outside the new radius unload.

## Acceptance criteria

- [ ] `infinity.client.view.RadarLeafSilhouetteIndex` exists, sibling of `BlockGeometryIndex`
- [ ] Silhouette mesh per leaf uses `Unshaded.j3md` with white color, no lighting
- [ ] `RadarLeafView` pages silhouette nodes in / out under `radarBlockRoot` driven by player position
- [ ] Paging radius derived from local avatar's `RadarRange` via `TileId` / `WorldGrids` (no `* 1024` literals)
- [ ] Paging radius updates when `RadarRange` changes
- [ ] Manual verification: walls / solid tiles visible as white silhouette inside the radar circle; extent grows / shrinks correctly on ship swap

## Blocked by

- [02-radarstate-scaffold-circle-gui](02-radarstate-scaffold-circle-gui.md)

## Comments
