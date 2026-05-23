# Real navmesh: clearance-aware A* + Floyd LoS smoothing + async build

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 3 of 9. Implements the full [ADR-0011](../../../docs/adr/0011-bot-navigation-navmesh.md).

## What to build

Replace the Slice #02 stubbed NavMeshService with the real thing — 8-connected
clearance-aware grid A* over the `.lvl` passable tiles, with Floyd-style
line-of-sight chain post-processing, computed asynchronously at arena-load.
Adds a `navigate-to-target` behaviour that paths around walls to the current
threat target — replacing reactive Pursue in corridor-heavy maps.

Demo: in a corridor-style arena, bot navigates around walls to reach the
player instead of pressing against them. Pure-open-arena behaviour
unchanged (path collapses to direct line).

## Acceptance criteria

- [ ] 8-connected grid A* over passable tiles; octile heuristic; diagonal-corner-clip rejected (block diagonals through wall pairs)
- [ ] Brushfire / BFS pre-compute populates per-tile `clearance` value; edge rejected when `min(clearance(a), clearance(b)) < shipRadiusInTiles`
- [ ] Floyd-style line-of-sight chain post-processing yields sparse corner-only waypoints
- [ ] Async build on worker thread at `onArenaLoad`; per-arena state goes `BUILDING` → `READY`; `PathPlanner.findPath` returns `Path.empty()` while `BUILDING`
- [ ] `Path.empty()` callers gracefully fall back (no crash; bot wanders)
- [ ] `FollowPath` BT action handles `Path.empty()` returning FAILURE → planner re-selects
- [ ] New behaviour `navigate-to-target`: enumerates `NavigateToTile(targetTile)` for current threat target; scorer prefers it when AvoidObstacles can't reach via straight line (heuristic: prior tick's straight-line attempt was reactive-blocked)
- [ ] Brawler `ArchetypeConfig` adds `'navigate-to-target'` at low weight; behaviour competes with Pursue
- [ ] Unit tests: A* finds shortest paths; clearance rejection works for narrow corridors; LoS smoothing collapses straight runs
- [ ] Pathfinding benchmark on a real `.lvl` map (microsecond per typical query, sub-second async build for 1024² maps)
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#02 — BotAiArenaContext scaffold + tracer](./02-arena-context-scaffold-tracer.md)

## Comments
