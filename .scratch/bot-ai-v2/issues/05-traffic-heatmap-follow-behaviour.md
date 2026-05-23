# `TrafficHeatmap` + `follow-traffic` behaviour

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 5 of 9. Implements [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md) `TrafficHeatmap`.

## What to build

First dynamic spatial service. `TrafficHeatmap` is a per-tile decaying
scalar field; per tick, every live ship's current tile increments its
counter, then all tiles decay by a small factor. New behaviour
`follow-traffic` navigates toward heatmap peaks — a Brawler bot drifts
toward where the action is rather than wandering randomly.

Demo: in an arena with players moving around, bots gradually congregate
toward areas the players visit most. Visible behaviour difference vs
Slice #02's random tile selection.

## Acceptance criteria

- [ ] `api/infinity.ai.spatial.TrafficHeatmap` interface (`heatAt(TileId)`, `hottest(int topN)`)
- [ ] `infinity-server/.../ai/spatial/TrafficHeatmapImpl` — per-tick increment from live ships' tiles + per-tick decay (factor `~0.99`); decay-only update can throttle (every Nth tick)
- [ ] Bundled into `BotAiArenaContext.traffic()`; immediate readiness (no async build needed; starts from zero)
- [ ] New behaviour `follow-traffic`: enumerates top-N heatmap tiles; intrinsic score weighted by heat + distance-decay; emits `NavigateToTile(tile)` goal (reuses Slice #02's goal type)
- [ ] Brawler `ArchetypeConfig` adds `'follow-traffic'` at low weight (e.g. 0.2) alongside existing behaviours
- [ ] Unit tests: heatmap increment + decay; top-N ordering; behaviour enumeration scores correctly
- [ ] Performance: per-tick update cost bounded by `O(liveShips + populatedTiles)`; benchmark with 32 ships in a 1024² arena stays within a single-digit-percent of one tick budget
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#03 — Real navmesh](./03-real-navmesh-clearance-smoothing.md)

## Comments
