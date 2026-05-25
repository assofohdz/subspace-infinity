# ADR 0014 — Capability-derived bot composition: archetypes as data-driven overlays

**Status:** Proposed — partially implemented
**Implementation:** `CapabilityProfile` × `BotSynergyTable` derivation landed in slices #04 (49a0dcd0 + 7f4ebeb3) and #05 (24db369f, runtime wiring); the `bots { tweak: [...] }` overlay landed in #01 (e9861a40). Objective × role multipliers (the four-multiplier composition) are pending #06.
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz

## Context

[ADR-0010](./0010-bot-composition-dsl.md) decided that a bot's tactical identity is an *archetype* = a `Map<String, Double>` weight vector over named `Behaviour`s ([ADR-0013](./0013-bot-tactical-goal-layer.md)), authored per-arena in `arena.groovy` via `bots { ship 'X', archetype: 'Y', weights: [...] }`. Operators write the weight vector for each archetype name from scratch; the `BrainRegistry` is the catalog of names.

The bot-AI v2 PRD ([.scratch/bot-ai-v2/PRD.md](../../.scratch/bot-ai-v2/PRD.md), §"Archetype-design step-back") flagged a problem with that path: slices #04 / #06 / #07 / #08 each invent a new archetype (`ChokeCamper`, `LeviathanSetup`, `MinerShark`, `JavelinBouncer`) as a side-effect of demoing a spatial service or behaviour. That is the wrong direction — archetypes are the user-facing artifact (the answer to *what kind of bot is this?*); they warrant deliberate roster design, not emergence-from-infrastructure-slices. Until the roster design lands, those four slices are frozen at `needs-info`.

This ADR offers a different resolution to the step-back: **don't author archetypes by hand at all**. Each ship's `ShipConfig` already declares what the ship is mechanically capable of — speed, recharge, energy pool, which weapons it carries, which status capabilities (`cloak`, `stealth`, `xradar`, `antiwarp`), which inventory counts (`maxMines`, `maxBursts`, `maxRepels`, `maxDecoys`, `maxPortals`). A Shark with mines, no bombs, and cloak *is* a miner-defender; a Javelin with bouncing bombs *is* a bouncer; a Leviathan with L3 splash bombs and high tankiness *is* a splash-anchor. The archetype's weight vector can be **derived from the ship's effective `ShipConfig`** rather than authored. Operator authoring shifts from "write the whole weight map" to "tweak the derived vector for this arena's flavour."

This shape:

- **Unblocks the four frozen slices** by making "what archetype is this?" a deterministic function of ship config — no roster-design workstream required.
- **Keeps ADR-0013's `TacticalPlanner` + `Behaviour` + per-goal Execute Sequence shape unchanged.** Only the *source* of the weight vector changes; the planner doesn't care whether weights were authored or derived.
- **Preserves ADR-0010's parameterize-don't-fork thesis.** Still one `BrainArchetype` impl per BT topology; weight datasets are still data. The dataset just isn't authored from scratch anymore.

### What this ADR does not settle

- **The UtilityBrain-vs-BT layer choice** flagged in the design conversation. This ADR works under either: the merged effective weight vector is the input to whatever picks goals. If the project later replaces ADR-0013's planner+BT with a pure-utility brain, this ADR's derivation pipeline still feeds it. Forcing function for the layer choice is the first `DodgeProjectile`-class reactive primitive — see §"Open work."
- **Live capability re-derivation on prize state.** A `Multifire` prize raises `burstDamage`; a `Bombs+` prize raises `areaDamage`. Spec'd as "recomputed on capability-affecting changes" — the exact change-event subscription set is follow-up work, not part of this ADR.
- **Named identities** (`MinerShark` / `JavelinBouncer` / `LeviathanSetup`). v2.0 ships pure derivation, no named presets. Whether named presets land in v2.1 as Groovy tweak overlays is deferred to first-playtest feedback (decided in design Q4).

## Decision

**A bot's effective `Behaviour` weight vector is derived from its ship's `CapabilityProfile` (a normalized read of `ShipConfig`) crossed with a per-zone Groovy-tier `synergy { … }` table that declares which capability dimensions favour each behaviour. The arena.groovy `bots { … }` block becomes a per-ship tweak overlay (additive/multiplicative deltas), not a from-scratch weight authoring surface. `CapabilityProfile` and `ArenaCapabilityNorms` are immutable api-tier records; computation lives in `infinity-server/.../ai/capability/`; the planner ([ADR-0013](./0013-bot-tactical-goal-layer.md)) reads the merged result via the existing `ArchetypeConfig` slot.**

