# Combat: `Fire` action + `InWeaponRange` condition + `OrbitTarget` strafing

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 5 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

First slice where bots actually fight. Add weapon-fire action + range condition + orbital strafe steering; extend the Brawler BT with an Engage branch.

- **api/** — `infinity.ai.brain.actions.FireWeapon` Action leaf node. Emits a `FireRequest` Change-entity intent ([RaM pattern](../../../.claude/rules/replacement-as-mutation.md)) that gets drained by the existing `WeaponsProjectileSpawnSystem`. Bots fire by exactly the same mechanism as human ships — no AI bypass of `BombFireDelay` / `BulletFireDelay` / energy gating.
- **api/** — `infinity.ai.brain.conditions.InWeaponRange` Condition leaf — true when `Blackboard.target` is within the bot's effective bullet/bomb range (configurable on the BT instance; tuned via Groovy in slice 8).
- **api/** — `infinity.ai.steer.OrbitTarget` primitive — circle the target at desired radius (steers tangentially with corrective inward bias).
- **Brawler BT** — Selector becomes `Selector(EngageBranch, PursueBranch, WanderFallback)` where `EngageBranch = Sequence(HasTarget, InWeaponRange, SteerOrbitTarget, FireWeapon)`.
- Unit tests for `FireWeapon` (emits `FireRequest` with correct shape); `InWeaponRange` (boundary correctness); `OrbitTarget` (tangent direction + corrective bias).

## Acceptance criteria

- [ ] `FireWeapon` Action + `InWeaponRange` Condition exist in api/
- [ ] `infinity.ai.steer.OrbitTarget` exists in api/
- [ ] Brawler BT has the 3-branch Selector with EngageBranch
- [ ] Fire delays + ammunition + energy cost respected (verified by reading the same code paths human-driven fires go through)
- [ ] Unit tests for new leaves + OrbitTarget math
- [ ] **Demo:** launch arena → bot pursues player → when player is in range, bot orbits + fires bullets/bombs with reasonable lead
- [ ] License headers + SPDX on every new file
- [ ] PMD ratchet
- [ ] Layer test passes

## Blocked by

- [#04 — BT framework + Brawler skeleton](./04-bt-framework-brawler-skeleton.md)

## Comments
