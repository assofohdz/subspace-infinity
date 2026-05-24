# Switch-ships (meta)

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — behaviour 30 of 30. Catalogued in [ADR-0016 §Information / meta](../../../docs/adr/0016-bot-behaviour-catalog.md). Phase 2.

## What to build

The first **meta-behaviour**: decide that respawning as a different hull would
better serve the team. Unlike every other catalogued behaviour, `switch-ships`:

- does **not** produce a `TacticalGoal` or movement Execute Sequence — it produces a **ship-change action**;
- is **not** evaluated at planner cadence — it runs at **Tier 4 (event-driven)** per ADR-0016 §"Update cadence": on death/spawn, round start, or a significant team-composition change. Never per tick, never per planner tick.

It inverts capability derivation ([ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md)): where 0014 maps a ship → its behaviour weights, `switch-ships` maps the **team's behaviour gap** → the candidate hull whose derived `CapabilityProfile` best fills it.

## Utility spec (from ADR-0016)

- **Hard gate:** `requires arena_allows_shipswitch`
- **Situational fit** (scored for the best candidate hull): `0.45 team_comp_gap + 0.30 (candidate_value − current_value) + 0.25 (1 − switch_cost)`
- **Capability affinity (synergy `bonus`):** — (team-need-driven, not own-capability-driven)
- **Substrate read:** ADR-0015 (#06) team needs + ADR-0014 `CapabilityProfile` of candidate hulls + roster awareness + ship-switch mechanic
- **Cadence:** Tier 4 — dispatched by the death/spawn/round/composition-change event, not polled by the planner

## Acceptance criteria

- [ ] `switch-ships` evaluation: for each arena-allowed candidate hull, estimate team-value contribution given current roster + `ArenaObjective`; pick the best; switch iff `best_value − current_value > switch_threshold`
- [ ] Uses `team_comp_gap` (added to the ADR-0016 input vocabulary in this change) + candidate `CapabilityProfile` fit; defines `candidate_value`, `current_value`, `switch_cost` (bounty loss + respawn delay + lost position)
- [ ] Dispatched on Tier-4 events only (death/spawn, round start, team-composition change) with a debounce knob in `zone-bot-ai.groovy` — **not** registered as a per-planner-tick enumerating `Behaviour`
- [ ] Emits a ship-change action through the canonical ship-switch path (no AI bypass per ADR-0009 §3); no-ops gracefully when the arena locks ships
- [ ] `switch_threshold` + debounce + value/cost coefficients live in `engine-bot-ai.groovy` / `zone-bot-ai.groovy`
- [ ] Unit tests: gap→best-candidate selection; threshold respected (no thrash when marginal); arena-lock no-op; debounce prevents repeated switching
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#06 — Arena objectives + roles](../../bot-ai-v2/issues/06-arena-objectives-roles.md) (team needs / objective)
- needs team-roster awareness (per-arena, per-freq composition view)
- needs a ship-switch action mechanic (respawn-as-hull-X)

## Comments
