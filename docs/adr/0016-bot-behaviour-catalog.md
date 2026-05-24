# ADR 0016 — Bot behaviour catalog + utility-score specification

**Status:** Proposed
**Date:** 2026-05-24
**Deciders:** Asser Fahrenholz

## Context

[ADR-0013](./0013-bot-tactical-goal-layer.md) established the *mechanism* — a `TacticalPlanner` picks one `TacticalGoal` per slow tick; named `Behaviour` building blocks enumerate candidate goals and score them by intrinsic utility; the planner multiplies intrinsic score × effective weight + a stickiness margin. [ADR-0014](./0014-capability-derived-bot-composition.md) decided *where the weight comes from* — derived from a ship's `CapabilityProfile` × an engine-authored `synergy { }` table. [ADR-0015](./0015-arena-objective-and-roles.md) added *objective + role bias* as further multiplicative weight sources.

What none of them settle is the **roster** and the **per-behaviour utility shape**. ADR-0013 gives three example behaviours; the bot-AI v2 PRD named ~7. Implementation slices kept *inventing* a behaviour (and an implicit archetype) as a side-effect of demoing a spatial service — the exact "emergence-from-infrastructure" anti-pattern [ADR-0014](./0014-capability-derived-bot-composition.md) flagged. The behaviour layer is the **user-facing surface** (it is *what a bot does*); it warrants a deliberate, enumerated catalog and a standardized scoring contract, not ad-hoc per-slice invention.

This ADR is that catalog. It does not change the planner, the capability-derivation pipeline, or the objective/role bias — it **instantiates** them for a concrete, enumerated set of behaviours, and pins the concrete shape of `Behaviour.intrinsicScore()` that ADR-0013 left abstract.

### What this ADR does not settle

- **The planner / weight / bias machinery** — owned by [ADR-0013](./0013-bot-tactical-goal-layer.md) / [ADR-0014](./0014-capability-derived-bot-composition.md) / [ADR-0015](./0015-arena-objective-and-roles.md). This ADR consumes them unchanged.
- **The spatial substrate** the inputs read from — owned by [ADR-0011](./0011-bot-navigation-navmesh.md) / [ADR-0012](./0012-bot-spatial-analysis-services.md).
- **Per-goal Execute Sequences** (the BT branches that *carry out* a chosen goal) — per-behaviour implementation work, specified in each behaviour's issue, not here.
- **Multi-bot squad coordination.** Behaviours score for a single bot from that bot's world view. Team-level goal arbitration (one bot mines while another distracts) is a future layer; the team-aware behaviours here (push, escort, anchor) read *shared* fields, they do not negotiate.
- **Online weight learning.** Weights are derived + authored, never learned. Out of scope, as in ADR-0014.

## Decision

**Bot behaviours are a fixed, enumerated catalog. Every behaviour is a registered `Behaviour` ([ADR-0013](./0013-bot-tactical-goal-layer.md)) with exactly one `synergy { }` entry ([ADR-0014](./0014-capability-derived-bot-composition.md)) and a utility score of the canonical form below. The score draws its situational terms from a single shared, normalized input vocabulary defined once in this ADR. There is no per-ship hard-coded role table — "which ship does this behaviour" is decided entirely by the capability gate + derived weight; named archetypes never reappear.**

### The canonical utility equation

Every behaviour scores each of its candidate goals as:

```
U(b, goal) = hardGate(b)              // {0,1} — capability requires{} AND situational booleans (los, in-range)
           × situationalFit(b, goal)  // [0,1] — normalized weighted sum over the input vocabulary
           × effectiveWeight(b)       // [0,∞) — derived(capability) × objectiveBias × roleBias  (ADR-0014 × ADR-0015)
```

The planner picks `argmax U` across all eligible behaviours' candidate goals, applies the stickiness margin against the running goal (ADR-0013), and dispatches.

Mapping to the four factors from the design conversation:

