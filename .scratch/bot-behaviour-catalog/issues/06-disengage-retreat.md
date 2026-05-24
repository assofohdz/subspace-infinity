# Disengage / retreat

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 06 of 30. Catalogued in [ADR-0016 §Combat](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 1.

## What to build

Register the `disengage / retreat` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Break contact to recharge.

## Utility spec (from ADR-0016)

- **Hard gate:** `self_energy_pct < threshold`
- **Situational fit:** `0.40 (1−energy_adv) + 0.30 threat_density + 0.30 escape_route`
- **Capability affinity (synergy `bonus`):** mobility (repel/portal raise it)
- **Substrate read:** ThreatField + nav

## Acceptance criteria

- [ ] `disengage / retreat` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
- [ ] `synergy { }` entry in `engine-bot-ai.groovy`: `requires` + `bonus` matching the gate + affinity above; fit coefficients live in `engine-bot-ai.groovy`
- [ ] Goal record + `Execute<...>` BT sequence for carrying out the chosen goal
- [ ] Any input-vocabulary term this behaviour needs that is not already in the ADR-0016 table is added to the shared library + that table in the same change (never redefined inline)
- [ ] Weapon/item actions go through the canonical weapons/intent path (no AI bypass per ADR-0009 §3)
- [ ] Unit tests: `enumerate()` + `intrinsicScore()`; hard gate respected (ineligible ship never enumerates); Execute Sequence completes; goal expiry re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#05 — TacticalPlanner](../../bot-ai-v2/issues/05-tactical-planner-baseline-behaviours.md)
- [#07 — Spatial fields](../../bot-ai-v2/issues/07-spatial-fields-chokepoints-follow-traffic.md)
- [#03 — Flow-field navigation](../../bot-ai-v2/issues/03-flow-field-navigation.md)

## Comments
