# Communicate / call-target

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 22 of 30. Catalogued in [ADR-0016 §Information / meta](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 2.

## What to build

Register the `communicate / call-target` `Behaviour` ([ADR-0013](../../../docs/adr/0013-bot-tactical-goal-layer.md)) with its `synergy { }` entry ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)) and per-goal Execute Sequence. Broadcast a priority target/threat to the team (not a movement action — emits a comm/state signal, still scored so the bot "decides" to call).

## Utility spec (from ADR-0016)

- **Hard gate:** none
- **Situational fit:** `0.50 (high-value target spotted) + 0.30 (team unaware) + 0.20 (own safety to act)`
- **Capability affinity (synergy `bonus`):** xRadar, scout-capable
- **Substrate read:** comm channel ([ADR-0003](../../../docs/adr/0003-communication-channels.md))

## Acceptance criteria

- [ ] `communicate / call-target` `Behaviour` impl: `enumerate()` produces its goal candidates; `intrinsicScore()` implements the fit formula above over the ADR-0016 input vocabulary
- [ ] `synergy { }` entry in `engine-bot-ai.groovy`: `requires` + `bonus` matching the gate + affinity above; fit coefficients live in `engine-bot-ai.groovy`
- [ ] Goal record + `Execute<...>` BT sequence for carrying out the chosen goal
- [ ] Any input-vocabulary term this behaviour needs that is not already in the ADR-0016 table is added to the shared library + that table in the same change (never redefined inline)
- [ ] Weapon/item actions go through the canonical weapons/intent path (no AI bypass per ADR-0009 §3)
- [ ] Unit tests: `enumerate()` + `intrinsicScore()`; hard gate respected (ineligible ship never enumerates); Execute Sequence completes; goal expiry re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- needs comm channel per ADR-0003

## Comments
