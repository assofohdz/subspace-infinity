# Settings Pipeline Tracker

End-to-end status of every Subspace fragment key (canonical reference:
[`REFERENCE.md`](subspace-ini-reference/REFERENCE.md)) as it travels from
operator-facing Groovy fragment → typed `*Config` → ECS component →
runtime consumer. Rows = canonical settings keys. Columns trace the five
gates a setting must pass to actually affect gameplay, plus a derived
roll-up (Complete) and a coverage column (Test).

> **Out-of-scope keys live in [`out-of-scope.md`](out-of-scope.md).**
> Sections Infinity has decided not to implement (`[Cost]`, `[Flag]`,
> `[Kill]`, `[King]`, `[Latency]`, `[PacketLoss]`, `[Periodic]`,
> `[Routing]`, `[Security]`, `[Soccer]`, `[Team]`, `[Territory]`, plus
> `[Message] MessageDistance` and per-ship Soccer keys) have been cut
> from the `.groovy` sources and removed from this tracker. Don't
> re-add them here unless they become in-scope.

## Target architecture (post-migration)

Decided 2026-05-03. Source: grilling session resolving option (b) of
the typed-record migration in
[`settings-pipeline-slices.md`](settings-pipeline-slices.md) (slices
B0–B4). The end state for the settings pipeline is a single typed
`ConfigRegistry` per arena, owned by `ConfigRegistrySystem`, populated
from typed Groovy fragments (one fragment file per typed slot, one
adapter per fragment). The `Ini`-mirror DSL and `SettingsSystem` are
deleted in B4.

Reference this section when authoring new fragment keys, designing a
new `*Config` record, or deciding which file a tunable belongs in.

### Flattened `ConfigRegistry`

```
ConfigRegistry (per-arena snapshot, owned by ConfigRegistrySystem)
│
├── Per-ship template
│   └── ships : Map<Ship, ShipConfig>           ← ships.groovy
│
├── Weapon-projectile tuning  (flattened from old WeaponsConfig)
│   ├── bullet            : BulletConfig         ← bullet.groovy        [Bullet]
│   ├── bomb              : BombConfig           ← bomb.groovy          [Bomb]
│   ├── gravBomb          : GravBombConfig       ← gravbomb.groovy      [Bomb] *shared in VIE
│   ├── mine              : MineConfig           ← mine.groovy          [Mine]
│   ├── burst             : BurstFireConfig      ← burst.groovy         [Burst]
│   ├── repel             : RepelConfig          ← repel.groovy         [Repel]
│   └── thor              : ThorConfig           ← thor.groovy          (Infinity addition)
│
├── Prize spawning + dispatch
│   ├── prize             : PrizeConfig          ← prize.groovy         [Prize]
│   ├── prizeWeights      : PrizeWeightsConfig   ← prize-weights.groovy [PrizeWeight]
│   └── deathPrizeWeights : PrizeWeightsConfig   ← death-prize-weights.groovy  [DPrizeWeight] *svs-league only
│
├── Other gameplay sections (typed in later gameplay slices)
│   ├── brick             : BrickConfig          ← brick.groovy         [Brick]
│   ├── decoy             : DecoyConfig          ← decoy.groovy         [Misc] DecoyAliveTime
│   ├── portal            : PortalConfig         ← portal.groovy        [Misc] WarpPointDelay
│   ├── rocket            : RocketConfig         ← rocket.groovy        [Rocket]
│   ├── shrapnel          : ShrapnelConfig       ← shrapnel.groovy      [Shrapnel]
│   ├── wormhole          : WormholeConfig       ← wormhole.groovy      [Wormhole]
│   └── door              : DoorConfig           ← door.groovy          [Door]
│
└── Cosmetic / metadata
    ├── radar             : RadarConfig          ← radar.groovy         [Radar]
    ├── toggle            : ToggleConfig         ← toggle.groovy        [Toggle]
    ├── spectator         : SpectatorConfig      ← spectator.groovy     [Spectator]
    ├── message           : MessageConfig        ← message.groovy       [Message]
    ├── misc              : MiscConfig           ← misc.groovy          [Misc]   *narrow — only wired knobs
    ├── custom            : CustomConfig         ← custom.groovy        [Custom]
    └── owner             : OwnerConfig          ← owner.groovy         [Owner]
```

### Lives outside `ConfigRegistry`

```
ArenaConfig (per-arena structural — read by ArenaSystem)
│   ← arena.groovy
│
├── map               : String
├── fragmentIncludes  : List<String>     ← arena.groovy include list
├── wallFriction      : double
├── friendlyFire      : int (0/1/2)      ← Slice 9a; 0=off, 1=bomb splash only, 2=all
└── spawn             : SpawnConfig      ← absorbs [Spawn]'s 12 keys (in-scope, gameplay Slice 7)
```

`ArenaConfig` doesn't sit in `ConfigRegistry` because `ArenaSystem`
reads it *before* loading fragments — it tells the loader **which**
fragments to load. Two-phase load:
1. `GroovyArenaLoader` → `ArenaConfig` (resolves the includeFragment list)
2. `ConfigRegistrySystem.load(arenaId, ArenaConfig.fragmentIncludes())` →
   evaluates each fragment with its typed adapter → `ConfigRegistry` →
   atomic `replace()`

### Cut / out-of-scope (already deleted in 🚫 sweep, see `out-of-scope.md`)

