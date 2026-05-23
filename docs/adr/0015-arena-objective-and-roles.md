# ADR 0015 — Arena objectives: per-mechanic behaviour bias + per-bot role assignment

**Status:** Proposed
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz

## Context

[ADR-0014](./0014-capability-derived-bot-composition.md) lets a bot's behaviour weight vector fall out of its ship's `CapabilityProfile`. That answers *"what is this ship mechanically good at?"* — a Shark with mines and cloak derives high weight on `mine-congestion-points` and `lurk-ambush`; a Levi with splash bombs derives high weight on `anchor` and `bomb-bank-shot`.

It does not answer *"what is this arena trying to win at, and how should this ship contribute?"*

Concrete cases the capability-derivation pipeline cannot express:

- **Hockey.** A Warbird with no weapons is in a sports vehicle; the objective is "carry the ball to the goal." `CapabilityProfile` says "this ship has no DPS"; no derived weight produces "go grab the ball."
- **CTF.** A Shark with mines should *defend the team's flag and capture the enemy flag*; "mine the most congested tile" is the wrong behaviour when the team's flag is being carried out by an enemy.
- **KOTH.** Every ship should bias toward "control the central tile" — regardless of capability. A Levi with no `anchor`-relevant weights still needs to plant itself on the king tile.
- **Turf / Conquest.** Behaviour shifts based on which control regions are held; a fully-derived bot can't read map state.
- **Powerball.** Similar to Hockey — possession-based; capability-derivation has no concept of "I am holding the ball; my behaviour changes."

[ADR-0013](./0013-bot-tactical-goal-layer.md) reserves the planner shape but consumes only one weight source (capability-derived per ADR-0014). [ADR-0008](./0008-arena-composition-and-modules.md) defines mechanic modules as the arena-composition unit (`KothMechanic`, `CtfMechanic`, etc.) but doesn't currently expose anything to the AI layer.

This ADR closes the gap with two concepts: **`ArenaObjective`** (per-mechanic behaviour bias produced by the mechanic module) and **`BotRole`** (per-bot tag that further biases behaviour, assigned by the objective at round start).

### What this ADR does not settle

- **Multi-objective arenas.** A single mechanic module produces one objective at a time. Composing two mechanics' objectives (e.g. KOTH + scoring-by-kills) is reserved for a future composition operator; v2.0 single-objective per arena.
- **Role-switching mid-round.** Roles are assigned at round start (or arena join) and held until next round. Dynamic mid-round role rotation ("the flag carrier dropped it, you're the new attacker") is v2.x.
- **Objective state synchronization to client.** The objective + role are server-only concepts. Client visualisation of "what role is this bot in" is a v2.x debug-HUD affordance via wire-crossing component.
- **AI for non-bot players.** Objectives drive *bot* behaviour. Human players see the same map state and figure it out themselves.

## Decision

**Each `ArenaModule` may produce an `ArenaObjective` (sealed interface with per-gametype record subtypes — `DeathmatchObjective`, `KothObjective`, `CtfObjective`, `TurfObjective`, `PowerballObjective`) that registers with the per-arena `BotAiArenaContext`. The objective declares (a) a `behaviourBias` map of multiplicative weight modifiers applied on top of capability-derived weights, (b) a set of static goal tiles (flag spawns, KOTH center, etc.) the navigation layer registers as `DistanceField` goals, and (c) a per-bot `BotRole` assignment hook called at round start. `BotRole` is a per-bot ECS component carrying an additional behaviour-bias map. The `TacticalPlanner` ([ADR-0013](./0013-bot-tactical-goal-layer.md)) reads `effectiveWeight(B) = derived(B) × objectiveBias(B) × roleBias(B)`.**

### Layer position

```
ShipConfig ──→ CapabilityProfile ──→ derived weights              (ADR-0014)
                                            ↓
ArenaModule produces ArenaObjective ──→ objectiveBias            (THIS ADR)
                                            ↓
ArenaObjective assigns BotRole at round start ──→ roleBias       (THIS ADR)
                                            ↓
                              effective weights → TacticalPlanner (ADR-0013)
                                            ↓
                                      goal selection
                                            ↓
                               BT executes per-goal sequence      (ADR-0009 / ADR-0013)
```

