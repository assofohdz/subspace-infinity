# Arena modules framework — implementation PRD

Status: ready-for-agent (Phase 1); ready-for-human (later phases)
Cross-ref: implements [ADR-0008](../../docs/adr/0008-arena-composition-and-modules.md); upstream of [`subspace-module-archetypes/PRD.md`](../subspace-module-archetypes/PRD.md) (content) and [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) (Phase 2 — external-author extension).
Labels: area:modules, area:arena, framework

## Background

[ADR-0008](../../docs/adr/0008-arena-composition-and-modules.md) landed the design for arena composition: arenas are made of horizontal `ArenaModule` instances (single-pick / layered / opt-in mechanic shapes); gametypes are emergent compositions; coordinators bridge layered modules to ADR-0001's one-canonical-writer rule; a 4-phase tick discipline orchestrates them; lifecycle hooks dispatch round/match-end. The ADR is the **decision shape**; this PRD is the **implementation sketch** and **slice plan**.

The previous `groovy-module-loader/PRD.md` (now superseded for v1) was scoped to a Groovy compile-on-load extension surface. That extension surface still has a future home (Phase 3 of this PRD), but the *core framework* — Java module classes, the catalog, the registry system, coordinators — is what unblocks the first consumer (an FFA-Deathmatch arena per ADR-0008 Open Work). Ship the framework first; the Groovy loader sits on top.

## Scope

### In scope (Phase 1 — this PRD's primary delivery)

- `ArenaModule` interface in `api/src/main/java/infinity/sim/` (rename of existing `BaseGameModule`; zero implementations today, so the rename is free).
- `ModuleCategory` enum + `RoundOutcome` / `MatchOutcome` records in `api/`.
- The Java module catalog (`ModuleCatalog` in `infinity-server/`) with an initial set of concrete module classes — only enough to ship the first consumer.
- `ArenaModuleSet` value record + `ArenaModuleSystem` (registry-owning system).
- `ModuleLoader` helper (reads arena.groovy module statements → builds an `ArenaModuleSet`).
- `ScoreCoordinatorSystem` + `WinConditionCoordinatorSystem` (always-loaded canonical writers per ADR-0001).
- `ArenaLifecycleDispatcherSystem` (phase-4 hook dispatcher).
- `arena.groovy` DSL extension: `scoring`, `winCondition`, `mechanic`, `teamSetup`, `roster`, `respawnPolicy`, `roundStructure`, `matchStructure`, `spawnPlacement`, `shop` top-level statements (parsed by `GroovyArenaLoader` into a new `ArenaConfig.modules()` field, per ADR-0008).
- Loader fail-fast diagnostics (unknown id, duplicate single-pick, missing required mechanic, cyclic deps, `*Config` validation error).
- First consumer: a single FFA-Deathmatch arena (`zone/arenas/ffa/arena.groovy`) demonstrating the framework end to end.

### In scope (Phase 2 — `usePreset` bundles + module-set hot-reload)

