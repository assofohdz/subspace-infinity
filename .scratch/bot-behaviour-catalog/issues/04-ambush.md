# Ambush

Status: ready-for-agent
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 04 of 30. Catalogued in [ADR-0016 §Combat](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 1.

## What to build

Register the `ambush` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Wait concealed along an expected enemy path.

## Utility spec (from ADR-0016)

- **Hard gate:** `requires cloak∨stealth`
- **Situational fit:** `0.35 traversal_rate + 0.25 concealment + 0.20 low_engagement + 0.20 predictability`
- **Capability affinity (synergy `bonus`):** cloak, stealth
- **Substrate read:** EnemyDensityField prediction + nav

## Acceptance criteria

- [ ] `ambush` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
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

## Triage (2026-05-27)

ready-for-agent, gated on [#00](00-situational-input-vocabulary.md). Goal: reuse `NavigateToTile` (move to a predicted enemy-path tile, then hold concealed). New inputs: `traversal_rate` (CombatDensityField at tile), `predictability` + `low_engagement` (shared with snipe). Gate: `requires cloak∨stealth` (capability — already in the synergy table). Effort: medium.

## Comments