Capability-derivation answers "what can I do?"; objective answers "what should I be doing?"; role answers "what's my specific job within that objective?" Composition is multiplicative; precedence is later-wins-on-zero (a role weight of 0 hard-mutes the behaviour for that bot regardless of capability).

### `ArenaObjective` shape

```java
// api/infinity.ai.objective
public interface ArenaObjective {

  /** Stable identifier; surfaces in BotDebug HUD + telemetry. */
  String name();

  /** Multiplicative bias on capability-derived weights. 1.0 = no change; 0.0 = disable; >1.0 = boost. */
  Map<String, Double> behaviourBias();

  /** Static map tiles the navigation layer should pre-build DistanceFields for. */
  List<TileId> staticGoalTiles();

  /** Called at round start (or arena join) to assign each spawned bot a role; returns the role name. */
  String assignRole(EntityId bot, ArenaSnapshot snapshot);
}

public record DeathmatchObjective() implements ArenaObjective {
  public String name() { return "deathmatch"; }
  public Map<String, Double> behaviourBias() { return Map.of(); }   // identity
  public List<TileId> staticGoalTiles() { return List.of(); }
  public String assignRole(EntityId bot, ArenaSnapshot s) { return "default"; }
}

public record KothObjective(TileId centralTile) implements ArenaObjective {
  public String name() { return "koth"; }
  public Map<String, Double> behaviourBias() {
    return Map.of("anchor", 2.0, "mine-congestion-points", 1.5, "wander", 0.3);
  }
  public List<TileId> staticGoalTiles() { return List.of(centralTile); }
  public String assignRole(EntityId bot, ArenaSnapshot s) {
    return s.team(bot).controlsKothTile() ? "koth-holder" : "koth-contester";
  }
}

public record CtfObjective(Map<FrequencyId, TileId> flagTiles) implements ArenaObjective {
  public String name() { return "ctf"; }
  public Map<String, Double> behaviourBias() {
    return Map.of("defend-flag-tile", 2.0, "capture-flag", 2.0,
                  "mine-congestion-points", 1.3);
  }
  public List<TileId> staticGoalTiles() { return List.copyOf(flagTiles.values()); }
  public String assignRole(EntityId bot, ArenaSnapshot s) {
    return s.team(bot).hasFlagCarrier() ? "flag-defender" : "flag-attacker";
  }
}

public record PowerballObjective(Map<FrequencyId, TileId> goals) implements ArenaObjective {
  public String name() { return "powerball"; }
  public Map<String, Double> behaviourBias() {
    return Map.of("ball-carry", 2.5, "ball-pursue", 1.8,
                  "engage", 0.5, "mine-congestion-points", 0.2);
  }
  public List<TileId> staticGoalTiles() { return List.copyOf(goals.values()); }
  public String assignRole(EntityId bot, ArenaSnapshot s) {
    return s.ballCarrier() == bot ? "ball-carrier" : "ball-hunter";
  }
}
```

