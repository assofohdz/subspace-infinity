# ADR 0010 — Bot composition DSL: per-arena archetype × ship-type × weights

**Status:** Proposed — partially implemented
**Implementation:** Slice #01 (e9861a40, 2026-05-25) landed the `bots { ship 'x', count: N, tweak: [...] }` block in the ADR-0014-amended overlay form (`tweak` deltas, no from-scratch `weights`/`archetype`). Non-behaviour `BotBrainConfig` knobs remain as described.
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz
**Amended by:** [ADR-0014](./0014-capability-derived-bot-composition.md) (2026-05-23) — weight authoring superseded by capability derivation; `tweak: [...]` overlay replaces `weights: [...]` set-value semantics. The parameterize-don't-fork thesis + `BrainArchetype` registry + non-behaviour `BotBrainConfig` knobs (perceptionRadius, aimConeDegrees, etc.) are unaffected.

## Context

[ADR-0009](./0009-bot-ai-architecture.md) established a single Behaviour Tree engine driving `BotShip` entities through one `BrainArchetype` (today: `CombatantBrain` / "Brawler"). Tuning knobs that the brain reads — perception radius, weapon range, orbit radius, low-energy threshold, lead-time, oversteer floor — currently live as Java constants in `CombatantBrain` and `BotBrainSystem`. There is no first-class arena-Groovy way for an arena author to express:

1. **Which archetype runs on which ship type.** A Shark in KOTH should miner-defend; a Shark in a duel-style arena should aggressively engage. Today both Sharks share the one hard-coded brain.
2. **Per-archetype tuning.** A "Duelist" Warbird and a "Brawler" Warbird differ in weapon range, engagement aggression, evasion threshold — not in BT *shape* but in BT *weights*. Today these are compile-time constants.
3. **Mix per freq / per team.** "2 Brawlers + 1 Defender on each KOTH team" or "all Duelist Warbirds in a Trench arena" — not expressible without writing a new `MechanicModule`.

The adr-backlog entry "Bot composition DSL in arena.groovy" (2026-05-23) reserved this decision space. The slice queue closes v1 at slice #08 (Groovy CCP + scaling), which is the natural pairing — once `BotBrainConfig` becomes a typed record loaded from Groovy, the archetype + ship selectors flow through the same pipeline.

The cognitive-AI literature on this problem is consistent (see *References*): **don't fork the brain per role; parameterize one brain.** Halo 2's BT + per-encounter "character" parameters, F.E.A.R.'s GOAP with weighted goal priorities, Civilization's leader personalities as weight vectors on a shared engine, The Sims' trait-stacking — all converge on the same shape: one engine, N datasets. Forking creates a maintenance multiplier (every new feature lands in N copies); parameterization compresses to one engine + N config files.

### What this ADR does not settle