- `usePreset 'name'` directive in arena.groovy. Module presets live in `zone/presets/<name>/<name>.groovy`.
- File-watcher integration for module-set diffs (per ADR-0008-β cleanup contract).
- A second consumer: KOTH arena (validates compositional reuse with the FFA's `kill-points` scoring + new `crowns` mechanic + new `last-crown-standing` winCondition).

### In scope (Phase 3 — external-author Groovy module loader)

- The content previously held in [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md): compile-on-load Groovy modules under `zone/<author-modules>/`, manifest DSL, security customisers, classloader isolation, manifest-hash handshake. Appends to the same `ModuleCatalog` the Phase-1 framework uses — no new framework surface needed.

### Out of scope (deferred to follow-on ADRs / PRDs)

- Durable state persistence across server restart (per ADR-0008 Open Work; in-memory only for now).
- AND-composed win conditions (`composite-and` module type; OR semantics ships first).
- Cross-arena module communication (zone-event concern per ADR-0008).
- Sandbox-against-deliberate-malice for Phase-3 Groovy modules (per existing `groovy-module-loader/PRD.md`).

## Implementation pattern

Mirrors the existing `ConfigRegistry` / `ConfigRegistrySystem` shape (data-class registry held inside an owning system). Same shape; different content.

### Value types (api/, no logic)

```java
// api/src/main/java/infinity/modules/
public record ArenaModuleSet(
    TeamSetupModule teamSetup,                  // single-pick (exactly one)
    RosterModule roster,
    RespawnPolicyModule respawnPolicy,
    RoundStructureModule roundStructure,
    MatchStructureModule matchStructure,
    SpawnPlacementModule spawnPlacement,
    ShopModule shop,
    List<ScoringModule> scoring,                // layered (zero or more)
    List<WinConditionModule> winConditions,
    Map<String, MechanicModule> mechanics       // opt-in, keyed by module type id
) {
  public static final ArenaModuleSet EMPTY = /* … */;
}

public enum ModuleCategory {
  TEAM_SETUP, ROSTER, RESPAWN_POLICY, ROUND_STRUCTURE, MATCH_STRUCTURE,
  SPAWN_PLACEMENT, SHOP, SCORING, WIN_CONDITION, MECHANIC
}

// Carries everything a module might need at construction time.
public record ModuleContext(
    ArenaId arenaId,
    EntityData ed,
    EventBus arenaEventBus,         // per-arena per ADR-0008 decision
    PhysicsManager physics,
    ChatHostedPoster chat,
    AccountManager accounts,
    ArenaManager arenas,
    TimeManager time,
    SettingsSystem settings
) {}

public record ModuleSpec(String moduleId, Map<String, Object> kwargs) {}

// What GroovyArenaLoader produces from arena.groovy's module statements.
public record ArenaModuleDeclarations(
    Optional<ModuleSpec> teamSetup,
    Optional<ModuleSpec> roster,
    Optional<ModuleSpec> respawnPolicy,
    Optional<ModuleSpec> roundStructure,
    Optional<ModuleSpec> matchStructure,
    Optional<ModuleSpec> spawnPlacement,
    Optional<ModuleSpec> shop,
    List<ModuleSpec> scoring,
    List<ModuleSpec> winConditions,
    Map<String, ModuleSpec> mechanics
) {}

public record RoundOutcome(
    int winningFreq,                    // -1 = no winner (UNDECIDED)
    String triggeringId,                // module id that triggered termination
    Map<Integer, Long> teamScores,      // freq -> final round-local score
    Map<String, Object> details         // free-form for module-specific data
) {}

public record MatchOutcome(
    int winningFreq,
    Map<Integer, Long> teamScores,      // freq -> final match-local score
    Map<String, Object> details
) {}

public record WinnerDeclaration(
    int winningFreq,                    // -1 = UNDECIDED (this winCondition abstains)
    String reason
) {
  public static final WinnerDeclaration UNDECIDED = new WinnerDeclaration(-1, "");
}
```

`ArenaModule` is the interface every concrete module implements; category-specific interfaces (`ScoringModule`, `MechanicModule`, `WinConditionModule`, …) extend it with optional category-specific contracts. The non-trivial extension is `WinConditionModule`'s two-role interface (per ADR-0008):

```java
public interface WinConditionModule extends ArenaModule {
  default Optional<RoundEndTrigger> checkTermination(ArenaId arenaId) { return Optional.empty(); }
  WinnerDeclaration declareWinner(ArenaId arenaId);
}
```

### Helpers (infinity-server/, not registered as systems)

**`ModuleCatalog`** — static-ish registry of available module types. Authored as an explicit Java class, not an SPI scan (ADR-0008 decision):

```java
// infinity-server/src/main/java/infinity/modules/
public final class ModuleCatalog {
  private static final Map<String, ModuleDescriptor> CATALOG = Map.of(
    "kill-points",        new ModuleDescriptor(KillPointsScoring.class, KillPointsConfig.class, SCORING),
    "ffa-private-freqs",  new ModuleDescriptor(FfaPrivateFreqsTeamSetup.class, FfaTeamSetupConfig.class, TEAM_SETUP),
    "all-ships",          new ModuleDescriptor(AllShipsRoster.class, AllShipsRosterConfig.class, ROSTER),
    "instant-respawn",    new ModuleDescriptor(InstantRespawn.class, InstantRespawnConfig.class, RESPAWN_POLICY),
    "continuous",         new ModuleDescriptor(ContinuousRound.class, ContinuousConfig.class, ROUND_STRUCTURE),
    /* …etc… */
  );
  public static ModuleDescriptor descriptor(String moduleId) { … }
  public static Set<String> allIds() { … }
}
```

The catalog is **the** answer to "what modules can I reference in arena.groovy?". One file, greppable. The future external Groovy loader appends to this catalog at server start (Phase 3).

**`ModuleLoader`** — stateless, **two-phase** helper. Validation runs entirely before instantiation, so a failed load never leaves half-instantiated modules behind (no rollback path).

```java
public final class ModuleLoader {
  // Phase 1 — pure, no side effects, no instantiation. Returns either
  // a clean OK or a list of validation errors.
  public ValidationResult validate(ArenaConfig arenaConfig);

  // Phase 2 — runs ONLY if validate() returned OK. By construction
  // cannot fail (modulo programming bugs).
  public ArenaModuleSet build(ArenaConfig arenaConfig, ModuleContext context);
}

public record ValidationResult(
    boolean ok,
    List<String> errors       // human-readable, surfaced to operator on arena-load
) {}
```

**Phase 1 (`validate`) checks:**
1. Every `moduleId` exists in `ModuleCatalog`.
2. No single-pick category has more than one declaration.
3. Every `requires:` mechanic dep on every loaded scoring/winCondition is satisfied by an actually-loaded mechanic.
4. The mechanic `requires:` graph is acyclic.
5. Each declaration's kwargs validate against the descriptor's `configType` (compact-constructor validation; e.g., `perKill: -50` rejected).

**Phase 2 (`build`) steps:**
1. Walk declarations in topological order (mechanics first, by `requires:` DAG; then single-pick categories; then layered).
2. For each declaration: look up `ModuleDescriptor` in `ModuleCatalog`, bind kwargs to `*Config` record, instantiate via single-arg `ModuleContext` constructor.
3. Compose into an `ArenaModuleSet` and return.

Caller (`ArenaModuleSystem.onArenaLoad`) handles the two phases:

```java
var result = loader.validate(arenaConfig);
if (!result.ok()) {
  arenaSystem.refuseLoad(arenaId, result.errors());     // operator sees precise error
  return;
}
var moduleSet = loader.build(arenaConfig, context);
modulesByArena.put(arenaId, moduleSet);
// then: onArenaLoad, onMatchStart, onRoundStart on each module
```

### Systems (infinity-server/, registered with `GameSystemManager`)

**`ArenaModuleSystem`** — `BaseInfinitySystem`. Owns `Map<ArenaId, ArenaModuleSet>`. Hooks into `ArenaSystem`'s arena-load/unload events. On load, calls `ModuleLoader.load(arenaConfig, services)` and installs the result; on unload, iterates modules in reverse to call `onArenaUnload`. Runs **phases 1 + 2** in `update(tick)`:

```java
public void update(SimTime time) {
  for (var entry : modulesByArena.entrySet()) {
    var arenaId = entry.getKey();
    var set = entry.getValue();
    // Phase 1: mechanics publish state
    for (var mechanic : set.mechanics().values()) {
      mechanic.tickMechanic(arenaId, time);
    }
    // Phase 2: scoring + winCondition emit transient contributions
    for (var scoring : set.scoring()) {
      scoring.tickContributions(arenaId, time);
    }
    for (var winCond : set.winConditions()) {
      winCond.tickTriggers(arenaId, time);
    }
  }
}
```

**`ScoreCoordinatorSystem`** — `BaseInfinitySystem`. Canonical writer of all three score tiers on all three scopes (per ADR-0001): `PlayerRoundScore` / `PlayerMatchScore` / `PlayerTotalScore`, `TeamRoundScore` / `TeamMatchScore` / `TeamTotalScore`, `ArenaRoundScore` / `ArenaMatchScore` / `ArenaTotalScore`. Drains `PlayerScoreChange` / `TeamScoreChange` / `ArenaScoreChange` transient entities each tick; applies each delta to ALL three tiers in one drain pass. Also drains `ScoreReset(arenaId, scope)` transients (emitted by dispatcher in phase 4 of prior tick) and zeros the appropriate tier components. **`*MatchScore` is omitted** for arenas whose `matchStructure` is `continuous` (no match-end will ever fire). Always loaded, regardless of which scoring modules are active in any arena.

**`WinConditionCoordinatorSystem`** — `BaseInfinitySystem`. Drains `RoundEndPending` transient component entities, ORs them per arena. If any fires for arena X, sets a per-arena pending-round-end marker that the lifecycle dispatcher consumes in phase 4 (same tick).

**`ArenaLifecycleDispatcherSystem`** — `BaseInfinitySystem`. Runs **phase 4**. For each arena with a pending-round-end this tick:
1. Calls `declareWinner()` on each loaded `winCondition` module; aggregates votes (**first non-`UNDECIDED` wins** for v1) into `RoundOutcome.winningFreq`.
2. Fires `onRoundEnd(arenaId, roundNumber, outcome)` on every module in registration order.
3. Asks the active `matchStructure` whether the match also ends. If yes: fires `onMatchEnd(arenaId, matchOutcome)`.
4. Emits `ScoreReset(arenaId, scope=ROUND)` transient component (and `scope=MATCH` if match ended). The score coordinator drains these next tick.
5. Fires `onMatchStart` (if a new match should start) + `onRoundStart(arenaId, roundNumber+1)` for the next round iteration.
6. Clears the pending-round-end marker.

**Score reset cascade ordering** on a match-end tick:
- Tick N, phase 4: dispatcher fires `onRoundEnd` → matchStructure detects match ending → `onMatchEnd` fires → dispatcher emits BOTH `ScoreReset(ROUND)` and `ScoreReset(MATCH)` → dispatcher fires `onMatchStart` + `onRoundStart` for next match.
- Tick N+1, phase 3: coordinator drains both `ScoreReset` transients in one pass; zeros `*RoundScore` and `*MatchScore` for the arena.

This means scores on the HUD show the *post-reset* values one tick after `onRoundEnd` fires. Accepted narrowing of same-tick atomicity in exchange for keeping the coordinator's writer path uniform with every other drain.

### Canonical-writer ledger

ADR-0001 requires exactly one writer per component. The new components introduced by this framework, each mapped to its writer and what drives the write:

| Component | Canonical writer | Drives the write |
|---|---|---|
| `ArenaEntity` lifecycle, `ArenaId` (on arena entity) | `ArenaSystem` | Arena load/unload |
| `RoundNumber` (on arena entity) | active `roundStructure` module | Increment in `onRoundStart` |
| `MatchNumber` (on arena entity) | active `matchStructure` module | Increment in `onMatchStart` |
| `TeamEntity` lifecycle, `Frequency` (on team entity), `TeamMemberCount` | active `teamSetup` module | Arena load + player freq-change events |
| `PlayerRoundScore`, `PlayerMatchScore`, `PlayerTotalScore` | `ScoreCoordinatorSystem` | Drains `PlayerScoreChange` transients (+ `ScoreReset` for zero) |
| `TeamRoundScore`, `TeamMatchScore`, `TeamTotalScore` | `ScoreCoordinatorSystem` | Drains `TeamScoreChange` transients |
| `ArenaRoundScore`, `ArenaMatchScore`, `ArenaTotalScore` | `ScoreCoordinatorSystem` | Drains `ArenaScoreChange` transients |
| `FlagOwnership` (on map-loaded flag entities) | `StaticFlag` mechanic | Drain `Spawned`-marked flags + freq-touch events |
| `FlagCarrier`, carry-flag pickup state | `CarryFlag` mechanic | Pickup/drop events |
| `CrownOwnership` (on player entities) | `Crowns` mechanic | Spawn at `onRoundStart`; transfer on death |
| `BallPossession` (on ball entities) | `Balls` mechanic | Touch events |
| `OutsidePlayArea` (on player entities) | `ShrinkingZone` mechanic | Per-tick position check vs current zone radius |
| `RoundEndPending` transient | `WinConditionCoordinatorSystem` | Drains `RoundEndTrigger` from terminator-role winCondition modules + roundStructure |
| `ScoreReset(arenaId, scope)` transient | `ArenaLifecycleDispatcherSystem` | Emit at phase 4 of round/match-end tick |

`PlayerScoreChange` / `TeamScoreChange` / `ArenaScoreChange` are *transient* components — emitted by any scoring module that targets the corresponding scope; the coordinator drains and destroys per the ADR-0001 one-shot pattern. The coordinator is the only system that *writes* the canonical `*Score` components; modules only emit `*Change` transients.

### System registration order

Phase ordering is enforced by registration order — same trick ADR-0001 uses for "register canonical writers before `DecaySystem`".

```
1. ArenaSystem                          // existing — arena lifecycle, fires load/unload
2. ConfigRegistrySystem                 // existing — typed *Config records per arena
3. ArenaModuleSystem                    // NEW — phases 1 + 2
4. ScoreCoordinatorSystem               // NEW — phase 3 (canonical writer of PlayerScore)
5. WinConditionCoordinatorSystem        // NEW — phase 3 (signals roundStructure)
6. ArenaLifecycleDispatcherSystem       // NEW — phase 4 (round/match-end dispatch)
7. … existing systems (Decay, etc.)
```

Within one server tick, systems run in this order, so:
- Phase 1+2 contributions are fully published by the time coordinators read them.
- Coordinator-emitted `RoundEndPending` markers are visible to the dispatcher in the same tick.
- All four phases execute in one server tick, preserving ADR-0008's atomicity claim.

### Hot-reload integration

The existing `ArenaSystem`+`ConfigRegistrySystem` mtime-poll file-watcher already handles arena.groovy reload. Extend it to:

1. **Config-diff (Phase 1):** if only kwargs changed on an existing module, call `onConfigReloaded(newConfig)` on that module instance. No teardown.
2. **Module-set-diff (Phase 2):** if the set of declared modules changed, compute the diff. New modules: instantiate + `onArenaLoad`. Removed modules: `onArenaUnload` (which MUST clean up owned components/entities per ADR-0008-β contract). Reconfigured modules: `onConfigReloaded`.
3. **Map swap:** if only `map '…'` changed, fire `~swapMap`-equivalent without touching the module set. Modules' `spawnPlacement` re-runs to re-place players on the new map.

### Module → service injection

At instantiation, `ModuleLoader` hands each module:

- `EntityData` for the arena.
- The per-arena `EventBus` (modules subscribe to arena events in `onArenaLoad`, unsubscribe in `onArenaUnload`).
- The five existing services from prior `BaseGameModule`: `PhysicsManager`, `ChatHostedPoster`, `AccountManager`, `ArenaManager`, `TimeManager`.
- Any future service the catalog declares as a module dependency.

Per ADR-0008's extensibility principle ("safe frame, wacky content"), modules get the tools and decide how to use them. Game-event hooks are not first-class methods on `ArenaModule` — modules use `EventBus.addListener` for `ShipEvent`, `KillEvent`, etc.

## Slice plan

Each slice is independently mergeable and corresponds to one or more issues.

### Slice F1 — Framework skeleton

- Rename `BaseGameModule` → `ArenaModule` in `api/`. Replace the existing fields with the unified lifecycle interface from ADR-0008.
- Add `ModuleCategory` enum, `RoundOutcome` / `MatchOutcome` records, `ArenaModuleSet` record in `api/`.
- Add the empty `ModuleCatalog` class, `ModuleLoader` helper, and the four NEW systems in `infinity-server/` (no concrete modules yet; the catalog is empty, the systems no-op when no modules are loaded).
- Register the four systems in `GameServer` in the order above.
- Wire `ArenaModuleSystem` to subscribe to `ArenaSystem` load/unload events (or poll via shared `ArenaRecord` state).

Acceptance: `./gradlew build` clean; existing arenas continue to load and play exactly as today (no module statements yet, no behavioural change).

### Slice F2 — FFA-Deathmatch first consumer

Implements the minimum module catalog to ship one arena composition end to end:

- `KillPointsScoring` — emits `PlayerScoreChange` on kill (`PerKill` configurable). Listener on per-arena `EventBus` for kill events.
- `FfaPrivateFreqsTeamSetup` — assigns each player their own freq on spawn; lazily creates per-freq `TeamEntity` per player; canonical writer of `Frequency`-on-team-entity and `TeamMemberCount`.
- `AllShipsRoster` — no roster restriction.
- `InstantRespawn` — drains death components, re-spawns immediately.
- `TimedRoundStructure` — terminator: emits `RoundEndPending` after N minutes.
- `ContinuousMatchStructure` — degenerate: emits `onMatchStart` at arena-load; never fires `onMatchEnd` until arena-unload; `*MatchScore` components are omitted under this matchStructure.
- `RandomRadiusSpawnPlacement` — places players within a radius of a center point (same algorithm as today's `spawn.groovy` fragment, module-owned).
- `FlatShop` — basic shop (matches Subspace canon defaults).
- `HighestScoreWinCondition` — decider only (no terminator role): `declareWinner()` reads `TeamRoundScore` and returns highest freq.
- `ScoreCoordinatorSystem` writes `PlayerRoundScore` / `PlayerMatchScore` / `PlayerTotalScore` (and team / arena tiers) from drained `*Change` transients. `*MatchScore` omitted because `matchStructure 'continuous'`.

Plus: `GroovyArenaLoader` accepts the new `arena.groovy` module statements; `ArenaConfig` gains an `ArenaModuleDeclarations` field via `modules()` accessor.

Plus: `zone/arenas/ffa/arena.groovy` composes the above into a playable FFA arena. `~loadArena ffa` works. `PlayerRoundScore` + `TeamRoundScore` update on HUD via `watchEntity` (clients watch the player + team entities). Round ends after the configured timer with a chat announcement; round score resets next tick.

Acceptance: manually launch + play FFA arena; kills award points; round ends after timer; scores reset; new round starts.

### Slice F3 — Cleanup contract tests + module-set hot-reload (ADR-0008-β)

- Add `ArenaModuleContractTest` to `infinity-server/src/test/java/`: instantiates each catalog module, runs `onArenaUnload`, asserts the arena's entity set is clean of module-owned components.
- Extend the arena-groovy file-watcher to compute module-set diffs and apply per `ModuleLoader`.
- Test: edit `ffa/arena.groovy` while the arena is running (add `scoring 'bonus-points'` — a no-op-but-loaded second scoring module). Verify the running arena picks it up without restart.

### Slice F4 — Trench (Turf-shaped) — second consumer + legacy rip-out

Adds the StaticFlag mechanic, two-team setup, and Trench-canonical winCondition. Rips out the legacy `FrequencySystem` flag-touch handler in the same slice (the new mechanic supersedes it).

New modules:
- `StaticFlag` mechanic — drains map-loaded transient flag entities (per the map-loaded game-element drain pattern); canonical writer of `FlagOwnership`. Subscribes to per-arena flag-touch events; updates `FlagOwnership` on touch.
- `TwoFixedTeamsTeamSetup` — eagerly creates 2 `TeamEntity` instances at `onArenaLoad`. Assigns freqs (0 + 1) to joining players via emit `FrequencyChange` (drained by existing `FrequencySystem`, which retains its `Frequency`-canonical-writer role minus the flag-touch handler).
- `FlagHoldTimeScoring` — per-tick: for each flag, looks up owning team, emits `TeamScoreChange(freq, +N)` for that team. `PerSecondPerFlag` configurable.
- `MostFlagOccupancyWinCondition` — decider only (no terminator). `declareWinner()` reads cumulative flag-hold time per team across the round and returns the highest. (Requires tracking flag-hold ticks per team across the round; stored on the mechanic's state or on the team entity.)
- (Reused from F2) `TimedRoundStructure 'timed(minutes: 10)'` — terminates the round on timer.

Plus: rip out `FrequencySystem.flagTouchHandler` and `MapFactory.createTurfStationaryFlag` legacy path. Migrate `deva/arena.groovy` to the new module pipeline in the same change (deva composes `mechanic 'staticFlags'` reading the same flag positions deva used today).

Plus: `zone/arenas/trench/arena.groovy` composes:
```groovy
arena {
  map 'trench/your-map.lvl'
  teamSetup      '2-fixed-teams'
  roster         'all-ships'
  respawnPolicy  'instant'
  roundStructure 'timed', minutes: 10
  matchStructure 'continuous'
  spawnPlacement 'random-radius', center: [512, 512], radius: 200
  shop           'flat'

  mechanic 'staticFlags'      // consumes map-loaded flag entities

  scoring 'kill-points', perKill: 100
  scoring 'flag-hold-time', perSecondPerFlag: 5

  winCondition 'most-flag-occupancy'
}
```

Acceptance: manually launch + play Trench arena. Flags are owned by the team last touching them. Both teams accumulate flag-hold time. Kill points accumulate normally. After 10 minutes the round ends; the team with more flag-hold time wins; scores reset; new round starts.

### Slice F5 — KOTH third consumer (validates two-role winCondition + opt-in mechanic)

Adds opt-in mechanic + layered scoring + lockout-respawn variants. Validates the two-role winCondition design (terminator + decider together).

- `Crowns` mechanic — spawns crown entities at `onRoundStart` (one per active player); transfers `CrownOwnership` on death by crown-holder.
- `CrownKillBonus` scoring — additive contribution; emits extra `PlayerScoreChange` when killer holds a crown.
- `LockoutNoCrownRespawnPolicy` — respawn requires arena to have ≥1 crown-holder remaining; otherwise queue.
- `LastCrownStandingWinCondition` — **both roles**: terminator emits `RoundEndPending` when crown-count drops to 1; decider returns the surviving crown-holder's freq.
- `CrownResetRoundStructure` — single round per crown distribution; despawns crowns on `onRoundEnd`; respawns on next `onRoundStart`.

Plus: `zone/arenas/koth/arena.groovy` composing the above with the existing FFA modules.

Acceptance: KOTH plays through a round end-to-end; crown drops on death; round resets; new crowns distributed.

### Slice F6+ — Module preset bundles + remaining catalog

- `usePreset` directive support; `zone/presets/trench-base/trench-base.groovy` as the first preset (extract the Trench composition from F4).
- Add remaining gametype modules per `subspace-module-archetypes/PRD.md` (CTF, Powerball, Speed Zone, Dueling, Jackpot variants). One slice per gametype.

### Phase 3 — external-author Groovy module loader (separate PRD)

The existing [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) covers this work. With the Phase-1 framework landed, the Groovy loader's job is reduced to: compile Groovy classes implementing `ArenaModule`, register them in `ModuleCatalog`, the rest of the framework just works. The loader is now substantially smaller than the originally-scoped work — most of the heavy lifting moved into Phase 1.

## Test strategy

- **Unit tests per module** (`*Test.java` in `infinity-server/src/test/`): each concrete module class gets a test verifying its phase logic (a `KillPointsScoringTest` asserts that processing a `KillEvent` emits a `ScoreContribution` of the configured `perKill`).
- **Coordinator drain tests**: `ScoreCoordinatorSystemTest` verifies multi-source summing (two scoring modules both emit `ScoreContribution(50)` for the same target → `PlayerScore` is `100`).
- **Cleanup contract test** (Slice F3): one parameterised test per catalog entry verifies `onArenaUnload` leaves the arena's entity world free of that module's state components.
- **Loader fail-fast tests**: `ModuleLoaderTest` verifies each fail-fast posture (unknown id, duplicate single-pick, missing dep, cyclic dep, invalid config).
- **Integration smoke**: an arena-load harness that walks a fixture arena.groovy through the full pipeline, asserts the resulting `ArenaModuleSet` matches expectations.

Manual launch remains the only end-to-end gameplay verification per the existing test gap (memory: [[project-spawn-projection-test-gap]]). The catalog tests + cleanup contract test minimize the manual-test surface to "does the gameplay feel right" rather than "does the framework work at all".

## Dependencies / consumers

**Upstream (this PRD depends on):**
- ADR-0008 (the design).
- ADR-0001 (canonical writer rule — coordinators preserve it).
- ADR-0002 (CCP — module `*Config` records follow the same template/instance split).
- ADR-0004 (settings pipeline — module hot-reload extends the existing mtime-poll machinery).
- ADR-0005 (api/server/client layering — `ArenaModule` interface in api/, impls in server/).

**Downstream (this PRD unblocks):**
- [`subspace-module-archetypes/PRD.md`](../subspace-module-archetypes/PRD.md) — the per-gametype module *content*. Once the framework is in (Slices F1-F2), each archetype becomes a Slice in the same series (F4 KOTH, F5 Turf, F6 CTF, etc.).
- [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) — the external-author Phase 3. With the framework in, this loader's job shrinks substantially.
- [`loaded-modules-list/PRD.md`](../loaded-modules-list/PRD.md) — client HUD showing loaded modules. Reads from `ArenaModuleSystem` via a new lightweight RMI surface.

## Remaining implementation questions

These are detail-level — not blockers for landing the framework, but worth recording:

- **`ArenaModuleSystem` ↔ `ArenaSystem` integration shape.** Two patterns work: (a) `ArenaSystem` emits a `ArenaLoaded` / `ArenaUnloaded` arena event that `ArenaModuleSystem` subscribes to; (b) `ArenaModuleSystem` polls `ArenaSystem.getActiveArenas()` against its internal set. Pattern (a) matches ADR-0003 communication channels better; pattern (b) is simpler. Decide based on whether `ArenaSystem` already emits such events.
- **`ModuleDescriptor` shape.** Currently sketched as `(class, configType, category)`. May need to grow a `requires: List<String>` for mechanic dependencies (e.g. `flag-captures` scoring `requires: ["carryFlags"]`) — keep it on the descriptor or on the module's `*Config` record? Module class is cleaner (descriptor stays a pure shape).
- **`*Config` validation surface.** Use Bean Validation, manual checks in the record's compact constructor, or a `Validated` interface modules implement? Lean toward compact constructors — keeps validation inside the record, no annotation pile-up.
- **Spawning module instances when arena has the module but config is empty.** E.g. `scoring 'kill-points'` with no kwargs — does the loader use defaults from the `KillPointsConfig` record or fail? Record defaults are the natural answer.
- **Are coordinators "always-loaded" globally or per-arena?** Per ADR-0008 they're core systems. They run globally but filter `EntitySet` by `ArenaId` — same pattern as today's energy / damage systems.
- **Where do module HUD elements live?** Some scoring modules want a HUD score display. The client side of arena modules is unscoped here; probably a Phase 5+ concern requiring a `ClientArenaModule` companion interface in api/.

## Comments

(none yet)

## References

- [ADR-0008](../../docs/adr/0008-arena-composition-and-modules.md) — the architectural decision this PRD implements.
- [ADR-0001](../../docs/adr/0001-ecs-component-model.md) — canonical writer rule + `*Change` entity pattern (coordinators are the same shape).
- [ADR-0002](../../docs/adr/0002-config-component-projection.md) — `*Config` records + template/instance split.
- [ADR-0004](../../docs/adr/0004-settings-pipeline.md) — settings pipeline hot-reload (this PRD extends it for module-set diffs).
- [ADR-0005](../../docs/adr/0005-layered-architecture.md) — api/ / server / client boundaries.
- [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) — Phase 3 external-author extension surface.
- [`subspace-module-archetypes/PRD.md`](../subspace-module-archetypes/PRD.md) — module *content* per gametype, unblocked by this framework.
- [`loaded-modules-list/PRD.md`](../loaded-modules-list/PRD.md) — client-side HUD downstream.
- `CONTEXT.md` — glossary entries for *Module*, *ArenaModule*, *Module coordinator system*, *Module composition shapes*, *Module tick discipline*, etc.
