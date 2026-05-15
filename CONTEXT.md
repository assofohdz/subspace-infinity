# Subspace Infinity

A JMonkeyEngine 3 multiplayer game built on Zay-ES (ECS), SimEthereal (state sync), Moss (physics + world grid), and Lemur (UI). Single context — the whole repo is one game.

## Language

### Layers

**Api layer**:
The `api/` Gradle module — data classes, interfaces, and immutable config records, with no dependency on the server, client, or modules.
_Avoid_: "shared", "common", "model".

**Server layer**:
The server-side code in `infinity/src/main/java/...` excluding the `client/` package — owns gameplay state, runs systems, the only writer of game-truth components.
_Avoid_: "backend".

**Client layer**:
Read-only observer in the `client/` package — subscribes to entity state, renders, handles input. Never writes a game-truth component; sends intent via RMI.
_Avoid_: "frontend".

### ECS

**System**:
A class extending `AbstractGameSystem` (server) or `BaseAppState` (client) — owns a slice of behaviour. Logic lives here, not in components.
_Avoid_: "service", "manager", "controller".

**Component**:
An immutable `EntityComponent` in `api/src/main/java/infinity/es/...` — pure data attached to an entity. Final fields, no-arg constructor, no setters.
_Avoid_: "model", "data class".

**Spawn system**:
The seam where a `*Config` template (Config-Component Projection, [ADR-0002](docs/adr/0002-config-component-projection.md)) is projected into per-entity components at spawn time. The only place template values cross into the live entity world.
_Avoid_: "factory", "builder".

### Settings

**Settings layer**:
The Groovy-DSL → typed-config pipeline in `infinity/src/main/java/infinity/settings/...` plus the Groovy files under `infinity/zone/`. Loads at boot, polls for live reload, drives `SettingsSystem` and `ConfigRegistry`.
_Avoid_: "config layer" (ambiguous — also means CCP / [ADR-0002](docs/adr/0002-config-component-projection.md) templates).

**Settings host**:
A `GroovySettingsHost<T>` instance — runs the read-evaluate-extract pipeline for one kind of Groovy file. Owns I/O resolution (filesystem-first dev, classpath fallback), security hardening (per-adapter import whitelist), error-to-empty translation, and `resolveOnDisk` for caller-driven mtime polling.
_Avoid_: "settings loader" (load is what an adapter does on top of a host).

**Settings adapter**:
A `GroovySettingsAdapter<T>` implementation — supplies the five things a host needs to evaluate one kind of file: default classpath path, allowed imports, shell-binding setup, result extraction, empty sentinel. Each existing `Groovy*Loader` becomes a thin adapter.
_Avoid_: "settings parser", "loader".

**Fragment**:
A small Groovy file under `infinity/zone/conf/<preset>/` that contributes one or more INI sections (`section('Bullet') { ... }`) to a per-arena settings tree. Loaded via `GroovyFragmentLoader`; included recursively from `arena.groovy` via the `include` directive.
_Avoid_: "preset file", "settings snippet".

**Config-Component Projection (CCP)**:
The template-vs-instance split: `*Config` records in `api/src/main/java/infinity/config/` are templates (one per type, immutable, server-only), projected to per-entity components by spawn systems. Hot-path code reads components only. Formalised by [ADR-0002](docs/adr/0002-config-component-projection.md); the historical "Pattern 4" name is retired.
_Avoid_: confusing this with the **Settings layer** — they overlap (Groovy populates the templates) but the *pattern* is about who reads what at runtime.

### Events

There are three event planes in the codebase, each suited to a different shape of cross-system communication. Pick the right one — they're not interchangeable.

**ECS transient component**:
A short-lived `EntityComponent` written by one system to signal a request to another (e.g. `HealthChange`, `Dead`, `Buff`, `Damage`). Producer adds it; consumer reads via `EntitySet`, applies, and removes. Atomic via Zay-ES `applyChanges()`; arena-scoped via the entity's `ArenaId`. Best for in-tick, in-system game-state changes that need to be queryable.
_Avoid_: "marker component" (some are markers; some carry payload).

**Zone event**:
A Simsilica `EventBus` event whose scope is the whole server / zone — login, account state, arena lifecycle, master-server pings. Lives in `infinity.events.zone.*` or, for session-coupled events, alongside its publisher (e.g. `infinity.net.AccountEvent`). Process-global; listeners are zone-level (lobby UI, account services).
_Avoid_: "meta event", "global event", "server event".