`ArenaObjective` is a **plain (non-sealed) interface**. The engine ships the canonical record subtypes above; community Groovy mechanic modules ([ADR-0008](./0008-arena-composition-and-modules.md)) can implement their own. Switches on `ArenaObjective` use a default branch rather than exhaustive pattern-match — small ergonomic loss for the engine, large extensibility gain for community modules. (Earlier `sealed interface ... permits ...` draft replaced — `sealed` was incompatible with ADR-0008's "community modules can ship new gametypes" stance.)

`ArenaSnapshot` is a small read-only view of the arena's current state — team rosters, who's carrying what, current scores — passed to `assignRole` to keep `ArenaObjective` decoupled from EntityData.

### `BotRole` shape — component (ID) + template (bias data) per CCP

Per [ADR-0002](./0002-config-component-projection.md) Config-Component Projection, the per-bot ECS component carries only the role *identity*; the bias *data* lives in a separate template tier loaded from Groovy. This split is mechanically cleaner (component is trivially immutable) and live-reload-friendly (tweaking role bias in Groovy doesn't require re-stamping every bot).

**The ECS component — just an ID:**

```java
// api/infinity.es (ECS component, server-only — does not cross the wire)
public final class BotRole implements EntityComponent {
  public static final String DEFAULT = "default";

  private final String name;

  public BotRole() { this("default"); }
  public BotRole(String name) { this.name = name; }
  public String name() { return name; }
}
```

Single immutable String field. Trivial component; mechanically compliant with [`components.md`](../../.claude/rules/components.md). Class (not record) per the existing convention for stamped server-only components.

**The template — bias data, loaded from Groovy:**

```java
// api/infinity.ai.objective
public record BotRoleConfig(String name, Map<String, Double> behaviourBias) {
  public BotRoleConfig {
    behaviourBias = Map.copyOf(behaviourBias);  // defensive immutability
  }
}
```

**Registry, lookup at planner time:**

```java
// api/infinity.ai.objective
public interface BotRoleRegistry {
  BotRoleConfig get(String roleName);   // throws if unregistered (caught by load-time validation)
  Collection<String> registeredNames();
}
```

`TacticalPlanner` ([ADR-0013](./0013-bot-tactical-goal-layer.md)) at scoring time:

```java
BotRole role = ed.getComponent(bot, BotRole.class);
BotRoleConfig roleConfig = roleRegistry.get(role.name());
double roleBias = roleConfig.behaviourBias().getOrDefault(behaviourName, 1.0);
```

**Authoring:** `BotRoleConfig` instances ship as engine defaults in `engine-bot-ai.groovy` (per [ADR-0014](./0014-capability-derived-bot-composition.md) §"Engine vs zone Groovy tiers"):

```groovy
// engine-bot-ai.groovy (engineer-authored)
roles {
    role 'default',         bias: [:]
    role 'koth-holder',     bias: ['anchor': 2.0, 'evade': 0.7]
    role 'koth-contester',  bias: ['engage': 1.5, 'anchor': 0.5]
    role 'flag-defender',   bias: ['defend-flag-tile': 2.5, 'engage': 0.8]
    role 'flag-attacker',   bias: ['capture-flag': 2.5, 'evade': 0.7]
    role 'ball-carrier',    bias: ['ball-carry': 3.0, 'engage': 0.2, 'evade': 1.5]
    role 'ball-hunter',     bias: ['ball-pursue': 2.0]
}
```

A new mechanic module shipping its own gametype adds its role configs to the same file (or to its own per-module Groovy fragment loaded into the same registry — pattern lands with the first community module).

**Live-reload:** when `engine-bot-ai.groovy` reloads, `BotRoleRegistry` re-loads with new bias data. Per-bot `BotRole` components don't need re-stamping — they hold names, not bias data. The planner reads the updated bias on the next scoring tick. Zero per-bot churn.

**Load-time validation:** the load pass walks every `behaviourBias` entry across all registered `BotRoleConfig`s and fails fast on any behaviour name that isn't in the `BehaviourRegistry` ([ADR-0013](./0013-bot-tactical-goal-layer.md) §"Behaviour registry"). Same validation pass covers synergy table + objective biases + per-arena tweak overlay.

### Effective weight composition

The `TacticalPlanner` ([ADR-0013](./0013-bot-tactical-goal-layer.md)) reads four sources:

```
derived(B)        = capability-derived weight, per ADR-0014 (depends on CapabilityProfile + engine synergy table)
objectiveBias(B)  = arenaContext.objective().behaviourBias().getOrDefault(B, 1.0)
roleBias(B)       = roleRegistry.get(bot.botRole.name()).behaviourBias().getOrDefault(B, 1.0)
overlay(B)        = arena.groovy 'bots' tweak entry, per ADR-0014 (1.0 if absent)

effective(B) = derived(B) × objectiveBias(B) × roleBias(B) × overlay(B)
```

`effective(B) < MIN_BEHAVIOUR_WEIGHT` → behaviour skipped from enumeration that tick (same as ADR-0014).

Multiplicative semantics propagate "hard mute" cleanly: any source setting weight to 0 disables the behaviour. This is the right shape for "in CTF, no bot wanders" (`objectiveBias{wander: 0.0}`) and for "this specific bot is the flag carrier; engagement is suppressed" (`roleBias{engage: 0.2}`).

### Mechanic-module producer integration

ADR-0008's `ArenaModule` contract grows one method:

```java
public interface ArenaModule {
  // ... existing lifecycle methods ...
  /** Mechanic modules return their ArenaObjective; non-mechanic modules return null. */
  default ArenaObjective objective() { return null; }
}
```

`BotAiArenaContext` polls registered modules on arena load and uses the first non-null `objective()` returned. If none, `DeathmatchObjective` is the implicit default (no bias, no goal tiles, default role).

The mechanic module is also the producer of dynamic objective state (current ball carrier, current KOTH controller, current flag state). The `ArenaSnapshot` passed to `assignRole` reads from the mechanic module's published state.

### Static goal-tile registration

`ArenaObjective.staticGoalTiles()` returns the tiles the navigation layer should pre-build `DistanceField`s for. `BotAiArenaContext.navigation()` registers them at arena load; the resulting fields are queryable as soon as the per-tile Dijkstra completes.

This is the integration seam with [ADR-0011](./0011-bot-navigation-navmesh.md) — the flow-field layer doesn't need to know what flags or KOTH centers *are*, just that some tiles want pre-built fields. The objective contract supplies them.

`ChokepointAnalyzer` ([ADR-0012](./0012-bot-spatial-analysis-services.md)) also feeds this set — its top-N chokepoint tiles get pre-built fields automatically (configurable cap in `engine-bot-ai.groovy`).

### Role assignment lifecycle

```
Round start (or arena join for late joiners):
  ArenaObjective.assignRole(bot, snapshot) → String (role name)
  BotRoleAssigner stamps BotRole(name) on the bot entity (canonical writer)
  EntityContainer.updateObject re-wires the brain to read new role bias

Per-tick:
  TacticalPlanner reads bot's BotRole(name), looks up roleRegistry.get(name).behaviourBias()

Role can change on event:
  ArenaObjective subscribes to its own mechanic state changes (ball-pickup,
  flag-grab) and re-runs assignRole + re-stamps BotRole. Same canonical-writer
  hook as round-start assignment.

Live engine-bot-ai.groovy reload:
  BotRoleRegistry re-loads with new BotRoleConfig data
  No per-bot re-stamping needed (bots hold name strings, not bias maps)
  Planner reads updated bias on next scoring tick
```

For v2.0, the only event-driven role change is "ball/flag possession changes" (Powerball, CTF). KOTH and Deathmatch reassign only on round transition. Turf reassigns on control-point flip.

### Layering

- **api/infinity.ai.objective.\*** — `ArenaObjective` plain interface + canonical record subtypes (`DeathmatchObjective` / `KothObjective` / `CtfObjective` / `TurfObjective` / `PowerballObjective`) + `BotRoleConfig` record + `BotRoleRegistry` interface + `ArenaSnapshot` interface.
- **api/infinity.es.BotRole** — single-String ECS component on the bot entity.
- **infinity-server/.../modules/mechanic/\*** — each mechanic module produces its objective in `objective()`; subscribes to its own state events for role reassignment.
- **infinity-server/.../ai/objective/\*** — `BotRoleAssigner` (the canonical writer of `BotRole`, hooked to round-start + objective events); `BotRoleRegistryImpl` (Groovy-loaded `BotRoleConfig` table).
- **infinity-server/.../settings/GroovyBotRolesLoader** — settings-pipeline adapter that loads `roles { ... }` blocks from `engine-bot-ai.groovy` into `BotRoleRegistry`.

### Live-reload semantics

| Event | Action |
|---|---|
| `EngineBotAiReloaded` | `BotRoleRegistry` re-loads `BotRoleConfig` templates. **No per-bot re-stamping** (bots hold name strings, not bias data). Planner reads updated bias on next scoring tick. |
| `ArenaGroovyReloaded` | If the mechanic module reconfigured (rare), the arena's `ArenaObjective` may change. Orchestrator re-runs `assignRole` for all bots in the arena; `BotRoleAssigner` re-stamps `BotRole` where role name changed. |
| `ZoneBotAiReloaded` | No direct action (no zone-tier objective/role config in v2.0). |
| `ObjectiveStateChanged` (mechanic-published; ball pickup, flag grab, KOTH flip) | `BotRoleAssigner` re-runs `assignRole` for affected bots; re-stamps `BotRole` where role name changed. Per ADR-0003 EventBus subscription. |

Same per-event-subscription pattern as the rest of the substrate. Each subsystem documents which events it cares about; orchestration via the existing `BotAiHostService`.

## Consequences

### Positive

- **Gametype-aware bots without per-gametype brain forks.** A Shark in CTF and a Shark in KOTH share the same `CombatantBrain`; the objective + role inject the behaviour shift via weight bias. No new `CtfSharkBrain` class.
- **Mechanic modules own their AI contract.** A new gametype = a new `ArenaModule` + a new `ArenaObjective` record + (probably) new behaviours for the gametype-specific actions (`ball-carry`, `defend-flag-tile`). No central registry to update; the catalog of supported gametypes is just "which mechanic modules ship in this build."
- **Composition with capability derivation is multiplicative.** A Levi (anchor-derived) × KOTH (anchor-biased) = a Levi that *really* wants to anchor on the central tile. A Spider (engage-derived) × KOTH (anchor-biased) = a Spider that anchors more than usual but still engages — derivation modulates the bias.
- **Roles solve the "team coordination via shared map state" problem cheaply.** No team-level planner; each bot reads its own role + the shared objective. Implicit coordination via objective state ("the team already has a flag defender; I'm assigned attacker").
- **Static goal tiles unify with navigation.** `ArenaObjective.staticGoalTiles()` + `ChokepointAnalyzer` produce the goal set; the flow-field layer ([ADR-0011](./0011-bot-navigation-navmesh.md)) pre-builds and caches. One pathway, gametype-extensible.

### Costs

- **`ArenaModule` interface grows by one method.** Default `objective() → null` preserves backward compatibility; non-mechanic modules don't override. Modest API surface growth.
- **One more weight source in the planner.** `derived × objective × role × overlay` is four multiplicative terms vs ADR-0014's two. Trivially cheap at runtime; conceptually one more thing for a contributor to track when debugging "why is this bot scoring X."
- **`BotRole` reassignment events** need wiring per mechanic. Each gametype's mechanic must subscribe to its own state changes and call the assigner. Boilerplate per gametype.
- **The behaviour vocabulary grows.** "Defend flag tile," "capture flag," "ball carry," "ball pursue" are new behaviours per gametype. Each needs an enumerator + scorer + Execute Sequence. Honest accounting: this isn't free — each gametype's AI is real implementation work beyond just defining the objective record.
- **`BotDebug` HUD must surface the role.** Without it, "why is this bot doing X?" is undebuggable. Cheap once the HUD exists.

### Performance budget (estimates; profile-validated per slice)

- **`assignRole` cost:** per bot per round-start / objective-state event. Each call is a few field reads + a Map lookup; ~microseconds per bot.
- **Memory per bot:** `BotRole` is ~20 bytes (String reference). Negligible.
- **Memory per arena:** `BotRoleConfig` templates × ~7 roles × ~30 bytes per entry = ~1 KB. Negligible.
- **`ObjectiveStateChanged` event rate:** Powerball/CTF ~once per few seconds; KOTH/Turf ~once per round; Deathmatch never. Bounded.

Numbers are seeds; first mechanic-module objective impl slice measures actuals.

### Neutral

- **Role bias precedence.** Roles override objectives where they conflict (role bias multiplies *after* objective bias; a role bias of 0.2 with an objective bias of 2.0 nets 0.4). This is the right shape — roles are more specific than objectives.
- **Default-Deathmatch fallback** keeps non-gametype arenas working unchanged. Any arena without a mechanic-module-supplied objective gets identity bias and default role.
- **Mid-round role switching** beyond ball/flag possession is reserved. v2.x can add a per-objective "should I reassign now?" tick if a gametype's design warrants it.

## Alternatives considered

### A. Per-gametype brain classes (`KothCombatantBrain`, `CtfCombatantBrain`, ...)

**Why considered:** Most direct — one class per gametype that overrides the BT root.
**Why rejected:** N × M combinatorial: 8 ships × 5+ gametypes = 40+ brain classes, all maintained in parallel. The whole point of ADR-0010 + ADR-0014 was to avoid this kind of forking; the objective layer keeps the parameterize-don't-fork thesis intact at the gametype dimension too.

### B. Inject objective state into the BT directly (no weight bias, just conditional branches)

**Why considered:** The BT has Selector + Sequence + Condition; "if KOTH and not holding tile, navigate to tile" is one Sequence.
**Why rejected:** This bloats the BT root with per-gametype branches and couples the brain to gametype enumeration. The weight-bias model keeps the BT generic — it dispatches on the *picked goal*, which the weight composition produces; the BT doesn't know whether the goal came from KOTH bias or capability derivation.

### C. Pure event-driven role reassignment (no objective; bots react to events)

**Why considered:** No central objective concept; bots subscribe to events ("flag grabbed") and adjust behaviour directly.
**Why rejected:** Distributed coordination problem. Bots can't easily ask "am I the closest available to defend the flag?" without a shared coordinator. The objective + role assignment is precisely that coordinator, made cheap by being a per-round-start one-shot for most cases.

### D. GOAP for arena-level goals

**Why considered:** F.E.A.R.-style action planning at the arena level — "satisfy round-win condition by sequencing actions."
**Why rejected:** Same argument as in ADR-0013 — GOAP plans *sequences*; this ADR's contribution is weight bias on a per-goal-tick selection. The arena's win-condition state is implicit in the objective's role assignment; sequence planning would be overkill for "defend the flag" or "anchor the tile."

### E. Per-bot LLM-style intent

**Why considered:** Each bot reasons about the arena state with full freedom; no fixed objectives or roles.
**Why rejected:** Out of v2 scope. Cost and latency make it inappropriate for per-tick or per-planner-cycle decisions. Reserve for a hypothetical v3+ "narrator bot" or "commentary bot" feature; gameplay AI stays mechanism-driven.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Producer of objective | **Mechanic `ArenaModule`** ([ADR-0008](./0008-arena-composition-and-modules.md)). Returns `ArenaObjective` from a new `objective()` method (default null). |
| Objective shape | **Plain (non-sealed) interface** with engine-shipped canonical record subtypes (Deathmatch, KOTH, CTF, Turf, Powerball). Community Groovy mechanic modules ([ADR-0008](./0008-arena-composition-and-modules.md)) can implement their own. |
| Bias shape | **Multiplicative `Map<String, Double>`** of behaviour name → factor. Behaviour names validated at load against `BehaviourRegistry` ([ADR-0013](./0013-bot-tactical-goal-layer.md) §"Behaviour registry"). |
| Role shape | **Component-template split per CCP** ([ADR-0002](./0002-config-component-projection.md)): `BotRole(name: String)` is the per-bot ECS component (trivially immutable); `BotRoleConfig(name, behaviourBias)` is the template loaded from `engine-bot-ai.groovy`; `BotRoleRegistry` is the lookup surface. Live-reload-friendly (re-loading templates doesn't re-stamp bots). |
| Role assignment | **At round start + on objective-state events** (ball/flag possession changes). Canonical writer `BotRoleAssigner`. |
| Effective weight | `derived × objectiveBias × roleBias × overlay`. Multiplicative; zero in any term disables the behaviour. |
| Static goal tiles | `ArenaObjective.staticGoalTiles()` registered with [ADR-0011](./0011-bot-navigation-navmesh.md) `NavigationFields`; chokepoint tiles ([ADR-0012](./0012-bot-spatial-analysis-services.md)) join automatically. |
| Default if no mechanic | `DeathmatchObjective` (identity bias, default role, no goal tiles). |
| Mid-round role change | Event-driven for possession-based gametypes (CTF, Powerball). Tick-driven for others is v2.x+. |
| Multi-objective composition | **Not v2.0.** One objective per arena. Composition operator deferred. |

## Open work

- **Mechanic-module objective implementations.** `KothMechanic`, `CtfMechanic`, `PowerballMechanic`, `TurfMechanic` each need an `objective()` impl. Land per-mechanic as the gametype's v2 PRD slice surfaces.
- **Gametype-specific behaviours.** `defend-flag-tile`, `capture-flag`, `ball-carry`, `ball-pursue`, `koth-anchor`, `turf-claim` — each needs an enumerator + scorer + Execute Sequence. Land per-mechanic alongside the objective impl.
- **`BotDebug` HUD role surface.** Show current `BotRole.name()` + role bias next to the bot's capability profile + objective bias. Cheap; high-debug-value.
- **Test infrastructure.** `FakeArenaObjective` (with overridable bias + role-assignment lambda) for planner tests. Real-mechanic objective tests at the mechanic-module level.
- **`ArenaSnapshot` shape.** Settle the read-only-view interface — team rosters, current carrier(s), current score, current control-point holders. Lands with the first mechanic-module objective impl.
- **Multi-objective composition operator.** Reserve; design when a v2.x gametype legitimately needs two objectives at once (e.g. "KOTH but also score kills").

## References

### Internal

- [ADR-0001](./0001-ecs-component-model.md) — Component model. `BotRole` is a server-only component; canonical writer is `BotRoleAssigner`.
- [ADR-0002](./0002-config-component-projection.md) — CCP. `BotRole` stamps follow the same shape (config → component) as `ShipConfig` → ECS components.
- [ADR-0005](./0005-layered-architecture.md) — api purity. `ArenaObjective` + `BotRole` + `ArenaSnapshot` are api-tier; assigner + mechanic-module impls are server.
- [ADR-0008](./0008-arena-composition-and-modules.md) — Arena composition. `ArenaModule.objective()` is the new producer hook.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI substrate. `BotBrainSystem` reads `BotRole` per tick; brain unchanged.
- [ADR-0010](./0010-bot-composition-dsl.md) — Bot composition DSL. Per-arena tweak overlay multiplies on top of objective + role bias.
- [ADR-0011](./0011-bot-navigation-navmesh.md) — Navigation. `staticGoalTiles()` registers `DistanceField` goals.
- [ADR-0012](./0012-bot-spatial-analysis-services.md) — Spatial analysis. `ChokepointAnalyzer` tile-list complements objective-supplied goal tiles.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — Tactical-goal layer. Planner reads `effective = derived × objective × role × overlay`.
- [ADR-0014](./0014-capability-derived-bot-composition.md) — Capability derivation. `derived` is one of the four multiplicative weight sources.

### External

- **Damian Isla, "Handling Complexity in the Halo 2 AI"** (GDC 2005). Encounter-level objective context biasing per-NPC behaviour — the architectural template this ADR generalises to per-mechanic objectives.
- **Killzone 2 / 3** (Guerrilla, GDC 2009-2011). Per-bot role assignment within a squad-level objective; same shape.
- **Quake 3 Arena bots** (id Software, ~1999). Per-gametype behaviour bias (CTF bots vs DM bots) implemented as per-gametype state-machine variants. The objective+role decomposition here is the parameterised version.
- **Team Fortress 2 bots** (Valve). Role-based coordination ("Engineer plays defense; Scout plays attack") with per-gametype objective state. Production reference for the role-assignment pattern.
- **Quake 3 / Quake Live AAS / BSP item-table** — production reference for "load-time tile-list registration feeds runtime queries" (their item table is the static-goal-set analogue).
