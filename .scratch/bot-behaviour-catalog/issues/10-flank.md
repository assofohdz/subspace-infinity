# Flank

Status: ready-for-agent
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 10 of 30. Catalogued in [ADR-0016 §Spatial / map control](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 1.

## What to build

Register the `flank` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Indirect route to attack from an unexpected angle.

## Utility spec (from ADR-0016)

- **Hard gate:** none
- **Situational fit:** `0.35 mobility-fit + 0.25 (alt-route availability) + 0.20 (enemy attention elsewhere) + 0.20 approach_safety`
- **Capability affinity (synergy `bonus`):** mobility
- **Substrate read:** flow-field alt routes + EnemyDensityField

## Acceptance criteria

- [ ] `flank` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
- [ ] `synergy { }` entry in `engine-bot-ai.groovy`: `requires` + `bonus` matching the gate + affinity above; fit coefficients live in `engine-bot-ai.groovy`
- [ ] Goal record + `Execute<...>` BT sequence for carrying out the chosen goal
- [ ] Any input-vocabulary term this behaviour needs that is not already in the ADR-0016 table is added to the shared library + that table in the same change (never redefined inline)
- [ ] Weapon/item actions go through the canonical weapons/intent path (no AI bypass per ADR-0009 §3)
- [ ] Unit tests: `enumerate()` + `intrinsicScore()`; hard gate respected (ineligible ship never enumerates); Execute Sequence completes; goal expiry re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#00 — Situational-input vocabulary (foundation)](00-situational-input-vocabulary.md)
- [#05 — TacticalPlanner](../../bot-ai-v2/issues/05-tactical-planner-baseline-behaviours.md)
- [#07 — Spatial fields](../../bot-ai-v2/issues/07-spatial-fields-chokepoints-follow-traffic.md)
- [#03 — Flow-field navigation](../../bot-ai-v2/issues/03-flow-field-navigation.md)

## Triage (2026-05-27)

ready-for-agent, gated on [#00](00-situational-input-vocabulary.md). Goal: reuse `NavigateToTile` (indirect / alt route to the target). New inputs: `mobility-fit`, `alt-route availability` (flow-field alternate routes from nav), `enemy attention elsewhere` (EnemyDensityField), `approach_safety` (shared). Gate: none. Effort: medium — depends on the flow field exposing an alt-route query.

## Comments