**Arena event**:
A Simsilica `EventBus` event whose scope is a single arena — ship lifecycle, weapon firing, future flag/score/KOTH events. Lives in `infinity.events.arena.*` (e.g. `ShipEvent`). Today the same `EventBus` instance carries both zone and arena events; listeners filter by event type and (when needed) by `ArenaId` in the payload. If arena scoping later becomes load-bearing — e.g. a HUD in arena B starts seeing events from arena A — the runtime split into per-arena bus instances comes with the first concrete consumer that demands it.
_Avoid_: "game event", "in-game event".

### Arena composition (in design)

The vocabulary below is being resolved in an active grilling session — entries land as the design decides them.

**Module** (a.k.a. *ArenaModule*):
A horizontally-scoped, arena-swappable concern with an orthogonal slice of game state — e.g. scoring, win condition, team setup, roster restriction, spawn-placement, shop, and opt-in mechanics (crowns, flags, balls, shrinking-zone, …). *Not* a whole gametype. Gametypes (KOTH, Jackpot, Turf, CTF, Powerball, Speed Zone, Dueling) are *emergent* compositions of modules; humans give the composition a name, the runtime sees only orthogonal modules. The implementing Java interface is `ArenaModule` (renamed from `BaseGameModule`, which has zero implementations today). Decided 2026-05-15.
_Avoid_: "gametype", "game mode", "rule set" for the module itself — those name *compositions*. "BaseGameModule" (the old name) is retired in favour of `ArenaModule` once the design lands.

**Pluggability boundary (Module vs Core system)**:
A concern is a **Module** if its variation across arenas is *behavioural*. A concern is a **Core system** if its variation is purely *numeric* (tuneable via `*Config`). Worked examples: `EnergySystem` is core (regen-toward-max algorithm is universal; `EnergyMax` varies numerically). Scoring is a module (kill-pot vs hold-time-points vs no scoring are different *algorithms*, not different numbers). Boundary can move over time — a core system can be split into "always-on infrastructure" + "behavioural module" when a second arena demands it. Confirmed module-eligible: scoring, win condition, team setup, roster restriction, spawn-placement, respawn policy, round/match structure, shop, plus *opt-in gameplay mechanics* (crowns, carry-flags, static-flags, balls/goals, region-triggers/laps). Decided 2026-05-15.

**Module composition shapes**:
The arena.groovy DSL supports three module shapes — picking the right one per category is part of the module's contract.
- **Single-pick** — exactly one per slot per arena. Categories: `teamSetup`, `roster`, `respawnPolicy`, `roundStructure`, `spawnPlacement`, `shop`. Loader must reject duplicates.
- **Layered** — zero-or-more, additive contributions. Categories: `scoring` (multiple simultaneous scoring contributions can coexist; no implicit base — `kill-points` must be loaded explicitly when desired), `winCondition` (zero-or-more round-terminators; default semantics = OR, first to trigger ends the current round). Order-of-include = order-of-application unless a module declares explicit ordering.
- **Opt-in mechanics** — independently included gameplay objects, each carrying its own placement/config inline. Catalog: Crowns, CarryFlags, StaticFlags, Balls + Goals, RegionTriggers, LapDetector, ShrinkingZone. Mechanics *publish state* that layered scoring/win-condition modules read; missing-mechanic + dependent-scoring = fail-fast at arena load.

Decided 2026-05-15.

**Win condition** *(two-role)*:
A `winCondition` module carries two distinct optional responsibilities; the same module can perform either, both, or neither.
- **Terminator role** — emit a `RoundEndPending` signal when world state says the round should end *right now* (e.g. `first-to-N-points`, `last-team-standing`, `last-crown-standing`). Aggregated OR across all loaded winConditions. The active `roundStructure` may also emit `RoundEndPending` on its own (e.g. `timed(10min)` on timer expiry). Both sources funnel through the same signal.
- **Decider role** — invoked at end-of-round (after a terminator fired) to vote on the winning `Frequency` by reading current world state. Used for arenas where round-end *trigger* and *winner determination* are separable (e.g. Trench: `roundStructure 'timed(10min)'` ends the round; `winCondition 'most-flag-occupancy'` declares the winner from flag-hold state).
Vote aggregation across multiple winConditions: **first-declared wins** (arena.groovy author orders the list). Zero winConditions = no winner (`RoundOutcome.winningFreq = -1`); `roundStructure` must be self-terminating in that case. Decided 2026-05-15.