### Layer position

```
ShipConfig (per-ship template)            ConfigRegistry (per-arena)
    │                                              │
    └─→ CapabilityProfile (derived) ←──── ArenaCapabilityNorms (max-across-arena's-ships)
                  │
                  ├──── ⊗ synergy { } (Groovy zone-tier)  →  raw weights
                  │
                  ├──── ⊗ bots { tweak: [...] } (arena.groovy overlay)  →  effective weights
                  │
                  ▼
           ArchetypeConfig.behaviourWeights  (consumed unchanged by TacticalPlanner)
```

Everything below `ArchetypeConfig` — `TacticalPlanner.select()`, the `Behaviour.enumerate()`/`intrinsicScore()` contract, per-goal Execute Sequences, additive stickiness, BT integration — is unchanged from ADR-0013.

### `CapabilityProfile` shape

Immutable api-tier record under `infinity.ai.capability.*`; one per `(Ship, ArenaCapabilityNorms)` pair. Cached on the bot entity as a server-only component, replaced wholesale when the ship's effective config changes (prize state, live reload).

```java
public record CapabilityProfile(
    Ship ship,
    // Continuous dimensions, all normalized to [0,1] against the arena's norms.
    double mobility,           // speed.max × rotation.max × thrust.max blended
    double burstDamage,        // peak 1-sec DPS window across all weapons at min fireDelay
    double sustainedDamage,    // DPS over recharge cycle, clamped by recharge economy
    double areaDamage,         // Σ (radius² × count) across bombs+gravBombs+bursts+thors
    double tankiness,          // geometric mean of energy.max + recharge.max
    double rechargeEconomy,    // shots/sec ceiling at full recharge
    double rangeProfile,       // max(bullet.speed × aliveTime, bomb.speed × aliveTime)
    // Boolean gates — true iff the corresponding ShipConfig field is non-null AND the
    // relevant tri-state (*Status > 0) or count (max > 0) qualifies.
    boolean bulletBounce, boolean bombBounce,
    boolean stealth, boolean cloak, boolean xRadar, boolean antiwarp,
    boolean attachReceive,    // added 2026-05-25: gate for anchor / attach-to-anchor (ADR-0016 catalog
                              // cross-check). No ShipConfig source yet → placeholder-derives false; see
                              // docs/bot-ai/capability-derivation.md.
    // Raw inventory counts (NOT normalized — eligibility scorers read these directly).
    int maxMines, int maxRepels, int maxBursts, int maxDecoys,
    int maxPortals, int maxThors, int maxBricks) {}
```