These have **no slot in `ConfigRegistry`** and **no `*.groovy` file**.
Promotion path documented in
[`out-of-scope.md`](out-of-scope.md#promotion-path).

`[Cost]`, `[Flag]`, `[Kill]`, `[King]`, `[Latency]`, `[PacketLoss]`,
`[Periodic]`, `[Routing]`, `[Security]`, `[Soccer]`, `[Team]`,
`[Territory]`, `[Message] MessageDistance` (single key), per-ship
Soccer keys.

### Per-arena fragment file inventory (post-migration)

A preset only authors files for sections it overrides; the rest fall
back to the matching `*Config.DEFAULTS` constant in `api/src/infinity/config/`.

```
infinity/zone/arenas/<arena>/arena.groovy           ← arena.groovy entry point
infinity/zone/conf/<preset>/
├── ships.groovy           ← ship(WARBIRD) { ... } per Ship enum
├── bullet.groovy          ← bullet { damageLevel … }
├── bomb.groovy            ← bomb { damageLevel …; aliveTime … }
├── gravbomb.groovy        ← gravBomb { … }            *if preset diverges from bomb defaults
├── mine.groovy
├── burst.groovy
├── repel.groovy
├── thor.groovy                                        *only if preset wants Thor tuning override
├── prize.groovy
├── prize-weights.groovy
├── death-prize-weights.groovy                         *svs-league only
├── brick.groovy
├── decoy.groovy           ← decoy { aliveTime … }     [Misc] DecoyAliveTime
├── portal.groovy          ← portal { activeTime … }   [Misc] WarpPointDelay
├── rocket.groovy
├── shrapnel.groovy
├── wormhole.groovy
├── door.groovy
├── radar.groovy
├── toggle.groovy
├── spectator.groovy
├── message.groovy
├── misc.groovy            *narrow — only the [Misc] knobs that are actually wired
├── custom.groovy
└── owner.groovy
```

### Why these design choices

| Decision | Rationale |
|---|---|
| **Promote `ConfigRegistrySystem`, delete `SettingsSystem`** | `ConfigRegistrySystem` is already the typed-record store; `SettingsSystem` is the laggard `Ini`-mirror middleware. One keeper > two. |
| **Load orchestration lives in `ConfigRegistrySystem`** | Single entry point for load + reload; per-section loaders become internal collaborators. ArenaSystem doesn't need to know about specific loaders. |
| **No listener API in B0–B4 (YAGNI)** | 0 subscribers exist. Hot tuning works via "respawn the ship" (weapons/prizes auto-pick-up on next consumption). When a real subscriber appears, the API can be shaped to fit. |
| **Per-file typed adapters, not a mega-adapter** | Keeps the existing `GroovySettingsAdapter` contract (one file = one result type). Each adapter stays small + focused. |
| **Flatten `WeaponsConfig` into direct slots** | The old `WeaponsConfig` is a 7-field grouping struct with no behavior; consumers always reach through. Flattening makes `ConfigRegistry`'s public API the visual inventory of "what's tunable." |
| **`ArenaConfig` stays separate** | Read by `ArenaSystem` before fragments load (it specifies which fragments to load). Different lifecycle, different consumer. |
| **Full-replace on each load** | No partial-update API. Fragment evaluation is fast; whole-snapshot atomic swap matches existing `replace()` semantics. |

## Columns

| # | Column | Question | Marker meaning |
|---|--------|----------|----------------|
| 1 | **Complete** | Roll-up of all other gates | ✅ all wired · ⚠️ partial · ❌ unwired |
| 2 | **Setting** | Canonical Subspace key name | — |
| 3 | **Authored?** | Does any preset's `.groovy` file declare this key? | ✅ yes (filename) / ❌ no |
| 4 | **Loader** | Which `Groovy*Loader` reads it from `SettingsSystem`? | Class name / — for keys read elsewhere / ❌ |
| 5 | **API config** | Which `*Config` record carries it? | `Class.field` / — for non-tuning / ❌ |
| 6 | **Component** *(per-ship + [PrizeWeight] tables only)* | Which per-entity ECS component holds the runtime value? | `Class` (or `Class`/`MaxClass`) / — if not per-entity / ❌ if needed but missing |
| 7 | **Applier** | If a prize type, which `*PrizeApplier` handles it? | Class name / — for non-prize keys / ❌ |
| 8 | **Subsystem** | Which Java consumer reads the value at runtime? | `Class.method` / ❌ if dead |
| 9 | **Test** | Automated test exercising this setting end-to-end | `Class.method` / ❌ none |

The **Component** column appears only on tables where Pattern 4
(template → component → consumer, see [`config-pattern.md`](../.claude/rules/config-pattern.md))
applies: per-ship stat tables and the [PrizeWeight] dispatch table.
Arena-global tuning tables ([Bomb], [Bullet], etc.) read `*Config`
directly at projectile-creation time and have no per-entity slot, so
they remain 8-column.

Marker glossary: ✅ wired · ❌ not wired · — not applicable. The
**Complete** column is derived from the other gates:

- ✅ — every gate is ✅ or `—`
- ⚠️ — at least one ✅ and at least one ❌
- ❌ — every gate is ❌ (no progress)
- 🔀 — **diverged**: Infinity intentionally skips this canonical key
  and absorbs the concept into a different surface (typically a
  per-spawner / per-entity DSL). The "Subsystem" cell points to where
  the concept now lives. Diverged rows aren't TODOs — they're
  documented gaps.

A row with Complete = ✅ is fully gameplay-active. ⚠️ is the
"in-flight" state. ❌ rows are unstarted but in-scope. 🔀 rows are
intentional divergences from Subspace canon (look at the Subsystem
cell for where the equivalent now lives). Out-of-scope keys live in
[`out-of-scope.md`](out-of-scope.md), not here.

## When to update this table

- **Adding a new fragment key** to a `.groovy` file → flip `Authored?` to ✅, add the row if missing.
- **Adding a key to a `Groovy*Loader`** → flip `Loader` to the loader class name.
- **Adding a `*Config` field** → flip `API config` to `Record.field`.
- **Adding a per-entity ECS component** (per-ship / [PrizeWeight] tables) → flip `Component` to the component class.
- **Implementing a prize applier** → flip `Applier` from stub to the impl class.
- **Wiring a runtime consumer** → flip `Subsystem` to `Class.method`.
- **Adding a test that exercises the setting** → fill `Test` with `Class.method`.
- **Recompute `Complete`** in the same edit (the roll-up is not auto-derived).

Drift between this table and the code is worse than no table — see the
always-on rule in [`CLAUDE.md`](../CLAUDE.md#always-on-rules).

## Resolved (was: dual-pipeline drift on per-ship inventory)

Fixed for active arenas (trench, deva) in B2-Migration (`cb276025`,
2026-05-03). Per-ship inventory keys (`Initial*` / `*Max` for bombs,
bullets, mines, bursts, thors, repels, decoys, bricks, rockets, portals)
now flow through the typed `ship(Ship.X) { … }` DSL exclusively;
`ship-<name>.groovy` files in trench/deva no longer carry inventory
keys (deferred clusters like Status, Speeds, Multifire, etc. remain
until their owning gameplay slices migrate them).

`(default)` arena (base preset) still uses `GroovyShipLoader.FALLBACK`
for inventory (= permissive `DEFAULT_*` constants) since base has no
typed `ships.groovy`. SVS-family presets keep their pre-migration
state — none are loaded by any arena. These deferred migrations land
when their arena becomes active.

Verify per-arena with `~ship`: trench/deva ships now show the
operator's authored loadout (e.g. trench warbird is gun-only —
matches `ship-warbird.groovy`'s `RepelMax 0`).

---

## [Bomb]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BombDamageLevel` | ✅ bomb.groovy | `BombAdapter` (typed DSL) | `BombConfig.damage` | — | `WeaponsSystem.createProjectileBomb` (also gravbomb) | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `BombAliveTime` | ✅ bomb.groovy | `BombAdapter` (cs×10→ms) | `BombConfig.decayMs` | — | `WeaponsSystem.createProjectileBomb` (also gravbomb) | ❌ |
| ✅ | `BombExplodeDelay` | ✅ bomb.groovy (renamed `explodeDelayCs`) | `BombAdapter` (cs×10→ms) | `BombConfig.explodeDelayMs` | — | `WeaponsSystem.createProjectileBomb` (→ `ProximityFuse.fuseMs`) + `ProximityFuseSystem` (per-tick scan + detonate via `WeaponsSystem.detonateProjectile`) | ✅ `ProximityFuseSystemTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `BombExplodePixels` | ✅ bomb.groovy (renamed `explodeRadius`, tiles) | `BombAdapter` (typed DSL) | `BombConfig.explodeRadius` (tiles / world units) | — | `WeaponsSystem.createProjectileBomb` (per-level mult → SplashDamage component) | ✅ `BombFactoryTest` + `WeaponsSystemSplashTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `ProximityDistance` | ✅ bomb.groovy (renamed `proximityDistance`, tiles) | `BombAdapter` (typed DSL) | `BombConfig.proximityDistance` (tiles, base L1) | — | `WeaponsSystem.createProjectileBomb` (per-level +1 → `ProximityFuse.radiusWorldUnits`) + `ProximityFuseSystem` arming scan | ✅ `WeaponsSystemSplashTest.proximityRadiusForLevel*` + `ProximityFuseSystemTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `JitterTime` | ✅ bomb.groovy (renamed `jitterTimeCs`, cs) | `BombAdapter` (cs×10→ms) | `BombConfig.jitterTimeMs` | — | `WeaponsSystem.stampJitter` (after FF gate in direct-hit + splash paths) → `Jitter` component on victim → `JitterReaperSystem` (deadline reap) → client `JitterState` (camera offset, decaying amplitude) | manual launch (no harness for client AppStates) |
| ✅ | `BombSafety` | ✅ bomb.groovy (renamed `bombSafety`, boolean) | `BombAdapter` (typed DSL) | `BombConfig.bombSafety` | — | `WeaponsSystem.canAttackBomb` → `bombSafetyClear` (per-tick fire-time scan via existing `energyEntities` EntitySet; reuses `proximityRadiusForLevel` per-level scaling + `ProximityFuseSystem.shouldArmOn` FF gate; auto-no-ops when `proximityDistance == 0`) | ✅ `WeaponsSystemSplashTest.victimBlocksBombFire_*` + `ConfigRegistrySystemLoadTest` |
| ⚠️ | `EBombShutdownTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `EBombDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BBombDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Brick]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BrickTime` | ✅ brick.groovy | `BrickAdapter` (cs×10→ms) | `BrickConfig.timeMs` | — | `ConsumableSystem.createBrick` → `GameEntities.createBrick` (marker entity Decay deadline) | ✅ `BrickFactoryTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `BrickSpan` | ✅ brick.groovy | `BrickAdapter` (typed DSL) | `BrickConfig.spanTiles` | — | `ConsumableSystem.createBrick` → `BrickSpan` component on marker entity | ✅ `BrickFactoryTest` + `ConfigRegistrySystemLoadTest` |

## [Bullet]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BulletDamageLevel` | ✅ bullet.groovy | `BulletAdapter` (typed DSL) | `BulletConfig.damage` | — | `WeaponsSystem.createProjectileBullet` (via `damageAtLevel`) | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `BulletDamageUpgrade` | ✅ bullet.groovy | `BulletAdapter` (typed DSL) | `BulletConfig.damageUpgrade` | — | `WeaponsSystem.createProjectileBullet` (via `damageAtLevel`) | ❌ |
| ✅ | `BulletAliveTime` | ✅ bullet.groovy | `BulletAdapter` (cs×10→ms) | `BulletConfig.decayMs` | — | `WeaponsSystem.createProjectileBullet` | ❌ |
| ⚠️ | `ExactDamage` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Burst]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BurstDamageLevel` | ✅ burst.groovy | `BurstAdapter` (typed DSL) | `BurstFireConfig.damage` | — | `WeaponsSystem.createProjectileBurst` | ❌ |

## [Custom]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `SaveStatsTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Door]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `DoorDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ (DoorSystem reads via different path?) | ❌ |
| ⚠️ | `DoorMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Message]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ❌ | `MessageReliable` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AllowAudioMessages` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BongAllowed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `QuickMessageLimit` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `MessageTeamReliable` | ❌ | ❌ | ❌ | — | ❌ | ❌ |

## [Mine]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `MineAliveTime` | ✅ mine.groovy | `MineAdapter` (cs×10→ms) | `MineConfig.decayMs` | — | `WeaponsSystem.createProjectileMine` | ❌ |
| ⚠️ | `TeamMaxMines` | ✅ misc.groovy | ❌ | ❌ (probably belongs in a TeamConfig) | — | ❌ | ❌ |

## [Misc]

The biggest section; bounce/safety/spawn/timer knobs that mostly aren't read on the server hot path yet.

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ❌ | `FrequencyShipTypes` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ✅ | `WarpPointDelay` | ✅ portal.groovy | `PortalAdapter` (cs×10→ms) | `PortalConfig.activeTimeMs` | — | `ConsumableSystem.createPortal` → `GameEntities.createPortal` (marker entity Decay deadline) | ✅ `PortalFactoryTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `DecoyAliveTime` | ✅ decoy.groovy | `DecoyAdapter` (cs×10→ms) | `DecoyConfig.aliveTimeMs` | — | `ConsumableSystem.createDecoy` → `GameEntities.createDecoy` (marker entity Decay deadline) | ✅ `DecoyFactoryTest` + `ConfigRegistrySystemLoadTest` |
| ⚠️ | `BounceFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SafetyLimit` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `TickerDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `WarpRadiusLimit` | ✅ misc.groovy + spawn.groovy slot | ✅ `SpawnAdapter` (slot reserved on `SpawnConfig.warpRadiusLimit`, unconsumed) | ✅ `SpawnConfig.warpRadiusLimit` | — | ❌ (deferred — WarpSystem warp-key/Warp-prize randomization is the follow-up consumer slice) | ❌ |
| ⚠️ | `ActivateAppShutdownTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `NearDeathLevel` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `VictoryMusic` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `BannerPoints` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `MaxLossesToPlay` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `SpectatorQuiet` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `MaxPlaying` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `TimedGame` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `ResetScoreOnFrequencyChange` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SendPositionDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SlowFrameCheck` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SlowFrameRate` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AllowSavedShips` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `FrequencyShift` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `ExtraPositionData` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `SheepMessage` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `MaxPlayers` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `GreetMessage` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `PeriodicMessage0..4` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `MaxXRes` / `MaxYRes` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `ContinuumOnly` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `LevelFiles` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `MinUsage` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `StartInSpec` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `MaxTimerDrift` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `DisableScreenshot` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AntiWarpSettleDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `SaveSpawnScore` | ❌ | ❌ | ❌ | — | ❌ | ❌ |

## [Owner]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ❌ | `UserId` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `Name` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Prize]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `MultiPrizeCount` | ✅ misc.groovy | ❌ | ❌ | (used by `MultiPrizePrizeApplier` stub) | ❌ | ❌ |
| 🔀 | `PrizeFactor` | ❌ (diverged) | (n/a) | (n/a) | — | absorbed into `SpawnerSpec.countPerPlayer` per-spawner DSL (Slice 8d) — additive scaling, not canonical pure-scale | (n/a) |
| 🔀 | `PrizeDelay` | ❌ (diverged) | (n/a) | (n/a) | — | absorbed into `SpawnerSpec.spawnIntervalMs` per-spawner DSL (Slice 8d C1, was already there pre-rename) | (n/a) |
| 🔀 | `PrizeHideCount` | ❌ (diverged) | (n/a) | (n/a) | — | absorbed into `SpawnerSpec.regenBatch` per-spawner DSL (Slice 8d) | (n/a) |
| 🔀 | `MinimumVirtual` | ❌ (diverged) | (n/a) | (n/a) | — | absorbed into `SpawnerSpec.radius` per-spawner DSL (Slice 8d C1, was already there pre-rename) | (n/a) |
| 🔀 | `UpgradeVirtual` | ❌ (diverged) | (n/a) | (n/a) | — | absorbed into `SpawnerSpec.radiusPerPlayer` per-spawner DSL (Slice 8d) | (n/a) |
| ✅ | `PrizeMaxExist` | ✅ prize.groovy | `PrizeAdapter` (cs×10→ms) | `PrizeConfig.defaultDecayMs` | — | `PrizeSystem` (decay routing for prize entities) | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `PrizeMinExist` | ✅ prize.groovy | `PrizeAdapter` (cs×10→ms) | `PrizeConfig.defaultMinDecayMs` | — | `PrizeSystem.sampleDecayMs` (uniform random in `[minDecayMs, maxDecayMs]` for spawners with no explicit `ttlMs`) | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `PrizeNegativeFactor` | ✅ prize.groovy | `PrizeAdapter` (raw int) | `PrizeConfig.prizeNegativeFactor` | — | `PrizeSystem.maybeRollNegative` (1-in-N → swap to `Dud` via DUD substitution; called from both `spawnBounty` + `spawnDeathPrize`) | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `DeathPrizeTime` | ✅ prize.groovy | `PrizeAdapter` (cs×10→ms) | `PrizeConfig.deathPrizeTimeMs` | — | `EnergySystem` death branch → `PrizeSystem.spawnDeathPrize` (1 weighted prize at ship's `BodyPosition` on death; no-op when `deathPrizeTimeMs == 0`) | ✅ `ConfigRegistrySystemLoadTest` |
| ⚠️ | `EngineShutdownTime` | ✅ misc.groovy | ❌ | ❌ | (Glue family — see Status appliers) | ❌ | ❌ |
| ⚠️ | `TakePrizeReliable` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `S2CTakePrizeReliable` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [PrizeWeight] — applier dispatch

[PrizeWeight] keys are spawn-frequency multipliers per prize type, authored in a typed `prize-weights.groovy` fragment, read by `PrizeWeightsAdapter` into `PrizeWeightsConfig.weights`, and consumed by `PrizeSystem.readArenaWeights` via `ConfigRegistry.prizeWeights()`. The "Applier" column tracks the per-prize implementation status. "Subsystem" column = which ship-side system the applier writes through (or delegates to).

Loader column below is uniform: `PrizeWeightsAdapter` reads every weight key into the same `Map<String,Integer>` slot.

| C | Prize type | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `Recharge` (= "Full Charge") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Energy`/`Health` | `QuickChargePrizeApplier` ✅ | `EnergySystem.refillHealth` | ❌ |
| ✅ | `Energy` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Energy`/`EnergyMax` | `EnergyPrizeApplier` ✅ | `EnergySystem.update` | ❌ |
| ✅ | `Rotation` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Rotation`/`RotationMax` | `RotationPrizeApplier` ✅ | `PlayerDriver.update` | ❌ |
| ✅ | `Stealth` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Stealth`/`StealthStatus` | `StealthPrizeApplier` ✅ | `StatusDrainSystem` (drain when toggle on) | ❌ |
| ✅ | `Cloak` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Cloak`/`CloakStatus` | `CloakPrizeApplier` ✅ | `StatusDrainSystem` (drain when toggle on) | ✅ `CloakPrizeApplierTest` |
| ✅ | `XRadar` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `XRadar`/`XRadarStatus` | `XRadarPrizeApplier` ✅ | `StatusDrainSystem` (drain when toggle on) | ✅ `XRadarPrizeApplierTest` |
| ✅ | `Gun` (= "Gun Upgrade") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `BulletCurrentLevel`/`BulletMaxLevel` | `GunPrizeApplier` ✅ | `WeaponsSystem.createProjectileBullet` | ❌ |
| ✅ | `Bomb` (= "Bomb Upgrade") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `BombCurrentLevel`/`BombMaxLevel` + `MineCurrentLevel`/`MineMaxLevel` | `CompositePrizeApplier(BombPrizeApplier, MinePrizeApplier)` ✅ | `WeaponsSystem` (bomb + mine) | ❌ |
| ✅ | `Thrust` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Thrust`/`ThrustMax` | `ThrusterPrizeApplier` ✅ | `PlayerDriver.update` | ❌ |
| ✅ | `Speed` (= "Top Speed") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Speed`/`SpeedMax` | `TopSpeedPrizeApplier` ✅ | `PlayerDriver.update` | ❌ |
| ⚠️ | `MultiFire` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Multishot` | `MultiFirePrizeApplier` ✅ | (firing-mode consumer in `WeaponsSystem` deferred) | ✅ `MultiFirePrizeApplierTest` |
| ⚠️ | `Proximity` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | ❌ (no Proximity component) | `ProximityPrizeApplier` ❌ stub | (needs Proximity component + applier) | ❌ |
| ⚠️ | `Super` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | ❌ (no Super-active component) | `SuperPrizeApplier` ❌ stub | (needs Super-active component) | ❌ |
| ⚠️ | `Shields` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | ❌ (no Shields-active component) | `ShieldsPrizeApplier` ❌ stub | (needs Shields-active component) | ❌ |
| ⚠️ | `Shrap` (= "Shrapnel") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | ❌ (no Shrapnel/ShrapnelMax) | `ShrapnelPrizeApplier` ❌ stub | needs Shrapnel/ShrapnelMax components | ❌ |
| ✅ | `AntiWarp` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Antiwarp`/`AntiwarpStatus` | `AntiWarpPrizeApplier` ✅ | `StatusDrainSystem` (drain when toggle on) | ❌ |
| ✅ | `Repel` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Repel`/`RepelMax` | `RepelPrizeApplier` ✅ | `ConsumableSystem.actOut` | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `Burst` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Burst`/`BurstMax` | `BurstPrizeApplier` ✅ | `WeaponsSystem.createProjectileBurst` | ❌ |
| ✅ | `Decoy` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Decoy`/`DecoyMax` | `DecoyPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Thor` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Thor`/`ThorCurrentCount`/`ThorMaxCount` | `ThorPrizeApplier` ✅ | `ConsumableSystem.actOut` (FIRETHOR) | ❌ |
| ✅ | `Brick` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Brick`/`BrickMax` | `BrickPrizeApplier` ✅ | `ConsumableSystem.actOut` | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `Rocket` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Rocket`/`RocketMax` | `RocketPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Portal` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | `Portal`/`PortalMax` | `PortalPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Warp` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | — (teleport, no slot) | `WarpPrizeApplier` ✅ | `WarpSystem.warpToCenter` | ❌ |
| ⚠️ | `BouncingBullets` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | ❌ (no Bounce ship-toggle) | `BouncingBulletsPrizeApplier` ❌ stub | (needs Bounce-toggle component) | ❌ |
| ⚠️ | `Glue` (= "Engine Shutdown") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | ❌ (no EngineShutdown component) | `GluePrizeApplier` ❌ stub | (needs EngineShutdown component + timer) | ❌ |
| ✅ | `AllWeapons` (= "Super!") | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | composite (Guns/Bombs/Bursts/Mines) | `CompositePrizeApplier(BombPrizeApplier, BurstPrizeApplier, GunPrizeApplier, MinePrizeApplier)` ✅ | `WeaponsSystem` (composite) | ❌ |
| ⚠️ | `MultiPrize` | ✅ prize-weights.groovy | `PrizeWeightsAdapter` | — | — (recursive dispatch, no slot) | `MultiPrizePrizeApplier` ❌ stub | (needs recursive dispatch + RNG) | ❌ |

