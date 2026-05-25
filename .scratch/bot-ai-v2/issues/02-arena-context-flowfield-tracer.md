# `BotAiArenaContext` + `BotAiHostService` scaffold + flow-field tracer

Status: done
Landed: db90765e (2026-05-23) — tracer substrate; production nav is #03 (deferred)
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 2 of 12. Establishes the nav substrate for [ADR-0011](../../../docs/adr/0011-bot-navigation-navmesh.md) + the field primitive shared with [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md).

## What to build

Thin vertical tracer through the navigation substrate. **Flow fields, not
A\*** — per the revised ADR-0011, navigation is a Dijkstra distance field whose
gradient points toward the goal; the steering layer samples the gradient
rather than chasing waypoints. This slice lands the api-tier field interfaces,
the `BotAiArenaContext` / `BotAiHostService` scaffold, a *synchronous*
single-source Dijkstra `DistanceField`, and the `SeekDirection` steering
primitive. Production async build + caching + door invalidation is slice #03.
The tactical planner is slice #05 — this slice drives the tracer with a
hardwired "pick a random reachable tile" goal, no planner needed.

Demo: bot's HUD shows `Nav: tile(50,80) via flow-field`; the bot thrusts
toward a specific reachable tile through the gradient (handling its own
momentum) rather than Reynolds-wandering in place.

## Acceptance criteria

- [ ] `api/infinity.math.Vec2d` — 2D double-precision vector; `fromXz(Vec3d)` / `toXz()` helpers
- [ ] `api/infinity.ai.field.ScalarField` (`width`, `height`, `valueAt(x,y)`, `valueAt(Vec3d)`)
- [ ] `api/infinity.ai.field.GradientField` (`directionAt(x,y)` → unit `Vec2d` toward lower value; `directionAt(Vec3d)`)
- [ ] `api/infinity.ai.field.FieldGradient` — derives a `GradientField` from any `ScalarField`
- [ ] `api/infinity.ai.field.DistanceField extends ScalarField` (`goal()`; `POSITIVE_INFINITY` for unreachable)
- [ ] `api/infinity.ai.field.NavigationFields` accessor interface (`fieldFor(TileId)`, `gradientFor(TileId)`, `evict(TileId)`) — synchronous impl this slice
- [ ] `infinity-server/.../ai/field/nav/DijkstraDistanceField` — single-source Dijkstra over `.lvl` passable tiles; diagonal-corner-clip rejected
- [ ] `api/infinity.ai.steer.SeekDirection` — Reynolds-style primitive taking a desired heading (not a target position); emits thrust+rotation `MovementInput` honouring momentum
- [ ] `infinity-server/.../ai/host/BotAiHostService` (`BaseInfinitySystem`) — owns a per-arena `BotAiArenaContext` map; lifecycle on `onArenaLoad`/`onArenaUnload`
- [ ] `api/infinity.ai.tactical.BotAiArenaContext` — `navigation()` accessor live; slots reserved (`threat()`, `opportunity()`, `chokepoints()`, capability norms) returning empty defaults until later slices
- [ ] `BotBrainSystem.BrainContainer.addObject` injects `BotAiArenaContext` into `Blackboard`
- [ ] Tracer wiring: a hardwired "navigate to random reachable tile" drives `SeekDirection(gradientFor(tile))` through the existing steering composition (`PrioritySteering(AvoidObstacles, SeekDirection)`)
- [ ] Unit tests: Dijkstra distances correct on a hand-built grid; gradient points downhill; `SeekDirection` heading math; unreachable → `POSITIVE_INFINITY`/zero gradient
- [ ] `BotInputCanonicalityTest` extended: `BotBrainSystem` is still the sole `MovementInput` writer despite the new nav layer
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

None — can start immediately. Slice #01 is a parallel independent track.

## Comments
