# Anchor

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 23 of 30. Catalogued in [ADR-0016 §Team / support](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 3.

## What to build

Register the `anchor` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Be the team's attach/warp point; survive at all costs.

## Utility spec (from ADR-0016)

- **Hard gate:** `requires attach-receive capability`
- **Situational fit:** `0.35 (own survivability) + 0.25 position_value + 0.25 (team needs forward presence) + 0.15 (safe pocket)`
- **Capability affinity (synergy `bonus`):** tankiness, attach-receive
- **Substrate read:** **attach-system PRD** + ally density

## Acceptance criteria

- [ ] `anchor` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
- [ ] `synergy { }` entry in `engine-bot-ai.groovy`: `requires` + `bonus` matching the gate + affinity above; fit coefficients live in `engine-bot-ai.groovy`
- [ ] Goal record + `Execute<...>` BT sequence for carrying out the chosen goal
- [ ] Any input-vocabulary term this behaviour needs that is not already in the ADR-0016 table is added to the shared library + that table in the same change (never redefined inline)
- [ ] Weapon/item actions go through the canonical weapons/intent path (no AI bypass per ADR-0009 §3)
- [ ] Unit tests: `enumerate()` + `intrinsicScore()`; hard gate respected (ineligible ship never enumerates); Execute Sequence completes; goal expiry re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [attach-system PRD](../../attach-system/PRD.md)

## Comments