- **Multi-archetype team composition algorithms** (the planner that picks "2 Brawlers + 1 Defender" from a pool given team composition / round state). v1 is per-slot-static; round-driven composition planners are a slice-#10+ concern.
- **Online archetype switching** (a Brawler that becomes a Defender mid-round when the team's flag is at risk). Same deferral — out of scope until the static case is solid.
- **External-author archetype contribution.** The DSL surfaces a registry of *built-in* archetypes for v1. Module authors writing custom archetypes is a v2 question.

## Decision

**Per-arena `arena.groovy` declares a `bots { }` block that maps ship type → archetype name with optional per-instance weight overrides. Archetypes are typed `BotBrainConfig` records (per [ADR-0002](./0002-config-component-projection.md) CCP) registered against `BrainRegistry` at server start. A new `BotBrainConfig` ECS component is stamped on each bot at spawn time; `BotBrainSystem` reads it to pick the archetype + apply weights. There is no archetype-per-class explosion — one parameterized `BrainArchetype` impl per *behaviour pattern* (Brawler, Duelist, Defender, …), per-arena overrides as data.**

### The DSL shape

```groovy
arena {
    map 'koth.lvl'
    teamSetup      'ffa-private-freqs'
    mechanic       'fill-up-x-teams', teams: 2

    // NEW: per-arena bot composition. Optional — if absent, fill-up-x-teams falls
    // back to its current single-archetype default ("Brawler" / Javelin / 1 per freq).
    bots {
        // Per-ship-type → archetype mapping. ShipType is the discriminator; the
        // archetype is looked up by name from BrainRegistry.
        ship 'javelin',  archetype: 'Brawler'
        ship 'warbird',  archetype: 'Duelist',  weights: [evadeThreshold: 0.4, orbitRadius: 8.0]
        ship 'shark',    archetype: 'MinerDefender'

        // Optional: per-archetype defaults that apply across all ship types using it.
        // Useful when you want one set of knobs for "every Brawler in this arena."
        defaults 'Brawler', perceptionRadius: 35.0, weaponRange: 18.0
    }
}
```

Equivalent typed records on the api side:

```java
public record BotsConfig(List<BotShipConfig> ships, Map<String, Map<String, Double>> defaults) {}
public record BotShipConfig(String shipType, String archetype, Map<String, Double> weights) {}
```

Loaded by `GroovyArenaLoader` into `ConfigRegistry` alongside `ShipConfig` / `RandomRadiusConfig` / etc.

### Two-tier weight resolution

Per-bot effective weights merge in this order (later wins):

1. **Archetype defaults** — compile-time constants on the `BrainArchetype` impl.
2. **Per-arena per-archetype defaults** — the `defaults 'Brawler', ...` line above.
3. **Per-ship-type overrides** — the `ship 'warbird', weights: [...]` map.

This mirrors the layering already used by `ShipConfig` (preset → arena → ship-type). The same precedence convention everywhere reduces "where does this knob come from?" cognitive load.

### Wiring: typed config → ECS component → brain

Per ADR-0002 Config-Component Projection:

1. `GroovyArenaLoader` parses `arena.groovy` → emits `BotsConfig` records into `ConfigRegistry`.
2. `FillUpXTeams.spawnBot` (or its successor `BotSpawnerModule`) reads the arena's `BotsConfig`, resolves `(shipType, archetype, weights)` for the chosen ship, stamps a new `BotBrainConfig` ECS component on the bot entity carrying the archetype name + merged weights.
3. `BotBrainSystem.BrainContainer.addObject` reads the bot's `BotBrainConfig`, looks up the archetype in `BrainRegistry`, calls `archetype.createBlackboard(config.weights())`. The brain reads weights from its blackboard.
4. Weight changes (live `arena.groovy` reload) re-stamp `BotBrainConfig`; the `EntityContainer.updateObject` hook re-wires the bot's brain in place (canonical "watched component changed" path per [`entity-containers.md`](../../.claude/rules/entity-containers.md)).

`BotBrainConfig` is server-only — clients don't need to see archetype assignments. No wire-crossing registration required (per [`components.md`](../../.claude/rules/components.md)).

### Archetype registry growth shape

v1 ships one archetype (`CombatantBrain` = "Brawler") with all weights compile-time. v1.x grows the registry to ~4-6 named archetypes by extracting the BT-shape variants the gameplay actually wants. Each new archetype is a `BrainArchetype` impl + a name registered at server start in `BotBrainSystem.initialize()`:

```java
brainRegistry.register(new CombatantBrain());     // "Brawler"
brainRegistry.register(new DuelistBrain());       // weight overrides on the same BT shape
brainRegistry.register(new MinerDefenderBrain()); // different BT (Defend > Wander > Pursue)
brainRegistry.register(new FlagCarrierBrain());   // CTF / KOTH role
```

Archetypes that differ in *weights only* (e.g. Brawler vs Duelist) should share a `BrainArchetype` impl + differ via the weight datasets — this is the *parameterize-don't-fork* literature lesson. Archetypes that differ in *BT shape* (e.g. MinerDefender prioritizes Defend over Pursue) get their own impl. The boundary is "does the tree topology change?"

### Default fallback semantics

If `bots { }` is absent or doesn't cover the spawned ship type, `FillUpXTeams` uses the documented v1 default: archetype = `"Brawler"`, ship = `JAVELIN`, weights = archetype defaults. The intent is that adding `bots { }` is purely additive — existing arenas that don't declare one keep working unchanged.

## Consequences

### Positive

- **Same brain, many roles.** "Shark-as-miner-defender" vs "Shark-as-duelist" without forking the brain. Literature-validated path.
- **Arena authors get composition control without Java edits.** Mirrors the experience [ADR-0008](./0008-arena-composition-and-modules.md) gave them for mechanics.
- **CCP unification.** Bot tuning flows through the same pipeline as ship stats / weapon stats / spawn placement — one mental model.
- **Live-reload survives.** `EntityContainer.updateObject` rewires brains on `BotBrainConfig` change; arena authors iterate weights without server restart.
- **Test substrate.** Per-archetype weight datasets are pure data — no behaviour change, just parametric testing of "does Brawler-with-aggressive-weights actually engage sooner?"

### Costs

- **One more typed record + ECS component + Groovy parse path.** Modest implementation cost; the shape is established by ADR-0002 + ADR-0004 + ADR-0008, so it's mostly mechanical.
- **The registry has to ship with archetype names + their valid weight keys.** Otherwise typos in `weights: [evadeThreshhold: ...]` silently fall through to defaults. Mitigation: archetype defines its weight keys; loader validates + warns. Strict per ADR-0004 settings-pipeline ethos.
- **The "default archetype" knob is now arena-dependent.** Behaviour observers ("the bot is too aggressive in arena X") need to check three places (archetype defaults / arena defaults / per-ship overrides) instead of one (hard-coded constant). Surfaces via `BotDebug` HUD by including the resolved archetype name.

### Neutral

- **Forward-compat for v2 multi-archetype planners.** When round-driven composition planning lands (per "What this ADR does not settle"), it slots in *above* the DSL — the planner emits the same `BotBrainConfig` stamps the DSL would. No re-architecture.
- **External-author archetypes.** Same `BrainArchetype` interface module authors would use; api/-tier already per ADR-0005 layering. v2 unlocks community archetype contributions.

## Alternatives considered

### A. Fork the brain per role (one Java class per archetype + ship combo)

**Why considered:** Simplest code change — just add `SharkDuelistBrain extends CombatantBrain { override weights }` and register it.
**Why rejected:** The maintenance-multiplier failure mode the literature warns against. Every new BT feature lands in N copies; weights diverge by accident; the registry grows to N×M (N archetypes × M ship types) instead of N+M. Compile-time-only — operators can't iterate without rebuilds.

### B. Single global `botBrain.groovy` config file (no per-arena scope)

**Why considered:** Smaller surface area — one file, no `bots { }` block per arena.
**Why rejected:** Defeats the per-arena variance the user explicitly named ("Shark as defender vs duelist depends on arena"). Per-arena scope is the whole reason to do this work; collapsing to global throws it away.

### C. Per-arena `botBrain.groovy` fragment (no inline `bots { }` block)

**Why considered:** Matches the existing per-arena fragment shape (`ship-warbird.groovy`, `bomb.groovy`, etc.) — operators already know that pattern.
**Why rejected:** Bot composition is small enough (per-arena: a handful of ship-type → archetype lines) that inlining in `arena.groovy` is more readable than a separate fragment. Composition-with-ship-types is also an *arena-level* concern (it interacts with `roster`, `teamSetup`, `mechanic 'fill-up-x-teams'`); separating it into a fragment fragments the arena's mental model. The bigger per-ship-stat fragment pattern is justified by file size; bot composition isn't.

### D. ECS-only (no Groovy DSL): `BotBrainConfig` stamped by a Java mechanic module

**Why considered:** Maximally typed. No Groovy parsing.
**Why rejected:** Loses the operator-iteration win. Bot composition is exactly the kind of arena-tuning-iteration knob operators want to flip without recompiling; that's the entire purpose of ADR-0004's settings pipeline. Java-only re-creates the "must rebuild to test" friction the Groovy tier exists to eliminate.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Where does per-arena bot mix live? | `bots { }` block in `arena.groovy`. Inline, not a separate fragment. |
| What's the typed config shape? | `BotsConfig(List<BotShipConfig>, Map<String, Map<String, Double>>)`. Per-ship + per-archetype-default maps. |
| How does it reach the bot? | CCP per ADR-0002: Groovy → `ConfigRegistry` → `BotBrainConfig` ECS component at spawn → read by `BotBrainSystem.BrainContainer.addObject`. |
| What's the weight precedence? | archetype defaults → arena defaults → per-ship overrides (later wins, mirrors ShipConfig). |
| Fork-vs-parameterize? | **Parameterize**. Same `BrainArchetype` impl + weight dataset for variants that share BT shape. New impl only when BT topology differs. |
| Default if no `bots { }`? | `"Brawler"` / Javelin / 1 per freq — existing v1 behaviour, unchanged. |

## Open work

- **Implementation pairing with slice #08** of the bot-AI v1 PRD (Groovy CCP + scaling + arch test). The `BotBrainConfig` typed record + Groovy loader + `bots { }` block parser land together. Slice #08 is the natural seam.
- **Archetype registry growth.** Slice #07+ adds Flocking; slice #08+ adds Defender / Duelist as the second + third archetypes. Each archetype gets a brief ADR comment about which BT shape it diverges from + why.
- **Weight-key validation.** Loader emits a warning when `weights: [foo: ...]` names a key the archetype doesn't recognise. Same ethos as the typed settings-pipeline strict-validation pass.
- **`BotDebug` HUD extension.** Include the resolved archetype name in the wire-crossing snapshot so the debug panel shows `Bot 42 [Duelist] Engage tgt=7 @12h` — surfaces the three-tier weight resolution to live observers.
- **Migration of the existing hard-coded constants** in `CombatantBrain` → archetype defaults visible via the typed config layer. No behaviour change; pure refactor; lands as part of slice #08.

## References

### Internal

- [ADR-0001](./0001-ecs-component-model.md) — Continuous + Stats split; canonical writer; Change-entity mutation. `BotBrainConfig` follows the immutable-component contract.
- [ADR-0002](./0002-config-component-projection.md) — Config-Component Projection. The DSL → typed record → ECS component path is the canonical CCP shape.
- [ADR-0004](./0004-settings-pipeline.md) — Settings pipeline + `GroovyArenaLoader`. Same loader, new typed record.
- [ADR-0005](./0005-layered-architecture.md) — api/ data + interfaces only; `BrainArchetype` is the api-tier interface.
- [ADR-0008](./0008-arena-composition-and-modules.md) — arena.groovy as pure data over a fixed Java module catalog. The `bots { }` block follows the same shape as `mechanic` / `scoring` / `roundStructure`.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI architecture. This ADR extends ADR-0009's `BrainRegistry` + `BrainArchetype` mechanism with arena-scoped composition.
- [`config-pattern.md`](../../.claude/rules/config-pattern.md) — CCP rule. New `BotBrainConfig` lives on the template tier; brain reads from its blackboard projection.
- [`entity-containers.md`](../../.claude/rules/entity-containers.md) — `EntityContainer.updateObject` hook for live-reload rewiring.

### External

- **Damian Isla, "Handling Complexity in the Halo 2 AI"** (GDC 2005). The canonical BT + per-encounter character paper. Same tree, parametric character data — the architectural template this ADR adopts. ([overview](https://gdcvault.com/play/1014752/) — slides typically circulated separately.)
- **Jeff Orkin, "Three States and a Plan: The AI of F.E.A.R."** (GDC 2006). GOAP goals with weighted priorities per agent instance. Demonstrates "same engine, different weight vector = different behaviour" at scale across squad AI.
- **Mat Buckland, *Programming Game AI by Example*** (2005). Chapter on individual vs squad behaviour layering reinforces the parameterize-don't-fork pattern at textbook level.
- **Civilization series leader personalities** — long-running production example of leader-as-weight-vector on a shared evaluation engine. Each civ leader is data; the AI brain is one engine.
- **Reynolds 1999, "Steering Behaviors for Autonomous Characters"** (https://www.red3d.com/cwr/steer/). The steering substrate this ADR layers on. Explicitly modular: same primitives, different weighted-sum or priority composites = different agents.
