# Situational-input vocabulary (foundation)

Status: ready-for-agent
Category: enhancement
Type: HITL

## Parent

[Bot behaviour catalog PRD](../PRD.md) — **foundation slice** (not a behaviour; precedes behaviours 01–30). Realises the ADR-0016 §"Shared input vocabulary" + §"`situationalFit` shape" contract that the behaviour issues assume is already free.

## Why this exists

The catalog issues (01–30) each say *"`intrinsicScore()` implements the fit formula over the ADR-0016 input vocabulary"* and *"any input not already in the table is added to the shared library."* But **there is no shared library yet.** The 5 registered behaviours (`Engage`/`Disengage`/`Search`/`FollowTraffic`/`HoldPosition`) are baseline stubs: `EngageBehaviour.intrinsicScore()` returns raw `BaselineFit.energyFraction()`, not the spec'd `0.30·range_fit + …`. Adding more siblings before the vocabulary exists forces each author to hand-roll `range_fit`/`los`/`support` inline — the exact drift ADR-0016 §Decision forbids ("never redefined inline").

This slice builds the substrate once, sources the inputs that are cheaply reachable, and **proves it end-to-end by converting `engage` from stub → the real ADR-0016 formula** (engage is the ADR's worked example). After it lands, a new behaviour is genuinely additive: gate + coefficients + goal + Execute.

## What to build

1. **`SituationalInputs` vocabulary library** — a server-side helper (e.g. `infinity.ai.tactical.SituationalInputs`) that computes the normalized `[0,1]` scalars from `Blackboard` (`self`, `target`, `perception`, `currentEnergy`/`maxEnergy`, `ownFreq`, `arenaContext`) **once per planner cycle**. One named accessor per ADR-0016 vocabulary row; behaviours call by name, never recompute. `clamp(x) ≡ max(0,min(1,x))` lives here. Tier-2 cost (per ADR-0016 §"Update cadence") — inputs are *reads* of Tier-3 field snapshots, not recomputes; the one allowed per-call compute is the `los` Bresenham line.

2. **Source the engage inputs** (the proof set):
   - `range_fit` — `1 − clamp(|dist(self,target) − range_opt| / range_opt)`; `range_opt` from the bot's weapon range (ship config / weapon stats).
   - `energy_adv` — `clamp(self_energy_pct − target_energy_pct + 0.5)`. **Needs target energy %, which `NearbyShip` does not carry today** (`id, position, orientation, velocity, frequency`). Add target energy (and bounty, below) to the perception projection / `NearbyShip` — see §"Data-sourcing sub-tasks".
   - `recharge_rdy` — `1` if own weapon cooldown elapsed else `0` (own weapon state).
   - `support` — `clamp(allies_in_radius(self, R) / 2)` from `perception.allies()` (count within R). Names the `AllyDensityField` read; a perception-list count is an acceptable first source.
   - `bounty_pull` — `clamp(target_bounty / B_ref)`. **Needs target bounty on `NearbyShip`.**
   - `los` — `1` if Bresenham line-of-sight to target clear else `0`. Reuse the nav LoS raycast if one exists (slice #03 shipped LoS); else add a one-line Bresenham helper.

3. **Fit coefficients in Groovy.** Add a per-behaviour fit-coefficient block to `engine-bot-ai.groovy` (sibling to `synergy { }`), a loader to read it, and a `*Config`/record to carry it (ADR-0006 / Rule #3 — coefficients are tuning knobs, not Java literals). `SituationalInputs` consumers read coefficients from config, never hardcode. Seed values = the ADR-0016 per-behaviour coefficients.

4. **Convert `engage` to the real formula** as the proving consumer: `EngageBehaviour.intrinsicScore` = `0.30·range_fit + 0.25·energy_adv + 0.15·recharge_rdy + 0.15·support + 0.15·bounty_pull`, coefficients from Groovy, inputs from `SituationalInputs`. Add the `has_offensive_weapon ∧ los` hard gate to `enumerate()` (no candidate when los is false).

## Data-sourcing sub-tasks (cross-layer)

- **Target energy % + bounty on `NearbyShip`** — extend the perception projection that builds `NearbyShip` to read the target's `Energy`/`EnergyStats` (→ pct) and bounty/score component. `NearbyShip` is in `api/` (immutable record) — adding fields is a wire-contract change; keep it minimal and document units. Verify the perception builder has access to those components server-side.
- **`B_ref` (bounty reference), `R` (support radius), `range_opt` fallbacks** — tuning knobs → `engine-bot-ai.groovy` (or `zone-bot-ai.groovy` if arena-tunable), not Java literals.

## Acceptance criteria

- [ ] `SituationalInputs` computes, at minimum, `range_fit`, `energy_adv`, `recharge_rdy`, `support`, `bounty_pull`, `los` — each named, each on `[0,1]`, computed once per planner cycle
- [ ] Target energy % + bounty reach the behaviour layer (perception projection extended; `NearbyShip` carries them or an adjacent snapshot does)
- [ ] `engine-bot-ai.groovy` carries a fit-coefficient block; a loader + `*Config` expose it; `EngageBehaviour` reads coefficients from config (no hardcoded fit weights)
- [ ] `EngageBehaviour` upgraded to the ADR-0016 formula over `SituationalInputs`; `has_offensive_weapon ∧ los` hard gate enforced in `enumerate()`
- [ ] `BaselineFit.energyFraction` either folded into `SituationalInputs` or retained only for behaviours not yet upgraded (note which)
- [ ] ADR-0016 §"Shared input vocabulary" table updated only if a genuinely new input is introduced (the engage set is all already listed — expect no table change)
- [ ] Unit tests: each sourced input on representative states (in-range/out-of-range, energy adv/disadv, los clear/blocked, allies present/absent); `EngageBehaviour.intrinsicScore` reproduces the formula from known inputs; coefficient reload changes the score
- [ ] PMD ratchet on touched files; layer test passes (`NearbyShip` change stays api-clean)

## Blocked by

- [#05 — TacticalPlanner](../../bot-ai-v2/issues/05-tactical-planner-baseline-behaviours.md) (landed)
- [#07 — Spatial fields](../../bot-ai-v2/issues/07-spatial-fields-chokepoints-follow-traffic.md) (landed — `ScalarField` threat/opportunity available)

## Unblocks

Every Phase-1 behaviour issue (01–10). Each then adds only the *new* inputs it first needs (e.g. `predictability` for snipe/ambush, `isolation` for assassinate, `traversal_rate` for ambush/area-denial), extending the shared library — never redefining inline.

## Comments
