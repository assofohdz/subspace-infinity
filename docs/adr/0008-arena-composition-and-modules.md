# ADR 0008 — Arena composition: gametypes as compositions of horizontal modules

**Status:** Proposed
**Date:** 2026-05-15
**Deciders:** Asser Fahrenholz

## Context

Subspace Infinity inherits a rich vocabulary of *gametypes* from the
Subspace canon — Free For All, Trench (Turf-shaped), KOTH, Jackpot,
CTF, Powerball, Speed Zone, Dueling, and any new arena-style mode the
zone wants to ship next. Each gametype is recognisable as a distinct
experience, but underneath they share most of their machinery: a way
to score, a way to end the round, a way to assign teams, a way to
place spawns, an optional shop.

Today the codebase has no language for this. An arena's `arena.groovy`
declares settings (ship tunings, prize weights, friction) but nothing
behavioural. The `BaseGameModule` interface in `api/src/main/java/infinity/sim/`
survives as a one-time stub for a server-extension contract — the
implementation home (`modules/` Gradle subproject) was deleted in
v1.0.17, and no concrete implementations exist. Scoring is not
modelled at all: kills broadcast as an `onPlayerKilled` arena event,
but no system consumes the event to award points. There is no win
condition, no round manager, no respawn policy module, no roster gate.
Teams emerge from `Frequency` (the int 0..65535 on a ship) without any
per-arena composition.

The forward question — *"how do we ship KOTH and Jackpot as different
arenas on the same server?"* — has two industry-standard answers:

1. **God-class gametypes** (the dominant industry pattern: Unreal
   `AGameModeBase`, Source `gamerules.cpp`, every other arena shooter).
   One class per gametype, subclassed for variants. Self-contained.
