# `BotAiArenaContext` scaffold + "navigate-to-random-tile" tracer

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 2 of 9. Establishes the substrate for [ADR-0011](../../../docs/adr/0011-bot-navigation-navmesh.md), [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md), [ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md).

## What to build

Thin vertical tracer through the full v2 stack — nav + tactical planner +
behaviour + BT + Groovy archetype DSL. NavMeshService returns a **stubbed**
graph (straight line A→B as a single waypoint), real A* lands in Slice #03.
Every other piece (planner, behaviour registry, archetype config DSL, IsGoal,
FollowPath, Arrive steering) is real.

Demo: bot's HUD shows `Goal: NavigateToTile(50,80) Behaviour: wander-with-purpose`,
the bot navigates to specific reachable tiles in the arena rather than
Reynolds-wandering in place.

## Acceptance criteria

- [ ] `infinity-server/.../ai/host/BotAiHostService` exists; owns per-arena `BotAiArenaContext` map; lifecycle on `onArenaLoad`/`onArenaUnload`
- [ ] `api/infinity.ai.tactical.BotAiArenaContext` accessor interface (`nav()`, slots reserved for spatial services + planner)
- [ ] `api/infinity.ai.nav.NavMeshService` interface + stubbed impl returning a single-waypoint Path
- [ ] `api/infinity.ai.nav.Path`, `TileId` (or re-use existing), `PathPlanner` interface
- [ ] `api/infinity.ai.steer.Arrive` Reynolds steering primitive (target with deceleration radius); long overdue from slice #03
- [ ] `api/infinity.ai.brain.FollowPath` BT Action — drives `Arrive(nextWaypoint)`; advances on arrival; SUCCESS at path end, RUNNING in-progress, FAILURE on null path
- [ ] `api/infinity.ai.tactical.TacticalGoal` sealed interface + `NavigateToTile(TileId)` first record
- [ ] `api/infinity.ai.tactical.Behaviour` interface (`name()`, `enumerate()`, `intrinsicScore()`)
- [ ] `api/infinity.ai.tactical.TacticalPlanner` interface + impl with cadence throttle (default 500ms) + additive stickiness margin (default 0.10)
- [ ] `api/infinity.ai.tactical.ArchetypeConfig` record (`name`, `Map<String, Double> behaviourWeights`)
- [ ] `api/infinity.ai.brain.IsGoal(GoalClass)` Condition leaf — exact-class dispatch
- [ ] One trivial behaviour: `WanderWithPurpose` (picks random reachable tile, emits `NavigateToTile(tile)` goal)
- [ ] Brawler `ArchetypeConfig` includes `'wander-with-purpose'` at low weight; existing Engage / Wander stay
- [ ] Groovy DSL: `archetype 'Brawler' { behaviour 'wander-with-purpose', weight: 0.3; ... }` parses + loads via existing fragment pipeline; `BotsAdapter` from Slice #01 picks it up
- [ ] `BotBrainSystem.BrainContainer.addObject` injects `BotAiArenaContext` reference into Blackboard
- [ ] `BotBrainSystem` runs planner on 500ms cadence; per-tick BT ticks normally
- [ ] Unit tests: TacticalPlanner picks max-weighted-score; stickiness prevents oscillation; IsGoal exact-class match
- [ ] `BotInputCanonicalityTest` extended: `BotBrainSystem` is still sole `MovementInput` writer despite new TacticalPlanner layer
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

None — can start immediately. Slice #01 is parallel (independent track).

## Comments