## [Radar]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `RadarMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `RadarNeutralSize` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `MapZoomFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Repel]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `RepelSpeed` | ✅ repel.groovy | ✅ `RepelAdapter` (typed DSL) | ✅ `RepelConfig.speed` | — | ✅ `ConsumableSystem.createRepel` → `RepelSpeed` component | ✅ `RepelFactoryTest` |
| ✅ | `RepelTime` | ✅ repel.groovy | ✅ `RepelAdapter` (cs×10→ms) | ✅ `RepelConfig.timeMs` | — | ✅ `ConsumableSystem.createRepel` → `Decay` | ✅ `RepelFactoryTest` |
| ✅ | `RepelDistance` | ✅ repel.groovy | ✅ `RepelAdapter` (typed DSL) | ✅ `RepelConfig.distancePixels` | — | ✅ `ConsumableSystem.createRepel` → `RepelDistance` component | ✅ `RepelFactoryTest` |

## [Rocket]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `RocketThrust` | ✅ rocket.groovy | `RocketAdapter` (typed DSL) | `RocketConfig.thrust` | — | `ConsumableSystem.createRocketBuff` (Thrust override on ship for buff lifetime) | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `RocketSpeed` | ✅ rocket.groovy | `RocketAdapter` (typed DSL) | `RocketConfig.speed` | — | `ConsumableSystem.createRocketBuff` (Speed override on ship for buff lifetime) | ✅ `ConfigRegistrySystemLoadTest` |