2. **Scripted compositional gametypes** (Halo Reach's Megalo, modern
   Halo's custom-game systems, some Lua-modded servers). Each gametype
   is a *bundle* of settings + script that composes orthogonal
   capabilities. Authoring is data-driven, not subclass-driven.

The god-class pattern is simpler in isolation but produces
combinatorial duplication across gametypes (every gametype re-
implements scoring, teams, respawn) and is hostile to
[ADR-0001](./0001-ecs-component-model.md)'s one-canonical-writer-per-
component rule (two god-class gametypes both writing `PlayerScore` is
exactly the multi-writer race ADR-0001 was written to prevent). The
scripted-compositional pattern matches Infinity's existing strengths —
Groovy settings DSL, ECS component-grain dispatch, hot-reload via
mtime polling — but only if the composition layer is itself
architected.

This ADR proposes the shape of that composition layer.

## Decision

**Arenas are composed of *horizontal modules* — orthogonal slices of
arena-scoped behaviour. Gametypes (KOTH, Jackpot, …) are *emergent*
compositions of modules; humans give the composition a name, the
runtime sees only modules.**

### Module categories and composition shapes

A *module* is a per-arena, swappable concern with a behaviourally
distinct algorithm. The pluggability boundary is **behavioural vs
numeric**: variation that changes the *algorithm* between arenas is a
module; variation that only changes *numbers* in a shared algorithm
stays as a core system tuned by `*Config`. `EnergySystem` is core
(every arena uses regen-toward-max; only `EnergyMax` varies).
`ScoringSystem` is *not* core, because Jackpot's kill-pot algorithm is
genuinely different from Turf's points-per-flag-per-second algorithm.

Modules group into three composition shapes:

- **Single-pick** — exactly one module per slot per arena. The loader
  rejects duplicates. Categories: `teamSetup`, `roster`,
  `respawnPolicy`, `roundStructure`, `matchStructure`, `spawnPlacement`,
  `shop`.
- **Layered** — zero or more modules per slot, contributions merged by
  category-specific semantics. No declaration-order guarantee. Anything
  order-sensitive must be reshaped as an additive contribution.
  Categories: `scoring` (contributions sum); `winCondition` (triggers
  OR, first to fire ends the round).
- **Opt-in mechanics** — independently included gameplay objects, each
  carrying its own placement/config inline. Each is a binary
  include/exclude. Catalog: `crowns`, `carryFlags`, `staticFlags`,
  `balls` + `goals`, `regionTriggers`, `lapDetector`, `shrinkingZone`.

Mechanics *publish state* (e.g. `CrownOwnership`, `FlagOwnership`,
`OutsidePlayArea`) that layered modules read. Missing-mechanic with
dependent-scoring is a load-time error.

### Module implementations

The engine ships a **fixed catalog of Java module types**, each
implementing the `ArenaModule` interface (renamed from
`BaseGameModule`, which has zero implementations today). The catalog
is an explicit `ModuleCatalog` class in `infinity-server/` — not an
SPI scan — so the available module types are greppable in one place.

`arena.groovy` is **pure data**: it references module types by string
identifier and supplies their config inline.

```groovy
arena {
  map 'koth-classic.lvl'

  teamSetup      'ffa-private-freqs'
  roster         'all-ships'
  respawnPolicy  'lockout-no-crown'
  roundStructure 'crown-reset'
  matchStructure 'continuous'
  spawnPlacement 'random-radius', center: [512, 512], radius: 200
  shop           'flat'

  mechanic 'crowns', expiresOnDeath: true
  mechanic 'shrinkingZone', schedule: [
    [t: 60.seconds,  radius: 400],
    [t: 180.seconds, radius: 200],
  ]

  scoring 'kill-points', perKill: 100
  scoring 'crown-kill-bonus', perKill: 250

  winCondition 'last-crown-standing', bonus: 1000
  winCondition 'timer-expired', minutes: 15
}
```

Each string id maps to a `Class<? extends ArenaModule>` registered in
`ModuleCatalog`. Each kwargs map binds to that type's paired `*Config`
record (per [ADR-0002](./0002-config-component-projection.md) Config-
Component Projection — `*Config` records are immutable templates that
spawn systems project into per-entity components).

### Per-arena instances

When an arena loads, the framework instantiates each declared module
*for that arena*. Two arenas running `scoring 'kill-points'` get two
independent `KillPointsScoring` instances, each bound to its arena's
config and `ArenaId`. Modules never have to filter by `ArenaId` —
their instance is the scope. The N×M growth (N arenas × M modules) is
fine in practice (modest arena counts, modest module counts per
arena); the alternative (zone-singleton with arena-filtering inside
every module) was rejected for the friction it adds to module
authoring.

### `ArenaModule` interface

Single unified interface in `api/src/main/java/infinity/sim/` with
default no-op hooks. Module types override only what they care about.

```java
public interface ArenaModule {
  String moduleType();                 // catalog id, e.g. "kill-points"
  ModuleCategory category();           // SCORING, WIN_CONDITION, MECHANIC, …
  Class<?> configType();               // paired *Config record class

  default void onArenaLoad(ArenaId arenaId, Object config)         {}
  default void onConfigReloaded(Object newConfig)                  {}
  default void onMatchStart(ArenaId arenaId)                       {}
  default void onRoundStart(ArenaId arenaId, int roundNumber)      {}
  default void onRoundEnd(ArenaId arenaId, int roundNumber, RoundOutcome o) {}
  default void onMatchEnd(ArenaId arenaId, MatchOutcome o)         {}
  default void onArenaUnload(ArenaId arenaId)                      {}
}
```

The five service interfaces the prior `BaseGameModule` injected
(`PhysicsManager`, `ChatHostedPoster`, `AccountManager`,
`ArenaManager`, `TimeManager`) are retained — modules get them
constructor-injected, alongside the arena's `EntityData` and the per-
arena `EventBus`. This is the **extensibility principle**: hand
modules powerful tools and trust them. The framework provides the safe
frame (lifecycle, coordinators, fail-fast loader); the module decides
what to do inside it.

### Two-level round / match structure

Every arena composes **both** a `roundStructure` and a `matchStructure`.
Degenerate variants (`continuous`) exist for arenas that don't need a
level — a persistent FFA composes `matchStructure 'continuous'` +
`roundStructure 'timed(minutes: 10)'` so the match level is a no-op
and the round resets every 10 minutes.

- `roundStructure` answers "what is one round?" Variants:
  `continuous`, `timed`, `elimination`, `score-threshold`,
  `crown-reset`, `hold-flag-for`, `lap-based`. Fires `onRoundStart`
  and `onRoundEnd`.
- `matchStructure` answers "how do rounds compose into a match?"
  Variants: `continuous`, `single-round`, `best-of(N)`,
  `period-based(N)`, `round-robin`. Fires `onMatchStart` and
  `onMatchEnd`.

`winCondition` triggers feed the active `roundStructure`'s decision
logic (via the `WinConditionCoordinator`); the `roundStructure`
decides when to fire `onRoundEnd`. The `matchStructure` decides
whether to also fire `onMatchEnd`, or to dispatch the next round.

Designing for two levels from day one (rather than collapsing to a
single `roundStructure` and emitting a second event from inside it)
costs little and lets nested-match shapes — best-of-N, tournament
brackets, round-robin — land as new `matchStructure` variants without
schema migration.

### Four-phase tick

Each arena tick runs modules in four ordered phases, an application of
[ADR-0001](./0001-ecs-component-model.md)'s phased-tick discipline
scoped to module categories:

1. **Mechanics phase.** Every loaded `mechanic` module reads last-
   tick canonical state and publishes its mechanic-state components
   (`CrownOwnership`, `FlagOwnership`, `BallPossession`,
   `OutsidePlayArea`, …).
2. **Module-contribution phase.** `scoring` and `winCondition`
   modules read mechanic state from phase 1 plus game-event signals
   (kill, death, capture) and emit *transient* contribution components
   (`ScoreContribution`, `WinConditionTrigger`). Modules never write
   canonical components.
3. **Coordinator phase.** Core systems drain contributions and write
   canonical components — `ScoreCoordinatorSystem` sums
   `ScoreContribution`s into `PlayerScore` and `TeamScore`;
   `WinConditionCoordinatorSystem` ORs `WinConditionTrigger`s; if any
   fires, it signals the active `roundStructure`.
4. **Lifecycle dispatch phase** *(only on round-end or match-end
   tick)*. The framework directly calls lifecycle hooks on all loaded
   modules — `onRoundEnd` first, then optionally `onMatchEnd`, then
   `onMatchStart` / `onRoundStart` for the next iteration. All within
   the same tick. The historical `RoundReset` event is replaced by
   direct method dispatch.

### Coordinator pattern

The bridge between *layered modules* and ADR-0001's *one canonical
writer per component*. Modules emit transient `ScoreContribution`
entities; the always-loaded `ScoreCoordinatorSystem` is the sole
canonical writer of `PlayerScore`. Many emitters, one writer — the
same shape ADR-0001 uses for `*Change` entities, but applied at the
*layered-module-contribution* level.

This generalises beyond scoring: any time a category is layered, a
coordinator owns its canonical output. `WinConditionCoordinator`
exists for the same reason. Future layered categories (`buffApplier`,
`spawnModifier`) would each get a coordinator.

### DSL shape

`arena.groovy` files use top-level statements, no wrapping `arena { }`
block. This matches today's `zone.groovy` and existing arena files;
zero migration cost. The DSL is "data + a fixed vocabulary", same as
existing settings DSLs.

- **Settings fragments** (existing `includeFragment`) contribute
  Subspace-canonical INI settings (`section('Ship') { ... }`) and stay
  in `zone/conf/<preset>/`. *Unchanged by this ADR.*
- **Module presets** (new `usePreset`) contribute bundles of module
  statements. Live in `zone/presets/<gametype>/`. A single fragment
  file may *not* mix settings and modules.
- **No `extends`** — only flat composition. Arenas list `usePreset`
  calls and their own statements; resolution is declaration-order.
- **Override semantics.** Single-pick categories let a later
  declaration *replace* (preset says `teamSetup 'ffa-private'`, arena
  says `teamSetup '2-fixed-teams'`, arena wins). Layered categories
  *append*. **No remove operator** — if a preset gives you the wrong
  bundle, skip the preset.

### Hot-reload model

Three flavours, all in v1 scope, all leveraging the existing settings
mtime-poll pipeline ([ADR-0004](./0004-settings-pipeline.md)):

- **Config-diff** — author edits kwargs (`perKill: 100 → 200`); the
  module gets `onConfigReloaded(newConfig)`. No teardown.
- **Module-set-diff** — author adds/removes modules; the loader diffs
  the set, calls `onArenaLoad` on new modules and `onArenaUnload` on
  removed ones. **Cleanup contract:** every mechanic module's
  `onArenaUnload` MUST remove the components/entities it owns. This
  contract is the test bed for the future external-author module
  loader; if it can't work for built-ins it can't work for externals.
- **Map swap** — separate concern; existing `~swapMap`. If
  `arena.groovy`'s `map '…'` line changes during reload, map swap
  fires as a consequence; modules are *not* torn down (state survives
  the swap; `spawnPlacement` re-runs to place players on the new map).

### Module ↔ game-event channels

Modules consume game events via the same channels as core systems —
no game-event hooks on `ArenaModule`. Two pre-existing channels cover
this, per [ADR-0003](./0003-communication-channels.md):

- **ECS transient components** — for in-tick, atomic, queryable state
  changes. A scoring module reads `KillEvent` transient components in
  phase 2.
- **Arena event subscription** — the per-arena `EventBus` is injected
  into each module at instantiation; modules call
  `eventBus.addListener(…)` in `onArenaLoad` and remove in
  `onArenaUnload`.

`ArenaModule` stays minimal — *only* lifecycle hooks. Game-event
subscriptions are uses of injected services, not interface methods.
This is consistency with how the rest of the codebase reads game
events, not a constraint on what modules can do.

### Layered architecture mapping

Per [ADR-0005](./0005-layered-architecture.md):

- **`api/`** — `ArenaModule` interface, `ModuleCategory` enum, all
  module `*Config` records, all components modules write or read
  (`PlayerScore`, `TeamScore`, `RoundTimer`, `MatchScore`,
  `FlagOwnership`, `CrownOwnership`, `OutsidePlayArea`, …), all
  transient contribution components (`ScoreContribution`,
  `WinConditionTrigger`, …), `RoundOutcome` and `MatchOutcome` types.
- **`infinity-server/`** — concrete module classes
  (`KillPointsScoring`, `JackpotPotScoring`, `CrownsMechanic`, …),
  the `ModuleCatalog` registry, the `ModuleLoader`, and coordinator
  systems (`ScoreCoordinatorSystem`, `WinConditionCoordinatorSystem`).
- **`infinity-client/`** — never instantiates modules; reads the
  canonical components modules produce (already covered by
  `client-read-only.md`).

A future external-author module loader will compile Groovy modules
dropped under `zone/<author-modules>/` and append them to the catalog
at server start. Those externals see api/ only; the layering rule
makes the extension surface mechanical.

### Loader fail-fast diagnostics

The loader rejects an arena at load time for:

- Unknown module identifier (`scoring 'nonexistent'`) — with did-you-mean.
- Duplicate single-pick category outside the preset-override path.
- Required mechanic missing for a scoring or winCondition (`scoring
  'flag-captures'` with no `mechanic 'carryFlags'`).
- Cyclic `requires:` graph between mechanics.
- `*Config` record validation error (e.g. negative `perKill`).

Soft-degrade is explicitly rejected. A silently-broken gametype is
much harder to debug than an arena that refuses to start with a
clear message; player-facing impact is the same.

## Consequences

### Positive

- **Compositional reuse.** KillPoints scoring is shared across FFA,
  KOTH, Jackpot, CTF, Turf, Dueling. RoundReset semantics are shared
  across every gametype with rounds. Team-side spawn is shared across
  every team gametype. None of this is duplicated.
- **One canonical writer per component, preserved.** Coordinators are
  the canonical writers of the few components fed by layered modules
  (`PlayerScore`, `TeamScore`). Modules contribute via transient
  components — the same shape ADR-0001 already uses for `*Change`
  entities. No new race surface.
- **Strategic depth from layered win conditions.** A Trench arena can
  compose both *"hold flag for 5 minutes"* and *"first team to 10000
  points"* and let either branch close the round. Players choose how
  to win.
- **Map-agnostic gametypes.** Spawn placement is module-owned, not
  map-owned. The same `trench.lvl` can back a 2-team Turf arena and
  an FFA-Deathmatch arena with different spawn rules.
- **Hot-reload at three levels** — config tweaks land instantly,
  module swaps land without arena restart, map swaps surface as a
  side-effect of editing `map '…'`. All via the existing settings
  mtime-poll pipeline.
- **Future external-author modules.** The extension path doesn't
  require this design to land first; it depends on this design having
  landed. Authors will drop a `zone/<modules>/<name>/module.groovy`
  file and the loader will compile and register it under a new
  identifier. No `arena.groovy` change.
- **Fail-fast loader.** Arena startup either succeeds with a fully-
  wired gametype or fails with a specific error. No silently-broken
  arenas.

### Costs (accepted, not avoided)

- **More component types than today.** `PlayerScore`, `TeamScore`,
  `RoundTimer`, `MatchScore`, `RoundOutcome`, `MatchOutcome`,
  `ScoreContribution`, `WinConditionTrigger`, the mechanic-state
  components per mechanic. The CCP layering ([ADR-0002](./0002-config-
  component-projection.md)) plus per-arena instance scoping keeps the
  cost manageable, but the count is real.
- **Per-arena module instances multiply allocation.** N arenas × M
  modules = N×M instances. Practical numbers stay small (≤10 arenas,
  ≤15 modules per arena ≈ ≤150 instances total); none of this is hot-
  path-allocated. Accepted.
- **The 4-phase tick is more rigorous than today's "every system runs
  in registration order" model.** Adds scheduler complexity at the
  framework layer. Justified by the multi-writer-without-races
  property it buys.
- **Module-set-diff hot-reload requires a cleanup contract.** Every
  mechanic's `onArenaUnload` MUST remove the state it owns; getting
  this wrong leaks zombie components. Code review + a contract test
  on `ArenaModule` is the mitigation; the contract itself is the test
  bed for the future external-author loader.
- **DSL surface to learn.** Authors must know which categories are
  single-pick vs layered, what mechanics each scoring contribution
  depends on. Discoverable from the catalog class + fail-fast loader
  diagnostics; documented in CONTEXT.md and module-author docs.

### Neutral / deferred

- **AND-composed win conditions.** v1 ships OR-only. A future
  `composite-and` winCondition module can compose triggers
  (`composite-and(['first-to-N-points', 'own-all-flags'])`) without
  schema migration.
- **Durable state persistence across server restart.** Per-arena
  module state is in-memory only for v1. Adding durability is a
  separate ADR pending a real consumer (league play, ranked stats).
- **Module priority / explicit ordering hints.** Skipped; the phased
  model + "no within-category order" handles the cases enumerated.
  Add when a real conflict needs it.
- **Cross-arena module communication.** Modules are per-arena;
  cross-arena messaging is a zone-event concern, not a module concern.

## Alternatives considered

- **Single god-class GameMode per gametype** (the Unreal `AGameModeBase`
  / Source `gamerules.cpp` pattern). Industry-dominant. Rejected
  because (a) it produces combinatorial duplication across gametypes
  that share most of their machinery, (b) it's hostile to ADR-0001
  (two god-class gametypes both writing `PlayerScore`), (c) it
  doesn't admit external-author extension without subclass-shipping.
  Conceptual simplicity per-gametype isn't worth the cross-gametype
  duplication and the lock-in.
- **Frame B: modules = whole gametypes** ("KOTH module", "Jackpot
  module"). Rejected for the same combinatorial-duplication reason as
  the god-class pattern.
- **Frame C: hybrid** (a coarse "GameMode" module wrapping fine
  subsystem modules). Rejected as needless layering — the preset
  mechanism (`usePreset 'koth-base'`) already gives authors the named-
  bundle ergonomics without a second module tier.
- **Pure-Groovy modules** (modules are Groovy scripts, not Java
  classes). Considered. Rejected for v1 because (a) hot-paths in
  Groovy are measurably slower, (b) IDE refactor + PMD + checkstyle
  don't cover Groovy code, (c) the Groovy security whitelist for
  general code is a much wider surface than for the existing settings
  DSL. The future external-author loader preserves this path as an
  *opt-in* extension; built-in modules stay Java.
- **Zone-singleton modules with arena filtering** (one
  `KillPointsScoring` for the whole zone; consults a `Map<ArenaId,
  KillPointsConfig>` to know which arenas it serves). Rejected for
  authoring friction — every module would need filter-aware code.
  Per-arena instances are cleaner; the instance count is modest.
- **Single-level round structure with both round and match events
  emitted from one module type.** Considered. Rejected because the
  symmetric two-level shape costs nothing extra now and lets nested-
  match gametypes (best-of-N, round-robin, tournament-bracket) land
  as new `matchStructure` variants without rework.
- **Game-event hooks on `ArenaModule`** (`onPlayerKilled(victim,
  killer, weapon)`, `onPlayerSpawn(player)`, …). Considered;
  ergonomically appealing. Rejected because it introduces a parallel
  channel to existing ECS-transient-component + arena-event reads,
  which is the convention for every other system in the codebase.
  Modules use those same channels; the extensibility principle
  (powerful tools, trust the module) substitutes flexibility for
  syntactic sugar.
- **SPI auto-registration of module types.** Rejected for an explicit
  `ModuleCatalog` class. Discoverability of the available catalog (one
  greppable file) is worth more than the modest cost of adding a
  catalog entry alongside the module class.

## Resolved decisions

- **Frame:** horizontal modules; gametypes are emergent compositions.
- **Pluggability boundary:** behavioural variation = module; numeric
  variation = core system tuned by `*Config`.
- **Composition shapes:** single-pick, layered, opt-in mechanic.
- **Implementation:** Java module classes registered in an explicit
  `ModuleCatalog`; `arena.groovy` is pure data.
- **Instance scope:** per-arena.
- **Lifecycle interface:** unified `ArenaModule` with default no-op
  hooks (load, match-start, round-start, round-end, match-end, unload,
  config-reload). Renamed from `BaseGameModule`.
- **Round/match structure:** two-level, both single-pick, degenerate
  `continuous` variants supported.
- **Tick discipline:** four phases — Mechanics → Module-contribution
  → Coordinator → Lifecycle-dispatch.
- **Coordinator pattern:** core systems are the canonical writers of
  components fed by layered modules; modules emit transient
  contributions.
- **DSL shape:** top-level statements, no `extends`, single-pick
  later-wins, layered later-appends, no remove operator. Settings
  fragments and module presets are separate file pipelines.
- **Hot-reload:** config-diff, module-set-diff (with cleanup contract),
  map-swap (separate). All in v1 scope.
- **State persistence:** in-memory only for v1.
- **Module → game events:** ECS transient components + per-arena
  EventBus; no game-event hooks on `ArenaModule`.
- **Extensibility principle:** safe frame, wacky content. Hand
  modules powerful tools (`EntityData`, EventBus, services) and trust
  them.
- **Loader posture:** fail-fast on every load-time inconsistency.

## Open work (PRD-scope)

- **Slice plan.** Land the framework first (`ArenaModule` interface,
  `ModuleCatalog`, `ModuleLoader`, the two coordinator systems, the
  4-phase scheduler), then the first slice of concrete modules
  (`kill-points`, `ffa-private-freqs`, `all-ships`, `instant-respawn`,
  `continuous` round + match, `random-radius` spawn, `flat` shop —
  enough to ship a single FFA-Deathmatch arena as the first consumer).
  Each subsequent gametype is its own slice, ordered by gameplay
  priority.
- **Module-author docs.** A short authoring guide alongside this ADR
  for "how do I write a new scoring module?". Covers the lifecycle
  interface, the contribution-emission pattern, the cleanup contract,
  and the catalog-registration step.
- **Future external-author module loader.** Separate PRD, depends on
  this ADR. Resurrects the spirit of the deleted `modules/`
  subproject under a guardrailed Groovy loader path. Out of scope
  here.
- **Durable state persistence.** Separate ADR pending a real consumer.
- **Live `ship-config-dictionary.md` extension.** As new modules
  surface settings, the dictionary tracker may need a sibling for
  module configs.

## References

- [ADR-0001](./0001-ecs-component-model.md) — one canonical writer per
  component, `*Change` entity mutation. The coordinator pattern in
  this ADR is the same shape, applied one tier up (layered modules
  → canonical components).
- [ADR-0002](./0002-config-component-projection.md) — Config-
  Component Projection. Module `*Config` records are templates;
  components live on entities; the boundary is the spawn / module-load
  step.
- [ADR-0003](./0003-communication-channels.md) — the three event
  planes (ECS transient component, arena event, zone event). Modules
  use all three; the choice per-event is per ADR-0003, not introduced
  here.
- [ADR-0004](./0004-settings-pipeline.md) — settings pipeline + hot-
  reload via mtime polling. Module configs piggy-back on this; module-
  set diffs extend it.
- [ADR-0005](./0005-layered-architecture.md) — api/server/client
  boundaries. `ArenaModule` lives in api/; concrete classes in
  infinity-server/; client is read-only.
- [ADR-0007](./0007-entity-ttl-decay.md) — `Decay` is the canonical
  TTL. Mechanic modules that spawn temporary entities use `Decay`,
  not parallel TTL components.
- `.claude/rules/components.md`, `.claude/rules/systems.md`,
  `.claude/rules/replacement-as-mutation.md`,
  `.claude/rules/api-contracts.md`,
  `.claude/rules/client-read-only.md` — the path-scoped rules this
  ADR's implementations will live under.
- `CONTEXT.md` — glossary entries for *Module*, *ArenaModule*,
  *Module coordinator system*, *Two-level round/match structure*,
  *Module lifecycle interface*, *Module extensibility principle*,
  *Module → game event channels*, etc.
