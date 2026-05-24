# `TacticalPlanner` + `Behaviour` contract + baseline behaviours

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 5 of 12. Implements [ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md). Consumes the weight vector from #04.

## What to build

The tactical-goal layer that sits above the BT. The `TacticalPlanner` picks
one `TacticalGoal` per slow cadence; named `Behaviour`s enumerate + intrinsically
score candidate goals; the planner multiplies intrinsic score × derived weight
(#04) + an additive stickiness margin against the current goal, writes the
winner to the `Blackboard`, and the BT dispatches to a per-goal Execute
Sequence. This slice ships the **baseline behaviours that map onto v1
steering** (`engage`, `strafe`, `evade`, `wander`, `navigate-to-target`) so the
end-to-end pipeline is observable without any weapon-specific work.

Demo (the substrate tracer): with no per-arena authoring, capability-derived
weights make a Warbird engage aggressively while a glassy / low-mobility hull
evades and repositions more — hull-driven behaviour divergence falls out of
#04 automatically.

## Acceptance criteria

- [ ] `api/infinity.ai.tactical.TacticalGoal` sealed interface + baseline records (`Engage(EntityId)`, `NavigateToTile(TileId)`, `Evade`, `Wander`)
- [ ] `api/infinity.ai.tactical.Behaviour` interface (`name()`, `enumerate(ctx)` → candidate goals, `intrinsicScore(goal, ctx)`)
- [ ] `api/infinity.ai.tactical.ArchetypeConfig` record (`Map<String, Double> behaviourWeights`) fed by #04's merged vector
- [ ] `api/infinity.ai.tactical.TacticalPlanner` interface + impl — cadence throttle (default ~150ms per amended ADR-0013; zone-overridable), additive stickiness margin (zone knob), `MIN_BEHAVIOUR_WEIGHT` skips enumeration for sub-threshold behaviours
- [ ] `api/infinity.ai.brain.IsGoal(GoalClass)` BT Condition — exact-class dispatch to per-goal branch
- [ ] Baseline behaviours: `engage`, `strafe`, `evade`, `wander`, `navigate-to-target` (the last uses #03 flow-field gradient toward the threat's tile, replacing reactive Pursue in corridors)
- [ ] Each baseline behaviour ships its `synergy { }` entry in `engine-bot-ai.groovy` (#04) in the same PR — a behaviour without a synergy line is incomplete
- [ ] `Blackboard.currentGoal` written by planner; BT reads + dispatches; per-goal Execute Sequences for the baseline goals
- [ ] `BotBrainSystem` runs the planner on its cadence; BT ticks per-frame as before
- [ ] Unit tests: planner picks max `intrinsicScore × weight`; stickiness prevents oscillation; sub-`MIN_BEHAVIOUR_WEIGHT` behaviours skipped; `IsGoal` exact-class match
- [ ] `BotInputCanonicalityTest`: `BotBrainSystem` still the sole `MovementInput` writer despite the planner layer
- [ ] Manual smoke: a Warbird vs a low-mobility hull behave visibly differently with no arena authoring
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#04 — Capability-derivation pipeline](./04-capability-derivation.md) (weight vector source)
- [#02 — `BotAiArenaContext` + flow-field tracer](./02-arena-context-flowfield-tracer.md) (`navigate-to-target` needs gradients; #03 for production build)

## Comments
