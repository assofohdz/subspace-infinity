# Snipe

Status: ready-for-agent
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 02 of 30. Catalogued in [ADR-0016 §Combat](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 1.

## What to build

Register the `snipe` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Engage from max effective range with precision weapons.

## Utility spec (from ADR-0016)

- **Hard gate:** `has_ranged_weapon ∧ los`
- **Situational fit:** `0.30 range_fit(opt=max) + 0.25 predictability + 0.20 approach_safety + 0.15 concealment + 0.10 low_engagement`
- **Capability affinity (synergy `bonus`):** rangeProfile, burstDamage
- **Substrate read:** perception + ThreatField

## Acceptance criteria

- [ ] `snipe` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
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

ready-for-agent, gated on [#00](00-situational-input-vocabulary.md). Goal: reuse `Engage` (fire at max effective range — `range_fit(opt=max)`). New inputs to add to the shared library: `predictability` (target heading-variance from perception history), `approach_safety` (mean ThreatField along approach vector — `arenaContext.threat`), `concealment` (own cloak/stealth active), `low_engagement` (own recent damage-in/out history). Gate: `has_ranged_weapon ∧ los`. Effort: medium — introduces the perception-history state shared with ambush + search.

## Comments
