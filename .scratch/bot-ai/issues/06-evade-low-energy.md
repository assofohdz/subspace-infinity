# `Evade` primitive + `LowEnergy` condition

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 6 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Bots switch to evasion when low-energy; resume pursuit when recovered. Adds the fight-or-flight inflection.

- **api/** — `infinity.ai.steer.Evade` primitive — parametric counterpart to `Pursue(target)`. Predicts where threat will be in `leadTime` seconds and steers directly away from that point. (Unlike a generic `Flee`, `Evade(target)` takes a specific entity — addresses the chicken-framework `Flee`'s ID-filter hack by being parametric from the start.)
- **api/** — `infinity.ai.brain.conditions.LowEnergy` Condition leaf — true when current `Energy` value is below the configured threshold (a tunable; defaults to 60% of `EnergyStats.max`; final value comes from `bot-tuning.groovy` in slice 8).
- **Brawler BT** — root becomes `Selector(EvadeBranch, EngageBranch, PursueBranch, WanderFallback)` where `EvadeBranch = Sequence(LowEnergy, HasNearestThreat, SteerEvade)`.
- Unit tests for `Evade` (direction correctness against varying threat velocities); `LowEnergy` (threshold boundary).

## Acceptance criteria

- [ ] `infinity.ai.steer.Evade` exists in api/
- [ ] `LowEnergy` Condition exists in api/
- [ ] Brawler BT has the 4-branch Selector with EvadeBranch first
- [ ] Unit tests for Evade + LowEnergy
- [ ] **Demo:** launch arena with player + bot → fight the bot down to low energy → bot disengages and flees → bot energy recharges → bot resumes pursuit
- [ ] License headers + SPDX on every new file
- [ ] PMD ratchet
- [ ] Layer test passes

## Blocked by

- [#05 — Combat: Fire + Range + Orbit](./05-combat-fire-range-orbit.md)

## Comments