| Design-conversation factor | Term above | Owned by |
|---|---|---|
| capability match | `hardGate` (the `requires{}` clause) + the capability part of `effectiveWeight` | ADR-0014 |
| situational fit | `situationalFit` — **the new per-behaviour formula this ADR pins down** | this ADR |
| opportunity cost | implicit in `argmax` — a behaviour loses by scoring lower than a rival, plus stickiness | ADR-0013 |
| role bias ("small constant toward natural roles, without hard-coding") | `effectiveWeight`'s derived + objective + role factors | ADR-0014 / ADR-0015 |

**Role affinity is not a hard-coded per-ship constant.** "Terrier → Anchor, Javelin → Area-denial" falls out of derivation: a Terrier's high tankiness + attach-receive capability derives a high `anchor` weight; a Javelin's `bombBounce` + `areaDamage` derives a high `area-denial` weight. The synergy `bonus { }` clause *is* the tie-breaking nudge, expressed as a function of capability rather than a ship name.

### `situationalFit` shape

A behaviour's `situationalFit` is a **convex weighted sum** of normalized inputs (coefficients sum to 1.0, each input on `[0,1]`), so the product stays on `[0,1]` and behaviours are comparable. Worked example for `engage`:

```
hardGate     = has_offensive_weapon · los
situationalFit = 0.30·range_fit + 0.25·energy_adv + 0.15·recharge_rdy
               + 0.15·support    + 0.15·bounty_pull
U_engage     = hardGate · situationalFit · effectiveWeight('engage')
```

The coefficients are tuning knobs and live in `engine-bot-ai.groovy` ([ADR-0006](./0006-tuning-knobs-vs-magic-numbers.md) / [ADR-0014](./0014-capability-derived-bot-composition.md) engine tier) alongside the synergy table — an engineer rebalances "range matters more than bounty for engage" without recompiling. The per-behaviour entries below give *seed* coefficients.

### Shared input vocabulary

All `situationalFit` formulas draw from this fixed library of normalized scalars. Defined once; behaviours reference by name. `clamp(x)` ≡ `max(0, min(1, x))`. Each is computed by the perception layer / spatial fields ([ADR-0012](./0012-bot-spatial-analysis-services.md)) per scoring tick.

| Input | Definition (all → `[0,1]`) | Source |
|---|---|---|
| `range_fit` | `1 − clamp(|dist(self,target) − range_opt| / range_opt)` | perception + ship weapon range |
| `energy_adv` | `clamp(self_energy_pct − target_energy_pct + 0.5)` | perception |
| `recharge_rdy` | `1` if weapon cooldown elapsed else `0` | own weapon state |
| `los` | `1` if Bresenham line-of-sight to target clear else `0` | one-off raycast |
| `support` | `clamp(allies_in_radius(self, R) / 2)` | `AllyDensityField` |
| `bounty_pull` | `clamp(target_bounty / B_ref)` | perception |
| `isolation` | `clamp(dist(target, target_nearest_ally) / iso_ref)` | `EnemyDensityField` |
| `predictability` | `clamp(1 − heading_variance(target))` — moving linearly ⇒ high | perception history |
| `concealment` | `1` if own cloak/stealth active else `0` | own status |
| `approach_safety` | `1 − clamp(mean ThreatField along approach vector)` | `ThreatField` |
| `escape_route` | `clamp(open-escape-vector fraction)` — low threat behind self | `ThreatField` + nav |
| `threat_density` | `clamp(ThreatField.valueAt(self) / threat_ref)` | `ThreatField` |
| `position_value` | tile value for the current objective/role | `ArenaObjective` (ADR-0015) |
| `mine_headroom` | `1 − clamp(mines_at_tile / saturation_cap)` | mine query |
| `traversal_rate` | `clamp(CombatDensityField.valueAt(tile) / trav_ref)` | `CombatDensityField` |
| `numerical_adv` | `clamp((allies − enemies in sector)/N + 0.5)` | ally/enemy density |
| `item_charges` | `clamp(charges / max_charges)` for the relevant item | own inventory |
| `time_pressure` | `clamp(1 − objective_timer_remaining / timer_max)` | `ArenaObjective` |
| `intel_gap` | `clamp(fog_coverage(key_sector))` | fog-of-war model |
| `low_engagement` | `1 − clamp(own recent-damage-in/out)` — am I free right now | perception history |
| `team_comp_gap` | `clamp(Σ unfilled needed-role weight / gap_ref)` — how under-filled the team's needed roles are | `ArenaObjective` (ADR-0015) + roster |

