# Capability derivation — `ShipConfig` → `CapabilityProfile`

Companion to [ADR-0014](../adr/0014-capability-derived-bot-composition.md). Defines the concrete formulas that turn a ship's effective config into a normalized `CapabilityProfile`, which the synergy table then crosses into behaviour weights. This is **first-cut, tunable** — every coefficient lives in `engine-bot-ai.groovy` (per ADR-0014 §"Engine vs zone Groovy tiers"); the shapes here are the seed.

> **Design choices flagged for review** are marked **[CHOICE]**. They shape which hulls derive which behaviours, so they're balance-relevant — review before the impl lands.

## Inputs

Per-ship from `ShipConfig`:
- `ShipStat {initial, max, upgrade}` for `rotation`, `thrust`, `speed`, `recharge`, `energy` — derivation reads **`.max`** (the ship's ceiling, not its spawn value). **[CHOICE]** use `max`, not `initial`, so a ship's *potential* defines its identity.
- `radarRange` (double).
- Nullable weapon stats: `bombs`/`mines` (`BombLevel max` 1–4 + `fireDelayCs` + `speed`), `bullets` (`BulletLevel max` + `fireDelayCs` + `speed`), `bursts` (`max`, `speed`), `gravBombs`/`thors` (`CountWithDelayStats max` + `fireDelayCs`), `rockets`, and `CountStats max` for `repels`/`decoys`/`bricks`/`portals`.
- Nullable `StatusStats {status, energyDrainPer1000Cs}` for `cloak`/`stealth`/`xradar`/`antiwarp` — `status` is the 0/1/2 tri-state.

Per-arena from `ConfigRegistry` (the absolute weapon damage / blast radius per level — **not** in `ShipConfig`): the bomb/bullet damage + bomb blast-radius tables. **[CHOICE — v1 proxy]** v1 does **not** read these tables; it uses **weapon level (1–4) as a linear proxy** for per-shot damage and `level` as a proxy for blast radius. v2 reads the real `BombConfig`/bullet-damage tables from the registry. Flagged because the proxy under-weights the non-linear L1→L4 damage curve.

## `ArenaCapabilityNorms`

Per-arena maxima across that arena's ships, used to normalize each profile dimension to `[0,1]`. `fromConfigRegistry(registry)` computes, over `registry.shipConfigs()`: `maxSpeed`, `maxRotation`, `maxThrust`, `maxEnergy`, `maxRecharge`, and the raw (pre-normalization) `maxBurstDpsRaw`, `maxSustainedDpsRaw`, `maxAreaDamageRaw`, `maxRange`, `maxRechargeEconomy`. A dimension whose arena-max is 0 normalizes to 0 for every ship (avoid divide-by-zero). **Arena-scoped, recomputed on arena load + `arena.groovy` reload.**

## Continuous dimensions (each normalized to `[0,1]`)

`norm(v, max) = max <= 0 ? 0 : clamp(v / max)`.

- **mobility** = `norm( w_s·speed.max + w_r·rotation.max + w_t·thrust.max , w_s·N.maxSpeed + w_r·N.maxRotation + w_t·N.maxThrust )`. **[CHOICE]** weighted *sum* of the three (not the ADR-sketch product — sum degrades gracefully when one component is 0). Seed weights `w_s=w_r=w_t=1`.
- **tankiness** = `norm( geomean(energy.max, recharge.max) , geomean(N.maxEnergy, N.maxRecharge) )`. Geometric mean so a glass-cannon (huge energy, tiny recharge) isn't rated tanky.
- **rechargeEconomy** = `norm(recharge.max, N.maxRecharge)` — shots/sustain ceiling proxy. **[CHOICE]** recharge.max alone; refine to energy-per-shot vs recharge later.
- **burstDamage** = `norm(maxOverWeapons(perShotDmg(level) × shotsInOneSec(fireDelay)), N.maxBurstDpsRaw)`, where `shotsInOneSec = min(round(1000 / fireDelayMs), magazineCap)`. Peak 1-sec window. Uses the level proxy.
- **sustainedDamage** = `norm(maxOverWeapons(perShotDmg(level) / fireDelaySec), N.maxSustainedDpsRaw)` — DPS clamped by fire cadence (not magazine). Uses the level proxy.
- **areaDamage** = `norm( Σ over {bombs, gravBombs, bursts, thors} of (radius(level)² × maxCount) , N.maxAreaDamageRaw )`. **[CHOICE]** `radius(level) = level` (proxy); `maxCount` = inventory cap (bursts/thors) or 1 (bombs are unlimited → count 1). The radius² term makes splash hulls (Levi L4) dominate, which is intended.
- **rangeProfile** = `norm( max(bullet.speed × bulletAliveTime, bomb.speed × bombAliveTime) , N.maxRange )`. **[CHOICE]** alive-times are arena-global (`BulletConfig`/`BombConfig` lifetime); if unavailable in v1, use `speed` alone as the range proxy.

## Boolean gates

The profile carries the **full** gate set (decision 2026-05-25: every dimension in now, so all 30 catalog behaviours reference a present field). Three have no `ShipConfig` source yet and **placeholder-derive** until a source signal is wired — flagged here, not silently:

- `cloak` / `stealth` / `xradar` / `antiwarp` = the matching `StatusStats != null && status > 0` (tri-state: 1 = acquirable, 2 = starts-active; both qualify the ship as *capable*). **Source exists — derives now.**
- `bombBounce` / `bulletBounce` — **no per-ship bounce flag on `ShipConfig`** (bounce is a prize/arena setting). Field present; v1 placeholder-derives `false`. No current catalog consumer (the old `bomb-bank-shot` behaviour was folded into `area-denial`); retained forward-compat. Wiring a per-ship/arena bounce source → follow-up.
- `attachReceive` — **no attach flag on `ShipConfig`** (attach is a per-ship Subspace setting, traditionally the Terrier). Field present; v1 placeholder-derives `false`. Required by `anchor` (#23) + `attach-to-anchor` (#24) — they won't derive a non-zero gate until the source + the [attach-system](../../.scratch/attach-system/PRD.md) land. Follow-up.

## Raw inventory counts (NOT normalized — eligibility scorers read directly)

`maxMines` (`mines.max` level→count? **[CHOICE]** `MineStats` carries a `BombLevel max`, not a count — mine *count* cap is `LandmineFireDelay`-gated, not a `*Max`; v1 sets `maxMines = (mines != null) ? 1 : 0` as a has-mines gate until a true mine-count cap exists), `maxRepels`/`maxDecoys`/`maxBricks`/`maxPortals` = `CountStats.max`, `maxBursts` = `BurstStats.max`, `maxThors` = `CountWithDelayStats.max`.

## Recomputation

`CapabilityProfile` is cached as a server-only component on each bot, recomputed when the effective `ShipConfig` changes (prize state, ship change, live reload). The exact change-event subscription set is follow-up per ADR-0014 (spec'd as "recomputed on capability-affecting changes").

## Behaviour coverage

The profile carries **every** dimension referenced by the 30 catalog behaviours (decision: all in now). So all 30 have a place in the derivation:

- **25** derive from real `ShipConfig` sources today (the continuous dims, the four status gates, `maxMines`/`maxRepels`/`maxPortals`/`maxBursts`/`maxThors`/`maxDecoys`/`maxBricks`).
- **2** (`anchor`, `attach-to-anchor`) reference `attachReceive` — present but placeholder until a source signal lands.
- **3** (`recharge`, `resupply`, `switch-ships`) are capability-neutral by design — they read the profile for affinity but their eligibility is situational/objective/team (ADR-0015), not capability.

`bombBounce`/`bulletBounce` are present (forward-compat) with no current consumer.

## Literature validation (2026-05-25)

Checked against utility-AI canon. The profile design is sound: it is the recognized **normalized "clearing house" of agent data** that considerations read (Shaggy Dev; McGuire, *AI Decision-Making with Utility Scores*), and the static-capability vs per-tick-situational split matches the literature's consideration-input-source separation. The `[0,1]` normalize → combine → argmax shape is canonical (Utility system, Wikipedia).

**One delta, at the scoring layer (not the profile):** the canonical Infinite Axis Utility System (Dave Mark) combines considerations by **multiplication + a Compensation Factor + per-input response curves**; our synergy `bonus` (and ADR-0016 `situationalFit`) use a **convex weighted sum + explicit `requires` hard gates**. Multiplication gives a free veto (a zero input kills the action) at the cost of score-shrink-with-count; our additive form is forgiving and range-stable, and recovers the veto via the `requires` gates. Deliberate simplification. **Escalation trigger:** if a behaviour ever fires when a key input is low (additive forgiveness misbehaving), promote that behaviour's `bonus` to multiplicative considerations / response curves — no `CapabilityProfile` change needed.

## ShipConfig coverage — what's deliberately NOT captured

The profile is a **lossy** read: its surface = what the 30 behaviours consume, not all of `ShipConfig`. Of the 26 `ShipConfig` fields:

- **Omitted — not capability:** weapon `cost` (shop economy), `repellable` (defensive trait), `bomb.thrust`. Correctly out of scope.
- **Omitted — physics feel:** `linearDamping`, `turnResponsiveness`, `bounceRestitution`. These affect real maneuverability; `mobility` currently blends only the `*.max` ceilings. **Refinement:** fold damping/responsiveness into `mobility`'s formula later (it's a formula change, not a new dim).
- **Omitted — real capability, no consumer yet:** `radarRange` (vision *reach* — distinct from the xRadar gate), `rockets` (burst-escape speed/thrust). Add a dim the day a behaviour's synergy references one; until then it's a consumer-less field (YAGNI).

## Known v1 simplifications (all refine later, none block the pipeline)

1. Weapon **level used as a linear damage/radius proxy** (real per-level tables = v2).
2. **3 gates placeholder-derive `false`** pending a `ShipConfig` source: `bombBounce`, `bulletBounce`, `attachReceive` (see Boolean gates).
3. **maxMines is a 0/1 has-mines gate** (no true mine-count cap in `ShipConfig`).
4. **rangeProfile/areaDamage alive-times** fall back to `speed` if arena lifetime config isn't read in v1.

These keep the derivation runnable on the current `ShipConfig` while flagging exactly where it diverges from full fidelity — so a Shark-with-mines vs a Warbird still derive visibly different profiles (the point of #04), and the proxies get replaced without reshaping the pipeline.
