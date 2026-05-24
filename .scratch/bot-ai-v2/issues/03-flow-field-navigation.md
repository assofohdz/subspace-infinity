# Production flow-field navigation: async build, caching, door invalidation

Status: needs-triage
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

- [ ] `NavigationFields.fieldFor(goal)` returns a `CompletableFuture<DistanceField>`-backed field; Dijkstra runs on a worker thread; readers use `getNow(EMPTY_FIELD)` for non-blocking access
- [ ] Pre-completion reads return the zero-gradient default → bot falls through to `AvoidObstacles` and wanders briefly; field ready next read. **No crash.**
- [ ] Failed builds leave the future failed; cache evicts + retries on next request
- [ ] Static fields (fixed map tiles — flag/KOTH-center/chokepoint) built at arena load; persist for arena lifetime
- [ ] Transient fields (predicted-target tile, hottest-traffic tile) built lazily; LRU + TTL eviction (default ~5s); `evict(goal)` honoured
- [ ] `BotAiHostService.onArenaLoad` registers static goals (union of chokepoint top-N from #07 + `ArenaObjective.staticGoalTiles()` from #06; both empty-tolerant until those slices land)
- [ ] Door-state invalidation: each field tracks the door tiles its Dijkstra pass crossed; a `DoorStateChanged` event invalidates fields whose tracked door set contains the changed door; rebuild on next request
- [ ] `.lvl` reload evicts all fields for that arena
- [ ] Tile-supersampling toggle read from `zone-bot-ai.groovy` (default off); changing it evicts + rebuilds
- [ ] Memory bound documented; supersampling path available if measurement demands
- [ ] Unit tests: async build completes; getNow default before completion; door-tracked invalidation; TTL eviction; diagonal corner-clip still rejected
- [ ] Benchmark: single-source Dijkstra on a real 1024² `.lvl` map completes sub-second off-thread; gradient sample is microsecond-scale
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#02 — `BotAiArenaContext` + flow-field tracer](./02-arena-context-flowfield-tracer.md)

## Comments