## [Shrapnel]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `ShrapnelSpeed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `InactiveShrapDamage` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `ShrapnelDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `Random` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Spawn]

Per-team spawn data. Subspace canonical encoding is 12 keys (4 teams ×
3 each: X, Y, Radius); Infinity's typed shape is a `List<TeamSpawn>` of
arbitrary length, looked up via `freq % teams.size()` — generalizes the
canon "Freq 4 → Team0, Freq 5 → Team1, …" wraparound to N teams.

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | per-team `Team<N>-X` / `Team<N>-Y` / `Team<N>-Radius` | ✅ trench/deva spawn.groovy | `SpawnAdapter` | `SpawnConfig.teams` | — | `ArenaSystem.getArenaSpawn(arenaName, freq)` → `GameSessionHostedService.resolveInitialSpawn` + `AvatarSystem.requestShipChange` | ✅ `ConfigRegistrySystemLoadTest` |

## [Spectator]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `HideFlags` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `NoXRadar` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Toggle]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `AntiWarpPixels` | ✅ misc.groovy | ❌ | ❌ | — | ❌ (relevant once AntiWarpPrizeApplier lands) | ❌ |

## [Wormhole]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `GravityBombs` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SwitchTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

---

## Per-ship sections (`[All]`, `[Warbird]`, `[Javelin]`, `[Spider]`, `[Leviathan]`, `[Terrier]`, `[Weasel]`, `[Lancaster]`, `[Shark]`)