**Spawn placement (arena-owned, not map-owned)**:
Team / role spawn locations are declared in `arena.groovy` via the `spawnPlacement` module, not embedded in the `.lvl` map file. Maps provide *geometry*; arenas compose *behaviour* including spawn algorithms (random-radius, team-side-points, start-line, named-region-list). This keeps "same map, different gametype" easy (one map can back FFA-Deathmatch and team-Turf arenas with different spawn rules). Decided 2026-05-15.

**Module implementation model**:
Hybrid (Option C): engine ships a fixed Java catalog of module types; `arena.groovy` is pure data referencing types by string identifier with inline kwargs. No code execution in arena.groovy. A future external-author loader will compile Groovy modules dropped under `zone/<author-modules>/` and extend the catalog — same contracts, same DSL, new identifiers. Decided 2026-05-15.
- **Module type** = a Java class implementing `ArenaModule`, declared in the `ModuleCatalog` registry under a string id. One per kind (`"kill-points"`, `"crowns"`, …).
- **Module instance** = per-arena instantiation of a type with bound `*Config`. Each loaded arena gets its own set of module instances (no zone-singleton-with-filtering); cross-arena module instances never share state.
- **Catalog** = explicit `ModuleCatalog` class in `infinity-server/` enumerating all built-in types. Discoverability > add-friction. Future external loader appends to this catalog at server start.

**Module home (api/ vs server/)**:
- `api/`: `ArenaModule` interface, `ModuleCategory` enum, all module `*Config` records, all components modules write or read (`PlayerScore`, `TeamScore`, `RoundTimer`, `FlagOwnership`, `CrownOwnership`, `CarryFlag`, `OutsidePlayArea`, …), all transient components modules emit (`ScoreContribution`, `RoundReset`, `RoundTerminated`, …).
- `infinity-server/`: concrete module implementations, `ModuleCatalog`, `ModuleLoader`, and **coordinator systems** that drain layered module contributions into canonical components (e.g. `ScoreCoordinatorSystem` drains `ScoreContribution` → writes `PlayerScore`, preserving ADR-0001 single-writer discipline).
- `infinity-client/`: never instantiates modules; reads the components modules produce (already covered by client-read-only rule).
Decided 2026-05-15.

**Module coordinator system**:
A core (always-loaded) server-side system that owns the canonical writer for a component fed by layered modules. Scoring is the motivating case: many scoring modules emit `*ScoreChange` transient components; `ScoreCoordinatorSystem` drains them and writes the per-tick consolidated *three-tier* score components: `*RoundScore` (resets on `onRoundEnd`), `*MatchScore` (resets on `onMatchEnd`, omitted when `matchStructure` is `continuous`), `*TotalScore` (in-arena-session accumulating). Reset is *next-tick* via a `ScoreReset(arenaId, scope)` transient emitted in phase 4. This bridges the "layered modules" composition shape with ADR-0001's "one canonical writer per component" rule — modules never write the canonical component directly; they contribute, the coordinator integrates. Decided 2026-05-15.
_Avoid_: "score system" (too narrow — the pattern generalises to any layered-contribution → canonical-component flow).

**ArenaEntity, TeamEntity**:
Per-arena and per-(arena,freq) Zay-ES entities holding aggregate state. `ArenaEntity` carries `ArenaId`, `RoundNumber`, `MatchNumber`, `Arena*Score`; created/destroyed by `ArenaSystem` at arena load/unload. `TeamEntity` carries `ArenaId`, `Frequency`, `Team*Score`, `TeamMemberCount`; created/destroyed by the `teamSetup` module. Different components on these entities have **different canonical writers** (`RoundNumber` ← `roundStructure`; `Arena*Score` ← `ScoreCoordinatorSystem`; `Frequency` on team entity ← `teamSetup` module; etc.) — ADR-0001's "one writer per component" is per-component, not per-entity. Player → team lookup uses the existing `Frequency` component on the player; no `TeamMembership` component. Decided 2026-05-15.