New inputs land with the first behaviour that needs them; the table is extended in the same change, never duplicated into a behaviour entry.

### The catalog

Six categories, 30 behaviours. Each entry: **gate** (`requires{}` + situational booleans), **fit** (seed coefficients over the vocabulary), **affinity** (the capability dimensions whose `bonus{}` nudges this up — *not* a ship list), **substrate** (what it reads beyond the core planner; gates its implementation phase).

The roster was reviewed (2026-05-24) against industry MP-shooter bot taxonomy (Killzone hierarchical MP-bot system; FPS Behaviour-Tree combat sets) and utility-AI canon (Dave Mark; Kevin Dill; Bill Merrill's utility-in-BT). `search` and `regroup` were added from that pass; deliberately-excluded standard behaviours are noted in §"Considered and excluded" below.

Baseline locomotion behaviours `wander`, `navigate-to-target`, `follow-traffic` are delivered by bot-AI v2 substrate slices (#05/#07) and are listed for completeness without a catalog issue.

#### Combat

- **engage** — close to optimal range and shoot a target. Gate: `has_offensive_weapon ∧ los`. Fit: `0.30 range_fit + 0.25 energy_adv + 0.15 recharge_rdy + 0.15 support + 0.15 bounty_pull`. Affinity: sustainedDamage, mobility. Substrate: perception + ThreatField.
- **snipe** — engage from max effective range with precision weapons. Gate: `has_ranged_weapon ∧ los`. Fit: `0.30 range_fit(opt=max) + 0.25 predictability + 0.20 approach_safety + 0.15 concealment + 0.10 low_engagement`. Affinity: rangeProfile, burstDamage. Substrate: perception + ThreatField.
- **assassinate / pick** — single out a high-value isolated target. Gate: `has_offensive_weapon ∧ los`. Fit: `0.30 bounty_pull + 0.30 isolation + 0.20 approach_safety + 0.20 concealment`. Affinity: mobility, burstDamage, cloak/stealth. Substrate: EnemyDensityField + perception.
- **ambush** — wait concealed along an expected enemy path. Gate: `requires cloak∨stealth`. Fit: `0.35 traversal_rate + 0.25 concealment + 0.20 low_engagement + 0.20 predictability`. Affinity: cloak, stealth. Substrate: EnemyDensityField prediction + nav.
- **harass** — pressure without committing; force energy drain. Gate: `has_offensive_weapon`. Fit: `0.30 range_fit + 0.25 escape_route + 0.25 energy_adv + 0.20 (rechargeEconomy adv)`. Affinity: mobility, rechargeEconomy. Substrate: perception + ThreatField.
- **disengage / retreat** — break contact to recharge. Gate: `self_energy_pct < threshold`. Fit: `0.40 (1−energy_adv) + 0.30 threat_density + 0.30 escape_route`. Affinity: mobility (repel/portal raise it). Substrate: ThreatField + nav. *Disengage is the combat-tier reaction (break contact); `recharge` (self-preservation) is the goal it leads into.*
- **search / re-acquire** — target lost line-of-sight; move to its last-known tile and sweep to re-establish contact. Gate: `had_recent_target ∧ ¬los`. Fit: `0.40 (target-recency) + 0.30 (last-known proximity) + 0.30 low_engagement`. Affinity: mobility, xRadar. Substrate: perception history + nav. *Distinct from `scout` (team intel gathering) and `anti-stealth-hunt` (counter-cloak); this is single-target re-acquisition.*

#### Spatial / map control

- **area-denial** — seed mines/bombs into a high-traffic corridor. Gate: `requires maxMines>0 ∨ bombs`. Fit: `0.35 traversal_rate + 0.30 mine_headroom + 0.20 position_value + 0.15 (choke strength)`. Affinity: maxMines, areaDamage. Substrate: CombatDensityField + chokepoint list.
- **hold-position / zone-control** — occupy a valuable tile and resist displacement. Gate: none. Fit: `0.40 position_value + 0.25 support + 0.20 (defensive item) + 0.15 (energy buffer)`. Affinity: tankiness, antiwarp. Substrate: chokepoint list / ArenaObjective tiles.
- **push / advance** — move the team frontline forward. Gate: none. Fit: `0.35 numerical_adv + 0.25 support + 0.20 mine_headroom(ahead) + 0.20 (recent-kill momentum)`. Affinity: mobility, tankiness. Substrate: ally/enemy density + ArenaObjective.
- **flank** — indirect route to attack from an unexpected angle. Gate: none. Fit: `0.35 mobility-fit + 0.25 (alt-route availability) + 0.20 (enemy attention elsewhere) + 0.20 approach_safety`. Affinity: mobility. Substrate: flow-field alt routes + EnemyDensityField.
- **choke / cut-off** — block enemy retreat or reinforcement. Gate: `requires antiwarp ∨ maxPortals>0`. Fit: `0.35 position_value(between) + 0.30 (item available) + 0.20 traversal_rate + 0.15 numerical_adv`. Affinity: antiwarp, maxPortals. Substrate: ArenaObjective + nav.

#### Team / support

- **anchor** — be the team's attach/warp point; survive at all costs. Gate: `requires attach-receive capability`. Fit: `0.35 (own survivability) + 0.25 position_value + 0.25 (team needs forward presence) + 0.15 (safe pocket)`. Affinity: tankiness, attach-receive. Substrate: **attach-system PRD** + ally density.
- **attach-to-anchor** — ride a teammate to the fight. Gate: `requires attach available ∧ anchor exists`. Fit: `0.40 (anchor well-positioned) + 0.30 (own slowness) + 0.30 (delivery need)`. Affinity: low mobility (Lev-type). Substrate: **attach-system PRD**.
- **escort / bodyguard** — stay near a high-value teammate, intercept threats. Gate: none. Fit: `0.35 (ward is anchor/flagger/low-energy) + 0.30 (threats inbound) + 0.20 (intercept ability) + 0.15 support`. Affinity: mobility, repel. Substrate: ADR-0015 role + ally density.
- **regroup / rally** — consolidate toward the ally centroid when scattered or outnumbered. Gate: none. Fit: `0.40 (1−numerical_adv) + 0.30 (dispersion from allies) + 0.30 threat_density`. Affinity: mobility. Substrate: ally density + nav. *Distinct from `push` (advance when ahead) and `escort` (guard one specific ally); regroup triggers on a numerical/dispersion deficit.*
- **repel/portal support** — provide tactical-utility items to team. Gate: `requires maxRepels>0 ∨ maxPortals>0`. Fit: `0.40 (ally in danger within range) + 0.30 item_charges + 0.30 (evac need)`. Affinity: maxRepels, maxPortals. Substrate: **item systems** + ally density.
- **sweep / clear** — remove enemy mines/bombs from a needed corridor. Gate: `requires (can absorb/trigger safely)`. Fit: `0.40 (corridor needed soon) + 0.35 (mine density) + 0.25 tankiness-fit`. Affinity: tankiness. Substrate: mine model + ArenaObjective.
- **spot / scout** — gather enemy-position intel. Gate: `requires xRadar`. Fit: `0.40 intel_gap + 0.30 low_engagement + 0.30 (team lacks intel)`. Affinity: xRadar, mobility. Substrate: **xradar + fog-of-war model**.
- **anti-stealth hunt** — actively counter cloaked enemies. Gate: `requires xRadar ∨ antiwarp`. Fit: `0.40 (cloaker inferred present) + 0.35 (own detection) + 0.25 (sector to sweep)`. Affinity: xRadar, antiwarp. Substrate: **xradar/antiwarp** + cloak-inference.

#### Objective

- **capture-objective** — grab flag/ball/control point. Gate: `requires arena has capturable objective`. Fit: `0.35 (objective contestable) + 0.30 (ship suited to carry) + 0.20 support + 0.15 approach_safety`. Affinity: mobility, tankiness. Substrate: **ADR-0015 ArenaObjective (#06)** + mechanic module.
- **defend-objective** — stay near owned objective, prevent capture. Gate: `requires own team holds objective`. Fit: `0.35 (enemies approaching) + 0.30 position_value + 0.20 (defensive tools) + 0.15 time_pressure`. Affinity: tankiness, antiwarp. Substrate: **ADR-0015 (#06)**.
- **deny-objective** — prevent enemy holding/scoring when you can't take it. Gate: `requires enemy holds objective`. Fit: `0.40 (can kill carrier / disrupt) + 0.35 (can't currently capture) + 0.25 position_value`. Affinity: burstDamage, areaDamage. Substrate: **ADR-0015 (#06)**.
- **spawn-camp / pressure regen** — pressure enemies at respawn/recharge. Gate: none. Fit: `0.40 (enemy team broken) + 0.35 numerical_adv + 0.25 (low overextension risk)`. Affinity: mobility, sustainedDamage. Substrate: spawn-zone model + ally/enemy density.

#### Self-preservation

- **recharge** — return to safe zone / back line to restore energy. Gate: `self_energy_pct < threshold`. Fit: `0.45 (1−energy_adv) + 0.30 (safe zone reachable) + 0.25 (no urgent team need)`. Affinity: — (universal). Substrate: **safe-zones PRD** + nav.
- **resupply / rearm** — return to refill items (shop zones). Gate: `requires items depleted ∧ shop accessible`. Fit: `0.40 (items depleted) + 0.30 (currency available) + 0.30 low_engagement`. Affinity: item-dependent ships. Substrate: **flat-shop PRD** + currency.
- **bait** — appear vulnerable to draw enemies into a trap. Gate: `requires trap exists (allied ambush / minefield)`. Fit: `0.40 (trap exists) + 0.35 escape_route + 0.25 (target greedy/aggressive)`. Affinity: mobility. Substrate: team-coordination + mine/ambush awareness.

#### Information / meta

- **communicate / call-target** — broadcast a priority target/threat to the team. *Not a movement action* — emits a comm/state signal, still scored so the bot "decides" to call. Gate: none. Fit: `0.50 (high-value target spotted) + 0.30 (team unaware) + 0.20 (own safety to act)`. Affinity: xRadar, scout-capable. Substrate: comm channel ([ADR-0003](./0003-communication-channels.md)).
- **reposition for next phase** — move toward where the fight is *about to be*. Gate: none. Fit: `0.40 (objective-shift prediction) + 0.35 (fight outcome trending) + 0.25 (travel time acceptable)`. Affinity: mobility. Substrate: **ADR-0015 (#06)** phase prediction + density trend.
- **switch-ships** *(meta — event-cadence, see §Update cadence)* — decide that respawning as a different hull would better serve the team. Does **not** produce a `TacticalGoal`; produces a ship-change action. Evaluated only at decision points (death/spawn, round start, team-composition change), never per planner tick. Gate: `requires arena_allows_shipswitch`. Fit (scored for the best candidate hull): `0.45 team_comp_gap + 0.30 (candidate_value − current_value) + 0.25 (1 − switch_cost)`. Affinity: — (team-need-driven, not own-capability-driven). Substrate: **ADR-0015 (#06)** team needs + **ADR-0014** `CapabilityProfile` of candidate hulls + roster awareness + ship-switch mechanic. *Neatly inverts capability derivation: 0014 maps ship → behaviour weights; switch-ships maps the team's behaviour gap → the candidate ship whose derived profile best fills it.*

### Considered and excluded

Standard MP-bot behaviours deliberately left out, recorded so a future reader knows they were weighed, not forgotten:

- **take-cover / use-cover** — there is no cover in a 2D top-down arena; [ADR-0012](./0012-bot-spatial-analysis-services.md) already dropped `CoverFinder` for the same reason. `hold-position` (occupy a tile) is the nearest spatial analogue.
- **suppress / suppressive fire** — pinning an enemy behind cover has no mechanic here (no cover; fast TTK). `harass` (pressure + energy drain) and `area-denial` (seed persistent threats) cover the intent.
- **melee** — no melee mechanic in Subspace.
- **patrol** — route-based idle movement is covered by the baseline `wander` + `follow-traffic` and, for owned territory, by `hold-position`; a separate patrol behaviour would duplicate them.

### Implementation phases

Each behaviour's substrate column gates when its issue is buildable. Three phases:

- **Phase 1 — v2.0** (substrate from bot-AI v2 slices #01–#07): engage, snipe, assassinate, ambush, harass, disengage, search, area-denial, hold-position, flank.
- **Phase 2 — v2.x** (needs ADR-0015 objective/role #06 + team-density model): push, choke, escort, regroup, sweep, spawn-camp, capture-objective, defend-objective, deny-objective, reposition-for-next-phase, bait, communicate, switch-ships.
- **Phase 3 — v3** (needs a separate substrate PRD): anchor + attach-to-anchor (attach-system), repel/portal-support (item systems), spot/scout + anti-stealth-hunt (xradar + fog-of-war), recharge (safe-zones), resupply (flat-shop).

Every behaviour gets an implementation issue now (per the 2026-05-24 decision); Phase 2/3 issues carry an explicit `Blocked by` on the substrate PRD and stay `needs-triage` until it lands.

### Update cadence — which calculation runs at what rate

Not every calculation runs every tick; the brain is a tiered-cadence system. This consolidates rates that the individual ADRs own, so the cost model is legible in one place.

| Tier | Rate | What runs | Owner |
|---|---|---|---|
| 0 — physics | per physics step | `PlayerDriver` applies `MovementInput` | engine |
| 1 — reactive | per tick (~30 Hz) | BT tick + steering (`SeekDirection`, `AvoidObstacles`); reads **pre-computed** gradients/fields | [ADR-0013](./0013-bot-tactical-goal-layer.md) |
| 2 — planner | ~150 ms (zone-tunable) | `TacticalPlanner` enumerates eligible behaviours, evaluates `situationalFit`, applies stickiness, picks the goal | [ADR-0013](./0013-bot-tactical-goal-layer.md) |
| 3 — fields | ~330 ms / per-event | dynamic `ScalarField`s recompute; static + nav flow fields build **async at load**, cached | [ADR-0012](./0012-bot-spatial-analysis-services.md) / [ADR-0011](./0011-bot-navigation-navmesh.md) |
| 4 — decision-point | event-driven | meta-behaviours (`switch-ships`) evaluated only on death/spawn, round start, or team-composition change — **never periodic** | this ADR |

Consequences for behaviour authors:

- **`situationalFit` is a Tier-2 cost, not Tier-1.** A behaviour's weighted-sum runs at planner cadence (~150 ms), so a dozen behaviours each summing ~5 inputs is cheap; the per-tick budget is spent on BT + steering only.
- **Inputs are reads, not computes.** Vocabulary inputs that derive from fields (`threat_density`, `traversal_rate`, `support`, …) sample the latest Tier-3 snapshot; the behaviour never triggers a field recompute. An input that needs a fresh per-call computation (e.g. `los` raycast) must be cheap (one Bresenham line) — anything heavier becomes a throttled field instead.
- **Meta-behaviours declare Tier 4.** A behaviour whose evaluation is expensive *and* rarely-changing (roster comparison, ship-value estimation) is event-driven, not periodic. `switch-ships` is the canonical Tier-4 entry; future "what should my loadout be" style decisions join it. Tier-4 behaviours are dispatched by the event that makes them relevant, not polled by the planner.
- **Cadence numbers are zone-tier knobs** (`zone-bot-ai.groovy`, per [ADR-0014](./0014-capability-derived-bot-composition.md) §"Engine vs zone Groovy tiers"): planner cadence + stickiness, field cadences, and the Tier-4 trigger debounce. Defaults are seeds; profile after Phase 1 lands.

## Consequences

**Positive.**
- The behaviour layer is enumerated and deliberate — no more archetype-by-side-effect. "What can a bot do?" has one authoritative answer.
- `situationalFit` has one shape and one input vocabulary, so behaviours are comparable, the planner's `argmax` is meaningful, and a new behaviour is a small, well-shaped unit (gate + weighted sum + synergy line + Execute Sequence).
- Tuning is centralized: every coefficient lives in `engine-bot-ai.groovy`; rebalancing is a Groovy edit, not a recompile.
- Substrate dependencies are explicit, so the catalog honestly phases across releases instead of pretending everything ships in v2.0.

**Negative / costs.**
- 27 behaviours is a large backlog; most are Phase 2/3 and will sit blocked. The catalog risks looking more "done" than it is — mitigated by the phase table + per-issue `Blocked by`.
- The shared vocabulary is a coupling point: changing an input's definition touches every behaviour that references it. Accepted — it's the same trade as any shared library; the alternative (per-behaviour input redefinition) drifts worse.
- Convex-weighted-sum fit cannot express strongly non-linear interactions (e.g. "only if BOTH A and B are high"). Where that's needed, a behaviour uses a `hardGate` boolean or a product term; if that proves insufficient, response curves (Dave Mark) are the documented escalation, deferred until a concrete behaviour demands them.

## Alternatives considered

- **Leave behaviours ad-hoc per slice (status quo).** Rejected — it reproduces the archetype-by-side-effect anti-pattern and yields incomparable, per-author scoring shapes.
- **Per-behaviour self-contained input definitions** (no shared vocabulary). Rejected (2026-05-24 decision) — massive duplication; inputs like `range_fit` would drift across behaviours.
- **Full GOAP action-graph planning.** Rejected for the same reason as ADR-0013 — utility selection of one goal + hand-authored Execute Sequences is sufficient and far cheaper to author; revisit only if action *sequences* need dynamic composition.
- **Response-curve utility (per-input non-linear curves) up front.** Deferred — convex weighted sums are simpler to author and reason about; promote per-input curves only when a behaviour provably needs one.

## References

- David Mark. *Behavioral Mathematics for Game AI* (2009) — utility scoring, response curves, the weighted-sum/argmax shape. Same source ADR-0013 cites.
- Kevin Dill, "Introduction to Utility Theory" in *Game AI Pro* (2013) — the modular utility-curve architecture this catalog's `situationalFit` is a simplified form of.
- Bill Merrill, "Building Utility Decisions into Your Existing Behavior Tree" in *Game AI Pro* (2014) — the utility-scored-selection-inside-a-BT hybrid that ADR-0013 + this catalog form together.
- Remco Straatman et al. (Guerrilla Games), *A Hierarchically-Layered Multiplayer Bot System for a First-Person Shooter* (Killzone) — the canonical MP-arena bot behaviour/objective taxonomy the 2026-05-24 roster review checked against.
- [Utility system (Wikipedia)](https://en.wikipedia.org/wiki/Utility_system) — the normalized-factor × multiplier formulation matching `situationalFit × effectiveWeight`.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — planner + `Behaviour` contract this instantiates.
- [ADR-0014](./0014-capability-derived-bot-composition.md) — capability gate + derived weight (`hardGate` + part of `effectiveWeight`).
- [ADR-0015](./0015-arena-objective-and-roles.md) — objective + role bias (rest of `effectiveWeight`).
- [ADR-0012](./0012-bot-spatial-analysis-services.md) — scalar fields the input vocabulary reads from.