Per-ship keys flow through `GroovyShipLoader` (parses `ships.groovy`'s
typed DSL into `ShipConfig`). Arena-global weapon/prize knobs flow
through their per-fragment typed adapters (`BulletAdapter`,
`BombAdapter`, `MineAdapter`, `BurstAdapter`, `RepelAdapter`,
`PrizeAdapter`, `PrizeWeightsAdapter`) — see the per-section tables
above. Authoritative per-ship coverage lives in
[`ship-config-dictionary.md`](ship-config-dictionary.md); this table
is the macro view.

### Initial / Maximum / Upgrade stats

| C | Setting | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `InitialRotation` / `MaximumRotation` / `UpgradeRotation` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.rotation` (`ShipStat` triple) | `Rotation`/`RotationMax`/`RotationUpgrade` | `RotationPrizeApplier` (Upgrade) | `PlayerDriver.update` | ❌ |
| ✅ | `InitialThrust` / `MaximumThrust` / `UpgradeThrust` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.thrust` | `Thrust`/`ThrustMax`/`ThrustUpgrade` | `ThrusterPrizeApplier` (Upgrade) | `PlayerDriver.update` | ❌ |
| ✅ | `InitialSpeed` / `MaximumSpeed` / `UpgradeSpeed` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.speed` | `Speed`/`SpeedMax`/`SpeedUpgrade` | `TopSpeedPrizeApplier` (Upgrade) | `PlayerDriver.update` | ❌ |
| ✅ | `InitialRecharge` / `MaximumRecharge` / `UpgradeRecharge` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.recharge` | `Recharge`/`RechargeMax`/`RechargeUpgrade` | `RechargePrizeApplier` (Upgrade) | `EnergySystem.update` | ❌ |
| ✅ | `InitialEnergy` / `MaximumEnergy` / `UpgradeEnergy` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.energy` | `Energy`/`EnergyMax`/`EnergyUpgrade` (+ `Health`) | `EnergyPrizeApplier` (Upgrade) | `EnergySystem.update` | ❌ |

### Ship abilities (Status family)

| C | Setting | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `CloakStatus` | ✅ ships.groovy | `GroovyShipLoader` (cloak block) | `ShipConfig.cloak.status` (`StatusStats`) | `CloakStatus` (+ `Cloak` toggle) | `CloakPrizeApplier` ✅ | `StatusDrainSystem` (drain when toggle on) | ✅ `CloakPrizeApplierTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `StealthStatus` | ✅ ships.groovy | `GroovyShipLoader` (stealth block) | `ShipConfig.stealth.status` (`StatusStats`) | `StealthStatus` (+ `Stealth` toggle) | `StealthPrizeApplier` ✅ | `StatusDrainSystem` | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `XRadarStatus` | ✅ ships.groovy | `GroovyShipLoader` (xradar block) | `ShipConfig.xradar.status` (`StatusStats`) | `XRadarStatus` (+ `XRadar` toggle) | `XRadarPrizeApplier` ✅ | `StatusDrainSystem` (drain when toggle on) | ✅ `XRadarPrizeApplierTest` + `ConfigRegistrySystemLoadTest` |
| ✅ | `AntiWarpStatus` | ✅ ships.groovy | `GroovyShipLoader` (antiwarp block) | `ShipConfig.antiwarp.status` (`StatusStats`) | `AntiwarpStatus` (+ `Antiwarp` toggle) | `AntiWarpPrizeApplier` ✅ | `StatusDrainSystem` | ✅ `ConfigRegistrySystemLoadTest` |
| ✅ | `CloakEnergy` / `StealthEnergy` / `XRadarEnergy` / `AntiWarpEnergy` | ✅ ships.groovy | `GroovyShipLoader` (`energy:` arg on each Status block) | `ShipConfig.{cloak,stealth,xradar,antiwarp}.energyDrainPer1000Cs` | `CloakEnergy` / `StealthEnergy` / `XRadarEnergy` / `AntiwarpEnergy` | — (drain rates) | `StatusDrainSystem` | ✅ `StatusDrainSystemTest` |

