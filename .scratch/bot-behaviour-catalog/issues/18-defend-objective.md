# Defend-objective

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 18 of 30. Catalogued in [ADR-0016 §Objective](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 2.

## What to build

Register the `defend-objective` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Stay near owned objective, prevent capture.

## Utility spec (from ADR-0016)

- **Hard gate:** `requires own team holds objective`
- **Situational fit:** `0.35 (enemies approaching) + 0.30 position_value + 0.20 (defensive tools) + 0.15 time_pressure`
- **Capability affinity (synergy `bonus`):** tankiness, antiwarp
- **Substrate read:** ADR-0015 (#06)

## Acceptance criteria

- [ ] `defend-objective` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
- [ ] `synergy { }` entry in `engine-bot-ai.groovy`: `requires` + `bonus` matching the gate + affinity above; fit coefficients live in `engine-bot-ai.groovy`
- [ ] Goal record + `Execute<...>` BT sequence for carrying out the chosen goal
- [ ] Any input-vocabulary term this behaviour needs that is not already in the ADR-0016 table is added to the shared library + that table in the same change (never redefined inline)
- [ ] Weapon/item actions go through the canonical weapons/intent path (no AI bypass per ADR-0009 §3)
- [ ] Unit tests: `enumerate()` + `intrinsicScore()`; hard gate respected (ineligible ship never enumerates); Execute Sequence completes; goal expiry re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#06 — Arena objectives + roles](../../bot-ai-v2/issues/06-arena-objectives-roles.md)

## Comments
