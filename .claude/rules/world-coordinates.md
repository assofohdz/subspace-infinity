---
paths:
  - "infinity-server/src/main/java/**/*.java"
  - "infinity-client/src/main/java/**/*.java"
---
# World Coordinate Rules

- **Use `TileId` APIs for map placement.** Never multiply by `1024` or `MAP_SIZE` — those are implementation details and will silently break if cell size changes.
- **`InfinityConstants.GRID_CELL_SIZE` is the source of truth** for grid-size reads. Do not go through `WorldGrids`.