### Inventory caps and starts

| C | Setting | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `InitialRepel` / `RepelMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.repels` (`CountStats`) | `Repel`/`RepelMax` | `RepelPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `InitialBurst` / `BurstMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bursts` | `Burst`/`BurstMax` | `BurstPrizeApplier` ✅ | `WeaponsSystem.createProjectileBurst` | ❌ |
| ✅ | `InitialBrick` / `BrickMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bricks` (or similar) | `Brick`/`BrickMax` | `BrickPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `InitialRocket` / `RocketMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.rockets` | `Rocket`/`RocketMax` | `RocketPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `InitialThor` / `ThorMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.thors` | `Thor`/`ThorCurrentCount`/`ThorMaxCount` | `ThorPrizeApplier` ✅ | `ConsumableSystem.actOut` (FIRETHOR) | ❌ |
| ✅ | `InitialDecoy` / `DecoyMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.decoys` | `Decoy`/`DecoyMax` | `DecoyPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `InitialPortal` / `PortalMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.portals` | `Portal`/`PortalMax` | `PortalPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `InitialGuns` / `MaxGuns` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bullets` (`BulletStats`) | `BulletCurrentLevel`/`BulletMaxLevel` | `GunPrizeApplier` ✅ | `WeaponsSystem.createProjectileBullet` | ❌ |
| ✅ | `InitialBombs` / `MaxBombs` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bombs` (`BombStats`) | `BombCurrentLevel`/`BombMaxLevel` | `BombPrizeApplier` ✅ | `WeaponsSystem.createProjectileBomb` | ❌ |

### Bullets / Bombs / Mines (per-ship cost & cadence)

| C | Setting | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `BulletFireEnergy` | ✅ ships.groovy | `GroovyShipLoader` | `BulletStats.fireCost` | `BulletCost` | — | `WeaponsSystem.deductCostOfAttackBullet` | ❌ |
| ✅ | `BulletSpeed` | ✅ ships.groovy (`bullets ... speed:`) | `GroovyShipLoader.bullets` | `BulletStats.speed` | `BulletSpeed` | — | `WeaponsSystem.getAttackInfo` (case BULLET) → `effectiveProjectileSpeed(BulletSpeed.speed, EngineConfig.subspaceVelocityScale, .maxProjectileSpeedJme)` | ✅ `WeaponsSystemSplashTest.effectiveProjectileSpeed_*` + `ConfigRegistrySystemLoadTest` (trench warbird `5000`) + `ShipSpawnSystemTest` (projection) |
| ✅ | `BulletFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `BulletStats.fireDelayMs` | `BulletFireDelay` | — | `WeaponsSystem` cooldown | ❌ |
| ⚠️ | `MultiFireEnergy` / `MultiFireDelay` / `MultiFireAngle` | ✅ ships.groovy | ❌ | ❌ | `Multishot` (toggle exists, fields ❌) | — | ❌ | ❌ |
| ⚠️ | `DoubleBarrel` | ✅ ships.groovy | ❌ | ❌ | `DoubleBarrel` (component exists) | — | ❌ | ❌ |
| ✅ | `BombFireEnergy` / `BombFireEnergyUpgrade` | ✅ ships.groovy | `GroovyShipLoader` | `BombStats.fireCost`/`fireCostUpgrade` | `BombCost` | — | `WeaponsSystem.deductCostOfAttackBomb` | ❌ |
| ⚠️ | `BombThrust` / `BombBounceCount` | ✅ ships.groovy | ❌ | ❌ | ❌ (no component) | — | ❌ | ❌ |
| ✅ | `BombSpeed` | ✅ ships.groovy (`bombs ... speed:`) | `GroovyShipLoader.bombs` | `BombStats.speed` | `BombSpeed` | — | `WeaponsSystem.getAttackInfo` (case BOMB) → `effectiveProjectileSpeed(BombSpeed.speed, scale, cap)` | ✅ `WeaponsSystemSplashTest.effectiveProjectileSpeed_*` + `ShipSpawnSystemTest` |
| ✅ | `BombFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `BombStats.fireDelayMs` | `BombFireDelay` | — | `WeaponsSystem` cooldown | ❌ |
| ⚠️ | `EmpBomb` / `SeeBombLevel` | ✅ ships.groovy | ❌ | ❌ | ❌ (no component) | — | ❌ | ❌ |
| ✅ | `MaxMines` | ✅ ships.groovy | `GroovyShipLoader` | `MineStats.max` | `MineMaxCount` | — | `WeaponsSystem` mine cap check | ❌ |
| ⚠️ | `SeeMines` | ✅ ships.groovy | ❌ | ❌ | ❌ (no component) | — | ❌ | ❌ |
| ✅ | `LandmineFireEnergy` / `LandmineFireEnergyUpgrade` | ✅ ships.groovy | `GroovyShipLoader` | `MineStats.fireCost`/`upgrade` | `MineCost` | — | `WeaponsSystem.deductCostOfAttackMine` | ❌ |
| ✅ | `LandmineFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `MineStats.fireDelayMs` | `MineFireDelay` | — | `WeaponsSystem` cooldown | ❌ |