Derivation formulas are documented in [`docs/bot-ai/capability-derivation.md`](../bot-ai/capability-derivation.md) (companion implementation doc, lands with slice #01 of the v2.1 derivation work). The formulas are tuning knobs themselves and live in `engine-bot-ai.groovy` per [ADR-0006](./0006-tuning-knobs-vs-magic-numbers.md) — operators can rebalance the weighted-blend coefficients (e.g. "rotation matters more than thrust for mobility") without recompiling.

### `ArenaCapabilityNorms` — arena-scoped, not zone-global

```java
public record ArenaCapabilityNorms(
    double maxSpeed, double maxRotation, double maxThrust,
    double maxEnergy, double maxRecharge,
    double maxBurstDpsRaw, double maxSustainedDpsRaw,
    double maxAreaDamageRaw, double maxRange,
    double maxRechargeEconomy) {

  public static ArenaCapabilityNorms fromConfigRegistry(ConfigRegistry r) { ... }
}
```

**Arena-scoped, not zone-scoped.** Subspace "zone" overloads with whole-server scope; the norms here are computed per arena from that arena's `ConfigRegistry.shipConfigs()`. The type is named `ArenaCapabilityNorms` (not the design-discussion-tentative `ZoneNorms`) to make the scope unambiguous in the Subspace/MOSS/Infinity terminology overlap (see [`subspace-moss-terminology` skill](../../.claude/skills/subspace-moss-terminology/SKILL.md)).

A Spider's `mobility = 1.0` in a low-speed arena; the same Spider in a high-speed arena where a Warbird out-runs it has `mobility < 1.0`. The norms object is owned by the per-arena `BotAiArenaContext` (per [ADR-0012](./0012-bot-spatial-analysis-services.md)) and recomputed on arena load + live `arena.groovy` reload.

### Engine vs zone Groovy tiers

Two distinct Groovy files separate engineer-tier from zone-tier tuning:

- **`engine-bot-ai.groovy`** — ships with the engine; ENGINEER-AUTHORED only. Contains the synergy table + capability-derivation blend coefficients + `BotRoleConfig` bias maps ([ADR-0015](./0015-arena-objective-and-roles.md)). Zone admins do not edit this file in normal operation; engine releases update it.
- **`zone-bot-ai.groovy`** — lives in zone config; ZONE-OPERATOR-AUTHORED. Contains `MIN_BEHAVIOUR_WEIGHT`, planner cadence + stickiness margin ([ADR-0013](./0013-bot-tactical-goal-layer.md)), dynamic field cadences ([ADR-0012](./0012-bot-spatial-analysis-services.md)), tile-supersampling toggle ([ADR-0011](./0011-bot-navigation-navmesh.md)). Per-zone performance and behaviour tuning lives here.

This mirrors Subspace's traditional engine-config vs zone-config separation. Operators have one obvious surface (`zone-bot-ai.groovy`) for tuning; the engine-tier file is treated as engine source code that happens to live in Groovy.

The v1 `bot-tuning.groovy` ([ADR-0009](./0009-bot-ai-architecture.md) §"Tuning via Config-Component Projection") retires when this split lands — its contents (per-arena `BotBrainConfig` knobs: perceptionRadius, aimConeDegrees, etc.) migrate into the existing per-arena settings pipeline (not into either of the new files; those are bot-AI-tactical knobs, not per-arena ship-tuning).

### Synergy table — engine-authored Groovy fragment

The table that says "which capability dimensions favour each behaviour" lives in `engine-bot-ai.groovy`. **Engineer-authored only.** Zone admins do not edit this file in normal operation; the per-arena tweak overlay (`bots { tweak: [...] }` in arena.groovy) is the operator-facing knob.

Why Groovy at all if not operator-edited? Two reasons: (a) live-reload during engine development without recompiling, (b) consistency with the rest of the settings pipeline ([ADR-0004](./0004-settings-pipeline.md)) — one mechanism for all engine + zone tunables. Treat `engine-bot-ai.groovy` as engine source code that happens to live in Groovy.

Authoring shape (in `engine-bot-ai.groovy`, edited by the engine team alongside new `Behaviour` impls):

```groovy
synergy {
    behaviour 'engage', {
        bonus { profile -> 0.3 * profile.sustainedDamage + 0.2 * profile.mobility }
    }
    behaviour 'bomb-bank-shot', {
        requires { profile -> profile.bombBounce }
        bonus    { profile -> 0.4 * profile.areaDamage + 0.3 * profile.rangeProfile }
    }
    behaviour 'mine-congestion-points', {
        requires { profile -> profile.maxMines > 0 }
        bonus    { profile -> 0.3 * profile.tankiness }
    }
    behaviour 'lurk-ambush', {
        requires { profile -> profile.cloak || profile.stealth }
        bonus    { profile -> profile.cloak && profile.stealth ? 0.4 : 0.2 }
    }
    behaviour 'scout', {
        requires { profile -> profile.xRadar }
        bonus    { profile -> 0.5 * profile.mobility }
    }
    behaviour 'strafe', {
        bonus { profile -> 0.4 * profile.mobility + 0.3 * profile.sustainedDamage }
    }
    behaviour 'evade', {
        bonus { profile -> 0.4 * profile.mobility + 0.3 * profile.rechargeEconomy }
    }
    behaviour 'wander', { bonus { _ -> 0.1 } }   // baseline floor for any ship
}
```

Loaded by a new `GroovyBotSynergyLoader` into `ConfigRegistry` as a `BotSynergyTable` typed record (per [ADR-0004](./0004-settings-pipeline.md) settings-pipeline pattern). The record is a `Map<String, SynergyRule>` where `SynergyRule` carries a precompiled `requires` predicate + `bonus` scoring closure. New `Behaviour` impls ship with their synergy entry in the same PR — adding a behaviour without its synergy line is incomplete (the planner would never select it).

The `requires` predicate gates eligibility (failing → weight = 0; behaviour skipped from enumeration entirely). The `bonus` closure produces the raw weight on `[0, 1+]`; values > 1 are allowed (a ship that exceeds the arena's average on multiple dims gets a synergy multiplier > 1, which is what we want — a Levi with full tankiness + full areaDamage should score `anchor` higher than 1.0, not be capped).

### Effective weight resolution

Per-bot effective weight for behaviour `B` resolves as:

```
raw(B)        = synergy[B].requires(profile) ? synergy[B].bonus(profile) : 0
overlay(B)    = arena.bots.tweak[B] ?? 1.0       (multiplicative; 1.0 = no change)
effective(B)  = raw(B) × overlay(B)
```

`effective(B) < MIN_BEHAVIOUR_WEIGHT` (default `0.05`, set in `zone-bot-ai.groovy` — it's a per-zone performance threshold, not engine-locked) → the behaviour is dropped from enumeration that planner tick to save the spatial-query cost. See §"Engine vs zone Groovy tiers" below. This replaces ADR-0013's "behaviours not named in the archetype contribute zero candidates" rule with "behaviours below threshold are skipped" (see ADR-0013 amendment §A).

### New `bots { … }` DSL — overlay, not authoring

```groovy
arena {
    map 'koth.lvl'
    teamSetup 'ffa-private-freqs'
    mechanic  'fill-up-x-teams', teams: 2

    // Optional. Absent → every bot's behaviour is pure-derived from its
    // CapabilityProfile × the zone synergy table. No fallback to "Brawler".
    bots {
        // Per-ship overlay. tweak: multiplicative deltas on derived weights.
        ship 'shark',   tweak: ['mine-congestion-points': 1.3, 'engage': 0.5]
        ship 'warbird', tweak: ['evade': 0.7]
        ship 'levi',    tweak: ['anchor': 1.2, 'wander': 0.3]
    }
}
```

What's gone vs. ADR-0010:

- **`archetype: 'Y'`** — there is no archetype name; the bot's behaviour is its derived vector. (Named tweak presets in `zone-bot-ai.groovy` may reintroduce labels in v2.1 if first-playtest feedback shows derived bots don't feel distinct enough; deferred per design Q4.)
- **`weights: [evadeThreshold: 0.4]`** (set-value semantics) — replaced by `tweak: [...]` (delta semantics on derived values).
- **`defaults 'Brawler', perceptionRadius: 35.0`** — non-behaviour per-arena knobs (perceptionRadius, aimConeDegrees, etc.) stay on `BotBrainConfig` exactly as today; they were never behaviour weights and shouldn't co-mingle with this pipeline.
- **The "default = Brawler" fallback semantics** — replaced by "default = derived." Pure derivation, no hard-coded fallback name.

### Wiring

Per [ADR-0002](./0002-config-component-projection.md) Config-Component Projection:

1. `GroovyBotSynergyLoader` parses `engine-bot-ai.groovy` → emits `BotSynergyTable` into `ConfigRegistry` (engine-scoped — same table for every arena in every zone unless explicitly overridden).
2. `GroovyArenaLoader` parses arena.groovy `bots { … }` block → emits `BotOverlayConfig(Map<Ship, Map<String, Double>>)` into the arena's `ConfigRegistry`.
3. `BotAiArenaContext` (per [ADR-0012](./0012-bot-spatial-analysis-services.md)) computes `ArenaCapabilityNorms.fromConfigRegistry` once at arena load.
4. `FillUpXTeams.spawnBot` (or its `BotSpawnerModule` successor) at spawn time:
   - Reads the bot's `ShipConfig`.
   - Derives `CapabilityProfile` against `ArenaCapabilityNorms`.
   - Applies `BotSynergyTable` → raw weights.
   - Applies `BotOverlayConfig` for the bot's ship type → effective weights.
   - Stamps `ArchetypeConfig(name=ship.name(), behaviourWeights=effective)` on the bot entity (ADR-0010 component, unchanged shape).
5. `BotBrainSystem.BrainContainer.addObject` reads `ArchetypeConfig`; planner consumes weights as today.
6. Live `arena.groovy` reload → `BotOverlayConfig` updated → `EntityContainer.updateObject` re-stamps `ArchetypeConfig` on each bot → brain re-wires (existing path).

All cross-cutting components (`ArchetypeConfig`, `BotBrainConfig`) are server-only — no wire-crossing serialization concerns.

### Composition observability

The four-multiplier composition (`derived × objective × role × overlay`) has a real cost: two unrelated 0.5 multipliers from different tiers produce 0.25× effective, which neither author intended. The mitigation is **observability**, not mechanism change — multiplicative semantics are essential for clean "hard mute" (any 0 → 0 effective, used by `ArenaObjective` to disable wander in CTF and similar).

`BotDebug` HUD surfaces each multiplier separately per bot per behaviour:

```
Bot 42 [Shark] in arena trench-koth-01
  Behaviour          derived × objective × role × overlay = effective
  mine-congestion    0.85    × 1.50      × 1.00 × 1.30    = 1.66    ← top
  engage             0.40    × 1.00      × 0.50 × 0.50    = 0.10    ← muted by role×overlay
  evade              0.60    × 1.00      × 1.00 × 1.00    = 0.60
  ...
```

Authoring conventions in engine docs: objective biases rarely drop below 0.7; role biases rarely below 0.5; if two tiers want to suppress the same behaviour, pick one to do the work. Documented in the operator guide; surfaced in the debug HUD so authors see exactly where weight disappeared.

### Live-reload semantics

| Event | Action |
|---|---|
| `EngineBotAiReloaded` | Synergy table re-loaded into `ConfigRegistry`; derivation coefficients re-loaded. No per-bot re-stamping — planner reads new synergy on next tick. |
| `ZoneBotAiReloaded` | Re-read `MIN_FRACTION` (per ADR-0013) and any zone-tier knobs; no per-bot work. |
| `ArenaGroovyReloaded` | Re-parse `bots { ... tweak: [...] }`; emit `BotOverlayConfig` updates; `EntityContainer.updateObject` re-stamps `ArchetypeConfig` on each bot. |
| `ShipConfigReloaded` | Re-derive `CapabilityProfile` for any bot whose `ShipConfig` changed; re-stamp. |
| `ArenaCapabilityNormsChanged` (rare; arena ConfigRegistry restructured) | Re-derive `ArenaCapabilityNorms` from current ship configs; re-derive all `CapabilityProfile`s in the arena; re-stamp. |

## Consequences

### Positive

- **PRD step-back resolved.** Slices #04/06/07/08 unblock. Sharks with mines mine; Javelins with bouncing bombs bounce; Levis with splash anchor — without authoring four `ArchetypeConfig` records.
- **New ships are free.** Adding a ninth ship type with novel stats (a stealth-bomber prototype, say) requires no archetype work — its behaviour falls out of its `ShipConfig`. Forward expansion path is "edit ShipConfig," not "ship a new archetype + register + author weights."
- **Roster discoverability is automatic.** Operator question "what does Shark do in this arena?" answers via `BotDebug` HUD surfacing the derived `CapabilityProfile` + effective weights. No separate archetype catalog to maintain.
- **CCP unification holds.** Bot behaviour now flows through the same template → component projection as ship stats; one mental model. ADR-0002 substrate carries it.
- **Tunability without authoring.** Operator tweaks the per-arena `bots { tweak: ... }` overlay (small) and the zone-wide `synergy { }` table (one-time per zone). The combinatorial "8 ships × N archetypes" matrix the previous design implied collapses to one synergy table + per-ship overlay.

### Costs

- **Synergy is engineer-authored Groovy.** The table uses closures (`bonus { profile -> ... }`); authors must understand `CapabilityProfile`'s field set + how its dimensions interact. This is engine-team work, not operator work — the operator-facing surface is the per-arena `bots { tweak: [...] }` overlay (just numbers). The "synergy is operator-tunable" framing in the original v0 of this ADR was wrong; the closure-based authoring is firmly engineer-tier and that's intentional.
- **The closure-formula complexity ceiling is real.** When a synergy formula grows past 3-4 terms, the right answer is to **add a new dimension to `CapabilityProfile`** (which the formula can then read), not to grow the closure. Reviewable in the same PR; treat formula bloat as a design smell.
- **Runtime failures on field-name typos.** A closure referencing `profile.areaDamge` (typo) compiles fine in Groovy and fails at first-bot-spawn after arena load. Mitigation: a load-time validation pass that runs each closure against a sentinel `CapabilityProfile` and flags failures before any bot ticks. Lands with the synergy loader's impl slice.
- **Behaviour authoring spans three files.** A new `Behaviour` impl (Java) requires the registry entry (Java) + the synergy table entry (Groovy) + possibly a new spatial field ([ADR-0012](./0012-bot-spatial-analysis-services.md)). Modest per-behaviour PR width; the cost is honest, not hidden.

### Performance budget (estimates; profile-validated per slice)

- **CapabilityProfile derive cost:** ~10-20 multiplications + array lookups per ship; computed once per bot at spawn + on relevant reload events. Negligible.
- **Memory per bot:** `CapabilityProfile` is ~13 doubles + ~6 booleans + ~7 ints = ~150 bytes per bot. ~1 KB per arena at 8 bots.
- **`ArenaCapabilityNorms`:** ~10 doubles per arena, computed at arena load. Negligible.
- **Synergy table evaluation:** 14 behaviours × `(requires + bonus)` closures per bot per planner tick (or per-spawn if cached). Closure call overhead in JVM ~50ns each; ~700ns per bot per evaluation. Negligible.

Numbers are seeds; first impl slice measures actuals.
- **Derivation cost is non-trivial on arena load.** 8 ships × N behaviours × (closure eval) at arena init. Cost is one-time per arena load + once per `Behaviour` registration / `arena.groovy` reload — not on the per-tick hot path. Acceptable.
- **Loses some explicit-author control.** A designer who wants a Warbird to behave like a defensive Brawler can't do it by just naming `archetype: 'Brawler'`; they have to author `tweak: [...]` deltas. Slightly more verbose for the "make this ship behave like that ship" case. Mitigation: v2.1 can reintroduce **named tweak presets** in `zone-bot-ai.groovy` if this becomes a real workflow friction (per Q4).

### Neutral

- **Forward-compat for v2 multi-archetype planners.** The ADR-0010 reservation about round-driven composition planning still applies — the planner emits the same `ArchetypeConfig` stamps; this ADR only changes how the stamp's weights are produced.
- **External-author behaviours.** A module author shipping a new `Behaviour` adds it to the registry AND adds a `synergy { behaviour 'foo', ... }` entry. The two-step authoring is intentional — declaring a behaviour without saying which ships it favours is incomplete.
- **The `BrainRegistry` of archetype names persists** for backward compatibility (`CombatantBrain` still registers as "Brawler" — useful for tests). But the registry is no longer the catalog operators consult; it's an implementation detail of the BT-topology dispatcher.

## Alternatives considered

### A. Keep ADR-0010 as authored, run a roster-design workstream (the PRD's recommendation)

**Why considered:** The PRD already reserved this as the recommended form. Familiar shape — each archetype is a hand-tuned weight vector + named identity + per-arena slot.
**Why rejected:** Combinatorial authoring burden. v2.0 ships 8 ship types and ≥6 named archetypes; that's 48 cells to fill in the (ship × archetype) matrix, plus per-arena tuning on top. Derivation collapses 48 cells to 8 derivation reads + 1 synergy table. Authored weights also drift: a `MinerShark` weights vector authored when bombs did X damage stays wrong after a bomb rebalance until someone re-tunes it. Derived weights track the ship config automatically.

### B. Synergy table in Java (static map next to `BehaviourRegistry`)

**Why considered:** Fully debuggable in one file; type-checked; no Groovy parsing cost. Was the first proposal in design Q2.
**Why rejected:** Operator iteration speed. Synergy formulas are the kind of tuning that benefits from iterate-without-rebuild — exactly what the ADR-0004 settings pipeline exists for. Operator picked Groovy in design Q2; cost noted (closure-formula complexity ceiling) but lived with.

### C. Per-`Behaviour` synergy method (each `Behaviour` impl overrides `synergyBonus(CapabilityProfile)`)

**Why considered:** Most OO; each behaviour owns its synergy policy; no central table.
**Why rejected:** Spreads policy across many files — `bot-ai-v2/PRD.md` already calls out 8+ planned behaviours; whole-table view from one file (the Groovy fragment) is more valuable than per-class encapsulation for the operator audience. Java-tier (Alt B) shares the same per-file argument; the Groovy choice in Q2 covers it.

### D. Per-arena derivation formula authoring (full closures in arena.groovy, no central synergy table)

**Why considered:** Maximally flexible — each arena can override the synergy table for its specific flavour.
**Why rejected:** Loses the operator-tweak / authored-derivation split. Arena-tier becomes the only knob, which means every arena re-authors the same synergy logic. The zone-tier table establishes "this is what `mine-congestion-points` wants in a ship" once; per-arena tweaks adjust the *result*, not the rule. Two clean tiers > one over-flexible tier.

### E. Pure ML-driven derivation (learn the weights from match data)

**Why considered:** Industry trend; would adapt to whatever players consider competitive.
**Why rejected:** Out of v2/v3 scope per ADR-0013's stated stance against online learning. May revisit once derivation + manual tuning are stable + have enough match telemetry to learn from. v3+ at earliest.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Weight source | **Derivation from `CapabilityProfile` × zone-tier `synergy { }` table × per-arena `tweak: [...]` overlay.** Authored weight vectors per archetype name are gone. |
| `CapabilityProfile` location | `api/infinity.ai.capability.*` — immutable record. Computation in `infinity-server/.../ai/capability/`. |
| `ArenaCapabilityNorms` scope | **Per arena, not zone-global.** Named for scope clarity (avoids Subspace "zone" overload). Lives on `BotAiArenaContext` (ADR-0012). |
| Synergy table tier | **Engine-authored Groovy** (`engine-bot-ai.groovy` → `BotSynergyTable` typed record). `requires` predicate + `bonus` closure per behaviour. Operator-facing surface is the per-arena `tweak: [...]` overlay only. |
| Effective weight formula | `effective(B) = (requires(profile) ? bonus(profile) : 0) × overlay(B)`. Threshold `MIN_BEHAVIOUR_WEIGHT = 0.05` skips enumeration entirely (replaces ADR-0013's "not-named-by-archetype = 0 candidates"). |
| Arena DSL shape | `bots { ship 'X', tweak: [behaviour: factor, ...] }`. No `archetype:`, no `weights:`, no `defaults`. |
| Default fallback | **Pure derivation.** Absent `bots { }` block ≠ Brawler fallback; the bot's behaviour is its derived vector. |
| Named identities | **None in v2.0.** Deferred per design Q4 — revisit in v2.1 if first-playtest feedback shows derived bots lack distinct identity. |
| Integration with ADR-0013 | Unchanged below `ArchetypeConfig`. Planner reads merged weights as today; behaviour enumeration changes only the eligibility rule (threshold-based vs name-listed). |
| Layer choice (utility brain vs BT) | **Still deferred.** This ADR feeds either; layer choice is forced by the first reactive-primitive impl (Dodge / BreakLOS). |

## Open work

- **`docs/bot-ai/capability-derivation.md` companion doc.** The derivation formulas (`mobility = 0.5·norm(speed) + 0.3·norm(rotation) + 0.2·norm(thrust)`, etc.) live there as the implementation reference. Lands with the first derivation slice.
- **`engine-bot-ai.groovy` for derivation coefficients.** Per [ADR-0006](./0006-tuning-knobs-vs-magic-numbers.md), the blend coefficients in the formulas (`0.5` / `0.3` / `0.2` for `mobility`) are tuning knobs and must live in Groovy, not in Java. New typed `BotDerivationConfig` record + Groovy fragment + loader.
- **ADR-0010 amendment.** Add a "Superseded in part by ADR-0014" section noting (a) the weight-authoring stance is replaced by capability derivation, (b) the parameterize-don't-fork thesis stays intact, (c) `BotBrainConfig` non-behaviour knobs (perceptionRadius, aimConeDegrees) are unaffected.
- **ADR-0013 amendment.** Adjust the "behaviours not named in the archetype contribute zero candidates" wording to "behaviours below `MIN_BEHAVIOUR_WEIGHT` skip enumeration." Single-paragraph change.
- **Slice re-scoping.** PRD slices #04/06/07/08 unblock — the deferred archetype-design workstream is replaced by this ADR. PRD §"Archetype-design step-back" gets a "resolved by ADR-0014" note pointing here. The four slices re-enter the queue with their original intent (each demos one spatial service or weapon-specific behaviour); they no longer invent named archetypes.
- **First slice for v2.1 derivation work.** Recommended shape: implement `CapabilityProfile` + `ArenaCapabilityNorms` + a stub `BotSynergyTable` (3-4 baseline behaviours: engage / evade / wander / strafe) end-to-end — proves the derivation pipeline with the existing Brawler-equivalent behaviours before adding spatial-service-dependent behaviours. Vertical tracer, same as ADR-0008 prefers for arena modules.
- **Live capability re-derivation event set.** Which mutation events should re-stamp `CapabilityProfile`? Candidates: ship-swap, prize-pickup that changes a `Status*` or `*Max` field, `arena.groovy` reload. The event subscription set lands with the first prize that meaningfully changes a derived dimension.
- **Layer choice forcing function.** When the first reactive primitive (`DodgeProjectile`) lands, measure utility-scoring it per-tick vs BT-leaf cost. Decision recorded in a new ADR (0015?) at that point.
- **`BotDebug` HUD extension.** Surface the bot's derived `CapabilityProfile` (the seven dim values + gate flags) and the effective behaviour weight vector. Makes "why is this Shark not mining?" answerable without log-grepping.

## References

### Internal

- [ADR-0001](./0001-ecs-component-model.md) — Component model. `CapabilityProfile` is a server-only component; immutable; replaced wholesale per canonical-writer rule (the spawn system / `BotAiArenaContext` is the writer).
- [ADR-0002](./0002-config-component-projection.md) — CCP. `ShipConfig` → `CapabilityProfile` follows the template → derived-component path.
- [ADR-0004](./0004-settings-pipeline.md) — Settings pipeline. New `BotSynergyTable` + `BotOverlayConfig` flow through the same `GroovyArenaLoader` / `ConfigRegistry` substrate.
- [ADR-0005](./0005-layered-architecture.md) — api purity. `CapabilityProfile` + `ArenaCapabilityNorms` + `BotSynergyTable` records are api-tier; derivation logic + loaders are server-tier.
- [ADR-0006](./0006-tuning-knobs-vs-magic-numbers.md) — Tuning knobs in Groovy. Derivation blend coefficients go to `engine-bot-ai.groovy`.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI substrate. `BrainArchetype` + `Blackboard` shape is preserved.
- [ADR-0010](./0010-bot-composition-dsl.md) — Per-arena composition DSL. **Superseded in part by this ADR** — weight authoring → capability derivation + tweak overlay.
- [ADR-0012](./0012-bot-spatial-analysis-services.md) — `BotAiArenaContext`. New home for `ArenaCapabilityNorms` alongside `TrafficHeatmap` / `ChokepointAnalyzer`.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — Tactical-goal layer. **Amended in §"Effective weight resolution"** — eligibility rule changes from "name in archetype map" to "effective weight ≥ threshold." Everything else (planner cadence, additive stickiness, `IsGoal` BT dispatch, per-goal Execute Sequences) is unchanged.
- [`config-pattern.md`](../../.claude/rules/config-pattern.md) — CCP rule.
- [`settings-pipeline.md`](../../.claude/rules/settings-pipeline.md) — settings pipeline rule.
- [`subspace-moss-terminology` skill](../../.claude/skills/subspace-moss-terminology/SKILL.md) — naming "ArenaCapabilityNorms" (not `ZoneNorms`) to avoid the Subspace zone-vs-arena overload.
- [.scratch/bot-ai-v2/PRD.md](../../.scratch/bot-ai-v2/PRD.md) — §"Archetype-design step-back" — the open question this ADR closes.

### External

- **The Sims** (Maxis, 2000+). "Smart objects advertise utility scores; agent picks max." This ADR keeps that shape ([ADR-0013](./0013-bot-tactical-goal-layer.md) consumes it); only changes the source of the weights.
- **Damian Isla, "Handling Complexity in the Halo 2 AI"** (GDC 2005). "Same engine, different character data" — this ADR pushes the character-data derivation upstream from authored to capability-derived. Same composition shape; different data pipeline.
- **David Mark, *Behavioral Mathematics for Game AI*** (2009). Utility scoring + response curves substrate. Synergy formulas in this ADR are response-curve-shaped (linear blends on normalized inputs).
- **Civilization series leader personalities** (long-running production reference). Each civ leader is a hand-tuned weight vector on a shared engine — the *non*-derivation reference point. This ADR proposes the opposite stance for bot ships: derive from the unit's mechanical capabilities instead of authoring per-leader.