**System-name glossary** (arena composition framework):
Several similarly-named systems collaborate; the table disambiguates.
- **`ArenaSystem`** (`infinity-server`, zone singleton) — arena lifecycle: load/unload/swap; canonical writer of `ArenaEntity` creation/destruction; handles `~loadArena`, `~swapMap`, `~unloadMap` chat.
- **`ArenaManager`** (`api/sim/` interface, injected into `ModuleContext`) — api-side façade for modules to read arena state without depending on `infinity-server`. Thin pass-through over `ArenaSystem`.
- **`ArenaModuleSystem`** (`infinity-server`, zone singleton) — owns `Map<ArenaId, ArenaModuleSet>`; runs tick phases 1+2; orchestrates `ArenaModule` instantiation via `ModuleLoader`.
- **`ScoreCoordinatorSystem`** / **`WinConditionCoordinatorSystem`** (`infinity-server`, zone singletons) — phase 3 canonical writers; drain `*Change` transients into canonical components per ADR-0001.
- **`ArenaLifecycleDispatcherSystem`** (`infinity-server`, zone singleton) — phase 4: fires lifecycle hooks on modules in registration order; aggregates winCondition winner-votes; emits `ScoreReset` for coordinator next-tick drain.
- **`ArenaModule`** (`api/sim/` interface) — per-arena instance of one module type; implements the lifecycle interface; constructed with a `ModuleContext`.
Decided 2026-05-15.

**Map-loaded game-element drain pattern**:
`.lvl`-embedded game elements (turf flags, soccer goals, spawn regions) follow a transient-entity drain that mirrors ADR-0001's `*Change`-entity flow. The map loader creates transient entities carrying a `Spawned` marker (plus `Flag`/`Position`/etc.); the consuming mechanic (`StaticFlag`, `Goals`, …) drains them on `onArenaLoad` by adding canonical state components (`FlagOwnership`, …) and removing the `Spawned` marker. Map owns the entity *lifecycle*; mechanic owns the *state components it adds*. If no mechanic consumes the transients (e.g., a turf map loaded into an FFA arena that doesn't load `StaticFlag`), the entities remain inert markers until map unload. Decided 2026-05-15.

**Two-phase `ModuleLoader`**:
Arena load runs validation entirely before instantiation. Phase 1 (`validate`) is pure and side-effect-free — checks unknown ids, duplicate single-pick, missing `requires:` mechanics, cyclic mechanic dependencies, `*Config` validation errors. Phase 2 (`build`) runs only if validate returned OK; by construction it cannot fail (modulo programming bugs). No rollback path needed; the arena either fails to load with a precise error, or loads cleanly. Decided 2026-05-15.

**Module tick discipline (4-phase, per category role)**:
Each arena tick runs modules in four ordered phases so coordinators always read fully-published state and ADR-0001 single-writer discipline is preserved without per-module bookkeeping.
1. **Mechanics phase** — every loaded `mechanic` module reads last-tick canonical state (positions, freqs, kills) and publishes its mechanic-state components (CrownOwnership, FlagOwnership, BallPossession, OutsidePlayArea, etc.).
2. **Module-contribution phase** — every `scoring` and `winCondition` module reads mechanic state from phase 1 plus game-event signals (kill, death, capture) and emits *transient* contribution components (`ScoreContribution`, `TeamScoreContribution`, `WinConditionTrigger`). Modules never write canonical components.
3. **Coordinator phase** — `ScoreCoordinatorSystem`, `WinConditionCoordinatorSystem`, etc. drain contributions and write canonical components (`PlayerScore`, `TeamScore`, `RoundTimer`). If any winCondition trigger fired, signal the active `roundStructure` module.
4. **Lifecycle dispatch phase** *(only on round-end or match-end tick)* — the framework directly calls lifecycle hooks on all loaded modules (`onRoundEnd`, optionally `onMatchEnd`, then `onMatchStart`/`onRoundStart` for next iteration). All within the same tick.

Within a category, no declaration-order guarantee — contributions are merged with category semantics: **scoring contributions sum; winCondition triggers OR**. Anything order-sensitive must be reshaped as additive contributions (e.g., a "flagger kill multiplier" emits a separate `ScoreContribution` delta, not a transformation of the base contribution). Mechanic-vs-mechanic dependencies declare `requires: [...]` and are topo-sorted at arena load. Decided 2026-05-15.

