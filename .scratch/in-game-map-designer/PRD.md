# Client/map: Implement a map designer that can work while you are in game

Status: ready-for-human
Cross-ref: [GH #22](https://github.com/assofohdz/subspace-infinity/issues/22)
Labels: area:client, backlog

**Situation:** Currently the map cannot be edited from within the game. The LVL/LVZ editors in the current Subspace sphere is needed.

**Ask:** Implement functions on the client side that interacts with the MapSystem on the server to create/update/delete tiles in the game. A Lemur panel can be used to create functions to let the player choose which tile to create etc.

## Code-extracted TODOs

- [ ] `MapState.forceLoadImage` and the `LegacyMapImageContainer.addObject` path both lazy-load a fresh `LevelFile` on the GL thread on first reference of a previously-unseen tileset — should be moved off the render thread (background loader / preload pass) so first tile of a new tileset doesn't stutter. Source: `infinity-client/src/main/java/infinity/client/states/MapState.java:260` and `:297`.
- [ ] `LegacyMapImageContainer.updateObject` is a no-op — a `TileInfo` component whose `tileIndex` changes at runtime will not re-render with the new tile texture. Either rebuild the image entry on update, or document that `TileInfo.tileIndex` is spawn-time-only. Source: `infinity-client/src/main/java/infinity/client/states/MapState.java:315`.

## Comments
