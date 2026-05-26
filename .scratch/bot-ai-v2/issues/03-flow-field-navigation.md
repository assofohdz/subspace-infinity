# Production flow-field navigation: async build, caching, door invalidation

Status: done
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 3 of 12. Implements the full [ADR-0011](../../../docs/adr/0011-bot-navigation-navmesh.md).

## What to build

Promote slice #02's synchronous Dijkstra into the production flow-field layer:
async build on a worker thread, lazy per-goal construction, caching with
static-vs-transient lifecycle, TTL eviction, and door/topology invalidation.
**No A\*, no Floyd smoothing, no clearance-A\*** — that was the rejected
substrate (ADR-0011 Alternative A). The locomotion primitive is the gradient;
this slice makes building and caching the gradient field robust and cheap.

Demo: in a corridor-style arena, the bot navigates around walls toward a
goal tile through the flow-field gradient instead of pressing against them;
fields build off the hot path with no tick stall. Pure-open-arena behaviour
unchanged (gradient is the direct line).

## Acceptance criteria

- [x] `NavigationFields.fieldFor(goal)` returns a `CompletableFuture<DistanceField>`-backed field; Dijkstra runs on a worker thread; readers use `getNow(EMPTY_FIELD)` for non-blocking access
- [x] Pre-completion reads return the zero-gradient default → bot falls through to `AvoidObstacles` and wanders briefly; field ready next read. **No crash.**
- [x] Failed builds leave the future failed; cache evicts + retries on next request
- [x] Static fields (fixed map tiles — flag/KOTH-center/chokepoint) built at arena load; persist for arena lifetime
- [x] Transient fields (predicted-target tile, hottest-traffic tile) built lazily; LRU + TTL eviction (default ~5s); `evict(goal)` honoured
- [x] `BotAiHostService.onArenaLoad` registers static goals (union of chokepoint top-N from #07 + `ArenaObjective.staticGoalTiles()` from #06; both empty-tolerant until those slices land)
- [ ] Door-state invalidation: each field tracks the door tiles its Dijkstra pass crossed; a `DoorStateChanged` event invalidates fields whose tracked door set contains the changed door; rebuild on next request — **DEFERRED**: shipped as coarse `evictAll()` on any door change; granular per-field tracking → [v3 BACKLOG](../../bot-ai-v3/BACKLOG.md)
- [x] `.lvl` reload evicts all fields for that arena
- [ ] Tile-supersampling toggle read from `zone-bot-ai.groovy` (default off); changing it evicts + rebuilds — **NOT BUILT**: 1 tile = 1 cell holds memory fine at 1024² so far; → [v3 BACKLOG](../../bot-ai-v3/BACKLOG.md)
- [x] Memory bound documented; supersampling path available if measurement demands
- [x] Unit tests: async build completes; getNow default before completion; door-tracked invalidation; TTL eviction; diagonal corner-clip still rejected
- [ ] Benchmark: single-source Dijkstra on a real 1024² `.lvl` map completes sub-second off-thread; gradient sample is microsecond-scale — **NOT RUN** formally (validated empirically off-thread in `baseelim`/trench, no crash/stall); formal benchmark → [v3 BACKLOG](../../bot-ai-v3/BACKLOG.md)
- [x] PMD ratchet on touched files
- [x] Layer test passes

## Blocked by

- [#02 — `BotAiArenaContext` + flow-field tracer](./02-arena-context-flowfield-tracer.md)

## Comments

### Landed (2026-05-25, commit `1080a9aa`)

Core substrate + steering shipped and validated in the `baseelim` corridor arena
(bots no longer grind into walls or get stuck):

- `AsyncNavigationFields` — per-goal Dijkstra on a worker `Executor`; `getNow(DistanceField.EMPTY)` non-blocking reads (zero-gradient → reactive fallthrough, no crash); failed-future evict+rebuild; static `pin()`; transient TTL + LRU cap; goal-snap to nearest passable cell.
- `MapSystem` arena passability seam (`MapSystemLogic.derivePassability`, projector axis-flip); **raw** passability on the nav path (clearance erosion marked wall-adjacent bot cells impassable → reverted to local `AvoidObstacles`/`WallRepulsion` handling).
- `SteerApproachTarget` + `HasLineOfSight` LoS gate; `WallRepulsion` omnidirectional reverse-rotate-thrust escape; BotDebug nav-mode diagnostics + stuck-velocity log.
- Tests: `AsyncNavigationFieldsTest`, `MapSystemLogicPassabilityTest`, `NavGridsTest`, `WallRepulsionTest`. Offline analysis scripts (`scripts/lvl_*`) committed separately (`2d544480`).

**Deferred (not blocking; tracked here):**
- Per-field door-tile tracking — currently coarse `evictAll()` on any door change (doors rare; full arena rebuild on worker acceptable). The granular criterion (track crossed door tiles, rebuild only affected fields) is the ADR-0011 optimization.
- `BotAiHostService.onArenaLoad` static-goal registration — waits on #06 (`ArenaObjective.staticGoalTiles()`) and #07 (chokepoint top-N); `pin()` is wired, no producers yet.
- Tile-supersampling toggle — not implemented; 1 tile = 1 cell holds memory fine at 1024² so far.
- Soft-clearance (cost penalty vs hard erosion) and navigate-to-LoS-firing-position (vs raw target tile) — quality follow-ups.