**Two-level round/match structure**:
Two single-pick categories instead of one. Every arena composes both (degenerate variants exist for arenas that don't need a level).
- `roundStructure` defines "what is one round?" — `continuous`, `timed(minutes)`, `elimination`, `score-threshold(N)`, `crown-reset`, `hold-flag-for(minutes)`, `lap-based(laps)`, etc. Emits `onRoundStart` / `onRoundEnd`. *Continuous* means "no time-based round boundary; rely entirely on `winCondition` terminator-triggers to end rounds" — NOT "rounds never end".
- `matchStructure` defines "how do rounds compose into a match?" — `continuous`, `single-round`, `best-of(N)`, `period-based(N)`, `round-robin`, etc. Emits `onMatchStart` / `onMatchEnd`. *Continuous* means "no match boundary"; `*MatchScore` is omitted entirely under this variant (rather than written-but-never-reset).

The active `roundStructure` decides when to fire `onRoundEnd` based on its own logic plus `winCondition` terminator triggers. The active `matchStructure` decides whether `onRoundEnd` also implies `onMatchEnd`. Decided 2026-05-15.

**Module lifecycle interface (`ArenaModule`)**:
Single unified interface with default-no-op hooks. Every module type implements it; modules override only what they care about.
- `onArenaLoad(arenaId, config)` — arena loaded, module instance bound to its config record.
- `onMatchStart(arenaId)` — emitted by `matchStructure` module (fires once for `continuous`, multiple times for nested-match shapes).
- `onRoundStart(arenaId, roundNumber)` — emitted by `roundStructure` module.
- `onRoundEnd(arenaId, roundNumber, RoundOutcome)` — emitted by `roundStructure` module; mechanics revert their state, round-local counters reset.
- `onMatchEnd(arenaId, MatchOutcome)` — emitted by `matchStructure` module after the final round.
- `onArenaUnload(arenaId)` — arena tearing down; **mechanics MUST remove the components/entities they own** (rule enforced for hot-reload safety per Q7-β).
- `onConfigReloaded(newConfig)` — config-diff hot-reload landed (per Q7-α).

Dispatch is direct method-call by the framework's module loop in phase 4, not via EventBus. Atomicity within a tick is preserved. Decided 2026-05-15.

**Module hot-reload model**:
Three flavours, all in v1 scope:
- **(α) Config-diff hot-reload** — Groovy file mtime change → modified kwargs → `onConfigReloaded(newConfig)`. No teardown, no player disruption. Direct extension of existing settings hot-reload.
- **(β) Module-set-diff hot-reload** — arena.groovy adds/removes modules → diff computed → new modules get `onArenaLoad`, removed modules get `onArenaUnload`. **Cleanup contract**: every mechanic module's `onArenaUnload` MUST remove the components/entities it owns; orphaned state on removal is the failure mode to prevent. Same contract is the test bed for the future external-Groovy modules loader.
- **(γ) Map swap** — separate concern; existing `~swapMap` flow. If arena.groovy's `map '…'` line changes during hot-reload, map swap fires as a consequence; modules are *not* torn down (state survives the swap; `spawnPlacement` re-runs to place players on the new map).
Decided 2026-05-15.

**State persistence (v1)**:
Per-arena module state is in-memory only. Server restart = clean slate (scores, round timers, flag positions, match wins all reset). Module configs are read from arena.groovy at boot — runtime never writes back to groovy. Durable persistence is deferred to a follow-up ADR pending a real consumer (league play, ranked stats). Decided 2026-05-15.

**Module extensibility principle**:
*"The game should be easily extensible in a safe frame. Wacky modules are fine."* The **safe frame** is the architectural commitments: per-arena instances, lifecycle interface (`ArenaModule`), 4-phase tick with coordinators preserving ADR-0001 single-writer discipline, fail-fast loader, cleanup contract on unload. **Wacky content** is what authors put inside that frame — any algorithm, any subscription, any combination of services. The framework hands modules powerful tools (`EntityData`, the per-arena `EventBus`, `PhysicsManager`, `ChatHostedPoster`, `AccountManager`, `ArenaManager`, `TimeManager`) at instantiation and trusts them to use those tools sensibly. Decided 2026-05-15.

**Module → game event channels**:
Modules consume game events via the same channels as core systems — no extra hooks on `ArenaModule`. Two pre-existing channels cover this:
- **ECS transient components** (per CONTEXT.md Events section) — for in-tick, atomic, queryable state changes (e.g. a `KillEvent` transient component drained by scoring modules in phase 2).
- **Arena event subscription** — the per-arena `EventBus` is injected into each module at instantiation; modules call `eventBus.addListener(EventType, listener)` in `onArenaLoad` and remove in `onArenaUnload`. Standard listener pattern, scoped to the module's arena.

`ArenaModule` itself stays minimal — *only* lifecycle hooks (load/match-start/round-start/round-end/match-end/unload/config-reload). Game-event subscriptions are not first-class methods on the interface; they are *uses* of the injected EventBus service. Decided 2026-05-15.

**Loader fail-fast diagnostics**:
The module loader rejects an arena at load time (not at first tick) for: unknown module identifier; duplicate single-pick category; required mechanic missing for a scoring/winCondition; cyclic mechanic `requires:` graph; `*Config` record validation error. Soft-degrade is explicitly rejected — a silently-broken gametype is much harder to debug than an arena that refuses to start with a clear message. Decided 2026-05-15.

**Arena.groovy DSL shape**:
- **Top-level statements**, no wrapping `arena { }` block. Matches today's `zone.groovy` and existing arena files. Zero migration cost for existing arenas.
- **Settings fragments vs module presets** are *separate file pipelines*: `includeFragment` (existing) contributes Subspace-canonical settings (`section('Ship') { ... }`); a new `usePreset` pulls in a bundle of module statements (`scoring '…'; mechanic '…'`). A single fragment file may *not* mix the two — settings fragments live where they live today (`zone/conf/<preset>/`); module presets live in a new tier (location TBD, likely `zone/presets/<name>/`).
- **No `extends`** — only flat composition. Arenas list their `usePreset` calls and their own statements; resolution is declaration-order.
- **Override semantics**: single-pick categories let a later declaration *replace* (preset says `teamSetup 'ffa-private'`, arena says `teamSetup '2-fixed-teams'`, arena wins). Layered categories *append*; **no remove operator** — if a preset hands you the wrong bundle, skip the preset and compose primitives.
Decided 2026-05-15.

## Relationships

- A **Settings host** is generic over result type `T`; one host class serves all adapters.
- A **Settings adapter** is a small class implementing `GroovySettingsAdapter<T>`; one adapter per kind of Groovy file (zone, arena, ship, fragment, future modules).
- A **Spawn system** reads `*Config` templates loaded by an **adapter** and projects them onto **components**.
- A **System** never reads `*Config` templates on the hot path — components only.
- A request to mutate game state (damage, heal, kill) flows through an **ECS transient component**, never an **arena event** — events notify, components request.
- An **arena event** signals "X just happened in this arena"; a **zone event** signals "X just happened on this server."

## Example dialogue

> **Dev:** "Where does ship-stat tuning go — host, adapter, or somewhere else?"
> **Author:** "The Groovy file lives in `infinity/zone/conf/<preset>/ships.groovy`. The `GroovyShipAdapter` parses the DSL and produces `ShipConfig` records. The **host** runs the evaluation pipeline; the adapter only knows DSL semantics. At spawn time, `ShipSpawnSystem` reads the template and writes per-entity components."
>
> **Dev:** "And if I want a hot-path tweak — say, change a thrust value mid-game?"
> **Author:** "Don't read the template on the hot path. Edit the Groovy file, the host re-evaluates on mtime change, the spawn system re-projects on next spawn or via `ShipSpawnSystem.reproject(...)` for live tuning."
>
> **Dev:** "When a ship gets killed, do I publish a `ShipDestroyed` arena event or write a `Dead` component?"
> **Author:** "Both, but they answer different questions. The `Dead` component *requests* the kill — `DeathSystem` reads it, applies the kill, removes it. That's the request channel. The arena event *announces* the kill once it's processed — for HUD updates, score logging, replay, anything cross-cutting that doesn't need to be queryable. Don't try to make events queryable or components broadcast: each tool has its shape."

## Flagged ambiguities

- "config" was used to mean both **Config-Component Projection** *templates* and arena-tier *settings*. Resolved: the templates are CCP (`*Config` records, formalised by [ADR-0002](docs/adr/0002-config-component-projection.md)); the file pipeline is the **Settings layer** ([ADR-0004](docs/adr/0004-settings-pipeline.md)). The historical "Pattern 4" name is retired.
- "loader" was used for both the host (the I/O+evaluation pipeline) and the adapter (the DSL semantics). Resolved: **host** runs the pipeline, **adapter** supplies the per-kind specifics. Existing `Groovy*Loader` classnames will become thin facades wrapping `host.load(adapter)`.
- "event" was used to mean three different things — a transient ECS component, an arena-scope `EventBus` event, and a zone-scope `EventBus` event. Resolved: see the three definitions in **Events** above. Events that turn out to need queryability or atomicity belong as transient components, not bus events.
