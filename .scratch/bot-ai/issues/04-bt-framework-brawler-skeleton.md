# BT framework + `Brawler` skeleton with `Wander` fallback

Status: done
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 4 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Introduce the Behaviour Tree framework + brain composition layer + the v1 `Brawler` archetype with a Selector picking between Pursue (when target visible) and Wander (fallback).

- **api/** — `infinity.ai.bt.Behavior` base class with `tick(Blackboard) → Status`; `Status` enum (`Success`, `Failure`, `Running`); composites `Selector` + `Sequence`; leaf interfaces `Action` + `Condition`. Defer `Parallel` and decorators (`Inverter`, `Cooldown`, `Timeout`, `Repeater`, `UntilFailure`) — they're not needed until later slices.
- **api/** — `infinity.ai.steer.Wander` primitive (Reynolds wander — random target ahead with momentum, varying every N seconds).
- **api/** — `infinity.ai.brain.BrainArchetype` factory interface (named-registry: name → `Supplier<Behavior>`); `Blackboard` per-bot scratchpad (last seen target, current `PerceptionSnapshot`, current `MovementInput` accumulator, last-fire timestamps).
- **api/** — `infinity.ai.brain.CombatantBrain` ships the v1 `Brawler` BT shape: `Selector(PursueBranch, WanderFallback)` where `PursueBranch = Sequence(HasTarget, SteerPursue)`.
- **BotBrainSystem** — replace inlined steering composition with BT tick: per bot, build perception → write to blackboard → tick the brain BT → read accumulated `MovementInput` from blackboard → write component.
- Unit tests for BT composites (Selector picks first non-Failure; Sequence is fail-fast on first Failure) and Wander (target drift respects momentum, varies on cadence).

## Acceptance criteria

- [x] `infinity.ai.bt.Behavior`, `Status`, `Selector`, `Sequence`, `Action`, `Condition` exist in api/
- [x] `infinity.ai.brain.BrainArchetype`, `Blackboard`, `CombatantBrain` exist in api/
- [x] `infinity.ai.steer.Wander` exists in api/
- [x] `BotBrainSystem` ticks the brain BT per bot per tick (no inlined logic)
- [x] Brawler archetype registered (named "Brawler") in a `BrainArchetype` registry consumed by `BotBrainSystem`
- [x] Unit tests for BT composites + Wander
- [x] **Demo:** launch arena with one player → bot wanders when player is out of perception; pursues when player enters perception; re-wanders if player leaves
- [x] License headers + SPDX on every new file
- [x] PMD ratchet
- [x] Layer test passes

## Blocked by

- [#03 — Pursue + AvoidObstacles + Perception](./03-pursue-avoidobstacles-perception.md)

## Comments