### Shrapnel / Burst / Turret / Misc / Physical

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `ShrapnelMax` | ✅ ships.groovy | ❌ | ❌ | `ShrapnelPrizeApplier` ❌ stub | ❌ | ❌ |
| ⚠️ | `ShrapnelRate` | ✅ ships.groovy | ❌ | ❌ | (used by `ShrapnelPrizeApplier` increment) | ❌ | ❌ |
| ✅ | `BurstSpeed` | ✅ ships.groovy (`bursts ... speed:`) | `GroovyShipLoader.bursts` | `BurstStats.speed` | — | `WeaponsSystem.getAttackInfo` (case BURST — slice 10 latent fix) reads `BurstSpeed` component → `effectiveProjectileSpeed(.speed, scale, cap)` | ✅ `WeaponsSystemSplashTest.effectiveProjectileSpeed_*` + `ShipSpawnSystemTest` |
| ⚠️ | `BurstShrapnel` | ✅ ships.groovy | ❌ (lives in `BurstFireConfig.projectileCount`?) | partial | — | `WeaponsSystem.createProjectileBurst` | ❌ |
| ⚠️ | `TurretThrustPenalty` / `TurretSpeedPenalty` / `TurretLimit` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ✅ | `RocketTime` | ✅ ships.groovy | `GroovyShipLoader` (`activeTimeCs` arg on `rockets` block; cs×10→ms at projection to ship `RocketTime` component) | `RocketStats.activeTimeCs` | — | `ConsumableSystem.createRocketBuff` (buff entity Decay deadline); `RocketBuffSystem` (swap/revert Thrust+Speed) | ✅ `RocketBuffActivationTest` |
| ⚠️ | `InitialBounty` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AttachBounty` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AfterburnerEnergy` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `DisableFastShooting` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| 🔀 | `Radius` | ❌ (diverged) | (n/a) | (n/a) | — | Infinity divergence — engine-tier `EngineConfig.shipRadius` instead of per-ship; collision radius is identical across all ships in the build (S6) | (n/a) |
| ⚠️ | `DamageFactor` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `PrizeShareLimit` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SuperTime` | ✅ ships.groovy | ❌ | ❌ | (used by `SuperPrizeApplier`) | ❌ | ❌ |
| ⚠️ | `ShieldsTime` | ✅ ships.groovy | ❌ | ❌ | (used by `ShieldsPrizeApplier`) | ❌ | ❌ |
| ⚠️ | `Gravity` / `GravityTopSpeed` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |

---

## Summary

- **Wired tuning settings (typed adapters):** `[Bullet]` (3 keys), `[Bomb]` (2), `[Mine]` (1), `[Burst]` (1), `[Repel]` (3), `[Rocket]` (2 + per-ship RocketTime), `[Brick]` (2), `[Misc] DecoyAliveTime` (1), `[Misc] WarpPointDelay` (1), `[Prize]` (1) — flow from preset → `*Config` → consuming subsystem.
- **Wired prize appliers (out of 30 PrizeTypes):** 17 done, 13 stubs (mostly Status family, Shrapnel, MultiPrize).
- **Per-ship `ShipConfig`:** thrust/speed/rotation/recharge/energy stat triples + 8 inventory CountStats + 3 weapon stats — fully wired via `GroovyShipLoader`.
- **Status family `*Status` / `*Energy` ship keys:** authored in ships.groovy and the per-entity components (`CloakStatus`, `Cloak`, `CloakEnergy`, etc.) **already exist** — what's missing is the `GroovyShipLoader` read, the `*Config` field, and the prize applier. So these rows are 1 component-class step further along than they look at first glance.
- **Component-class gaps surfaced by the v2 column:** `Proximity`, `Super`, `Shields`, `Shrap` (Shrapnel/ShrapnelMax), `BouncingBullets`, `Glue` (EngineShutdown) all need new component classes before their stub appliers can do anything meaningful.
- **Out-of-scope (🚫):** see [`out-of-scope.md`](out-of-scope.md). Cut from this tracker and the `.groovy` sources in the typed-DSL migration sweep.
- **In-scope but unstarted (❌):** `[Spawn]` (12 keys, today driven by hardcoded `centerOfArena`), several `[Misc]` rows that are unauthored.
- **Test coverage:** 0 rows have any automated test today. Per the spawn-projection-test-gap, manual launch is the only verification path. Filling the Test column is a separate workstream.
