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
│   ├── prizeWeights      : PrizeWeightsConfig   ← prizeweights.groovy  [PrizeWeight]
│   └── deathPrizeWeights : PrizeWeightsConfig   ← deathprizeweights.groovy  [DPrizeWeight] *svs-league only
│
├── Other gameplay sections (typed in later gameplay slices)
│   ├── brick             : BrickConfig          ← brick.groovy         [Brick]
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
├── prizeweights.groovy
├── deathprizeweights.groovy                           *svs-league only
├── brick.groovy
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

A row with Complete = ✅ is fully gameplay-active. ⚠️ is the
"in-flight" state. ❌ rows are unstarted but in-scope. Out-of-scope
keys live in [`out-of-scope.md`](out-of-scope.md), not here.

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

## Known issue: dual-pipeline drift on per-ship inventory

Two parallel paths read ship config and they don't agree:

- **Typed loader** — `infinity/zone/conf/<preset>/ships.groovy` evaluated
  by [`GroovyShipLoader`](../infinity/src/main/java/infinity/settings/GroovyShipLoader.java)
  via the typed DSL (`repels start: X, max: Y`, `bursts ...`, etc.).
  Output: `ShipConfig.repels` / `bursts` / `thors` / weapons stats →
  `ShipSpawnSystem` projects to ECS components. Drives Pattern-4 tuning.
- **Untyped fragments** — `infinity/zone/conf/<preset>/ship-<name>.groovy`
  evaluated by [`GroovyFragmentLoader`](../infinity/src/main/java/infinity/settings/GroovyFragmentLoader.java)
  via `shipSection(name) { Key Value }` which writes Subspace-canonical
  INI keys (`RepelMax`, `InitialRepel`, `BurstMax`, …) into the merged
  `Ini` store backing `SettingsSystem`. Drives Subspace-canonical
  consumers that read from `SettingsSystem` directly.

When `ships.groovy` omits an inventory block (e.g. trench-04-2026 has no
`repels`/`bursts`/`thors` blocks for any ship), the typed loader falls
back to `DEFAULT_REPELS = CountStats(start=10, max=20)`. The
`ship-<name>.groovy` fragment's `RepelMax 4` for that same ship is
silently ignored by the spawn-projection path — it only affects code
that reads `SettingsSystem` directly. End result: per-ship `Repel` /
`RepelMax` rows below show ✅ but the *value* a player sees is the
loader default, not the Subspace fragment value.

Resolution options for a future slice:

1. Make `GroovyShipLoader.ShipBuilder` consult the merged `Ini` store
   for inventory keys when its typed block is missing — single source
   per ship, fragment values win.
2. Move every per-ship inventory key to the typed `ships.groovy` DSL
   and delete the corresponding lines from `ship-<name>.groovy`.
   Higher migration cost, fully eliminates the dual path for inventory.
3. Document the split as intentional: `ships.groovy` for the typed
   tuning surface, `ship-<name>.groovy` for unmigrated/legacy keys
   that still go through `SettingsSystem`. Lowest churn, leaves the
   footgun in place.

Verify with `~ship` (in-game) — if `repel=10/20` doesn't match the
`InitialRepel`/`RepelMax` in the preset's `ship-<name>.groovy`, this
drift is the cause.

---

## [Bomb]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BombDamageLevel` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBomb` | `BombConfig.damage` | — | `WeaponsSystem.createProjectileBomb` (also gravbomb) | ❌ |
| ✅ | `BombAliveTime` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBomb` | `BombConfig.decayMs` | — | `WeaponsSystem.createProjectileBomb` (also gravbomb) | ❌ |
| ⚠️ | `BombExplodeDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BombExplodePixels` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `ProximityDistance` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `JitterTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BombSafety` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `EBombShutdownTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `EBombDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BBombDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Brick]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `BrickTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BrickSpan` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Bullet]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BulletDamageLevel` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBullet` | `BulletConfig.damage` | — | `WeaponsSystem.createProjectileGun` (via `damageAtLevel`) | ❌ |
| ✅ | `BulletDamageUpgrade` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBullet` | `BulletConfig.damageUpgrade` | — | `WeaponsSystem.createProjectileGun` (via `damageAtLevel`) | ❌ |
| ✅ | `BulletAliveTime` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBullet` | `BulletConfig.decayMs` | — | `WeaponsSystem.createProjectileGun` | ❌ |
| ⚠️ | `ExactDamage` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Burst]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `BurstDamageLevel` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBurst` | `BurstFireConfig.damage` | — | `WeaponsSystem.createProjectileBurst` | ❌ |

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
| ✅ | `MineAliveTime` | ✅ misc.groovy | `GroovyWeaponsLoader.loadMine` | `MineConfig.decayMs` | — | `WeaponsSystem.createProjectileMine` | ❌ |
| ⚠️ | `TeamMaxMines` | ✅ misc.groovy | ❌ | ❌ (probably belongs in a TeamConfig) | — | ❌ | ❌ |

## [Misc]

The biggest section; bounce/safety/spawn/timer knobs that mostly aren't read on the server hot path yet.

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ❌ | `FrequencyShipTypes` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `WarpPointDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `DecoyAliveTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BounceFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SafetyLimit` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `TickerDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `WarpRadiusLimit` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
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
| ⚠️ | `PrizeFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `PrizeDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `PrizeHideCount` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `MinimumVirtual` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `UpgradeVirtual` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ✅ | `PrizeMaxExist` | ✅ misc.groovy | `GroovyWeaponsLoader.loadPrize` | `PrizeConfig.defaultDecayMs` | — | `PrizeSystem` (decay routing for prize entities) | ❌ |
| ⚠️ | `PrizeMinExist` | ✅ misc.groovy | ❌ | ❌ (would need a range field) | — | ❌ | ❌ |
| ⚠️ | `PrizeNegativeFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `DeathPrizeTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `EngineShutdownTime` | ✅ misc.groovy | ❌ | ❌ | (Glue family — see Status appliers) | ❌ | ❌ |
| ⚠️ | `TakePrizeReliable` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `S2CTakePrizeReliable` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [PrizeWeight] — applier dispatch

[PrizeWeight] keys are spawn-frequency multipliers per prize type, read by `PrizeSystem.handlePrizeAcquisition` via the merged `Ini`. The "Applier" column tracks the per-prize implementation status. "Subsystem" column = which ship-side system the applier writes through (or delegates to).

| C | Prize type | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `Recharge` (= "Full Charge") | ✅ misc.groovy | `PrizeSystem` (PrizeWeights) | — | `Energy`/`Health` | `QuickChargePrizeApplier` ✅ | `EnergySystem.refillHealth` | ❌ |
| ✅ | `Energy` | ✅ misc.groovy | `PrizeSystem` | — | `Energy`/`EnergyMax` | `EnergyPrizeApplier` ✅ | `EnergySystem.update` | ❌ |
| ✅ | `Rotation` | ✅ misc.groovy | `PrizeSystem` | — | `Rotation`/`RotationMax` | `RotationPrizeApplier` ✅ | `PlayerDriver.update` | ❌ |
| ⚠️ | `Stealth` | ✅ misc.groovy | `PrizeSystem` | — | `Stealth`/`StealthStatus` | `StealthPrizeApplier` ❌ stub | (needs toggle wiring) | ❌ |
| ⚠️ | `Cloak` | ✅ misc.groovy | `PrizeSystem` | — | `Cloak`/`CloakStatus` | `CloakPrizeApplier` ❌ stub | (needs toggle wiring) | ❌ |
| ⚠️ | `XRadar` | ✅ misc.groovy | `PrizeSystem` | — | `XRadar`/`XRadarStatus` | `XRadarPrizeApplier` ❌ stub | (needs toggle wiring) | ❌ |
| ✅ | `Gun` (= "Gun Upgrade") | ✅ misc.groovy | `PrizeSystem` | — | `GunCurrentLevel`/`GunMaxLevel` | `GunPrizeApplier` ✅ | `WeaponsSystem.createProjectileGun` | ❌ |
| ✅ | `Bomb` (= "Bomb Upgrade") | ✅ misc.groovy | `PrizeSystem` | — | `BombCurrentLevel`/`BombMaxLevel` + `MineCurrentLevel`/`MineMaxLevel` | `CompositePrizeApplier(BombPrizeApplier, MinePrizeApplier)` ✅ | `WeaponsSystem` (bomb + mine) | ❌ |
| ✅ | `Thrust` | ✅ misc.groovy | `PrizeSystem` | — | `Thrust`/`ThrustMax` | `ThrusterPrizeApplier` ✅ | `PlayerDriver.update` | ❌ |
| ✅ | `Speed` (= "Top Speed") | ✅ misc.groovy | `PrizeSystem` | — | `Speed`/`SpeedMax` | `TopSpeedPrizeApplier` ✅ | `PlayerDriver.update` | ❌ |
| ⚠️ | `MultiFire` | ✅ misc.groovy | `PrizeSystem` | — | `Multishot` | `MultiFirePrizeApplier` ❌ stub | (needs Multishot wiring) | ❌ |
| ⚠️ | `Proximity` | ✅ misc.groovy | `PrizeSystem` | — | ❌ (no Proximity component) | `ProximityPrizeApplier` ❌ stub | (needs Proximity component + applier) | ❌ |
| ⚠️ | `Super` | ✅ misc.groovy | `PrizeSystem` | — | ❌ (no Super-active component) | `SuperPrizeApplier` ❌ stub | (needs Super-active component) | ❌ |
| ⚠️ | `Shields` | ✅ misc.groovy | `PrizeSystem` | — | ❌ (no Shields-active component) | `ShieldsPrizeApplier` ❌ stub | (needs Shields-active component) | ❌ |
| ⚠️ | `Shrap` (= "Shrapnel") | ✅ misc.groovy | `PrizeSystem` | — | ❌ (no Shrapnel/ShrapnelMax) | `ShrapnelPrizeApplier` ❌ stub | needs Shrapnel/ShrapnelMax components | ❌ |
| ⚠️ | `AntiWarp` | ✅ misc.groovy | `PrizeSystem` | — | `Antiwarp`/`AntiwarpStatus` | `AntiWarpPrizeApplier` ❌ stub | (needs toggle wiring) | ❌ |
| ✅ | `Repel` | ✅ misc.groovy | `PrizeSystem` | — | `Repel`/`RepelMax` | `RepelPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Burst` | ✅ misc.groovy | `PrizeSystem` | — | `Burst`/`BurstMax` | `BurstPrizeApplier` ✅ | `WeaponsSystem.createProjectileBurst` | ❌ |
| ✅ | `Decoy` | ✅ misc.groovy | `PrizeSystem` | — | `Decoy`/`DecoyMax` | `DecoyPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Thor` | ✅ misc.groovy | `PrizeSystem` | — | `Thor`/`ThorCurrentCount`/`ThorMaxCount` | `ThorPrizeApplier` ✅ | `ConsumableSystem.actOut` (FIRETHOR) | ❌ |
| ✅ | `Brick` | ✅ misc.groovy | `PrizeSystem` | — | `Brick`/`BrickMax` | `BrickPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Rocket` | ✅ misc.groovy | `PrizeSystem` | — | `Rocket`/`RocketMax` | `RocketPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Portal` | ✅ misc.groovy | `PrizeSystem` | — | `Portal`/`PortalMax` | `PortalPrizeApplier` ✅ | `ConsumableSystem.actOut` | ❌ |
| ✅ | `Warp` | ✅ misc.groovy | `PrizeSystem` | — | — (teleport, no slot) | `WarpPrizeApplier` ✅ | `WarpSystem.warpToCenter` | ❌ |
| ⚠️ | `BouncingBullets` | ✅ misc.groovy | `PrizeSystem` | — | ❌ (no Bounce ship-toggle) | `BouncingBulletsPrizeApplier` ❌ stub | (needs Bounce-toggle component) | ❌ |
| ⚠️ | `Glue` (= "Engine Shutdown") | ✅ misc.groovy | `PrizeSystem` | — | ❌ (no EngineShutdown component) | `GluePrizeApplier` ❌ stub | (needs EngineShutdown component + timer) | ❌ |
| ✅ | `AllWeapons` (= "Super!") | ✅ misc.groovy | `PrizeSystem` | — | composite (Guns/Bombs/Bursts/Mines) | `CompositePrizeApplier(BombPrizeApplier, BurstPrizeApplier, GunPrizeApplier, MinePrizeApplier)` ✅ | `WeaponsSystem` (composite) | ❌ |
| ⚠️ | `MultiPrize` | ✅ misc.groovy | `PrizeSystem` | — | — (recursive dispatch, no slot) | `MultiPrizePrizeApplier` ❌ stub | (needs recursive dispatch + RNG) | ❌ |

## [Radar]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `RadarMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `RadarNeutralSize` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `MapZoomFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Repel]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ✅ | `RepelSpeed` | ✅ misc.groovy | ✅ `GroovyWeaponsLoader.loadRepel` | ✅ `RepelConfig.speed` | — | ✅ `ConsumableSystem.createRepel` → `RepelSpeed` component | ✅ `RepelFactoryTest` |
| ✅ | `RepelTime` | ✅ misc.groovy | ✅ `GroovyWeaponsLoader.loadRepel` (cs×10→ms) | ✅ `RepelConfig.timeMs` | — | ✅ `ConsumableSystem.createRepel` → `Decay` | ✅ `RepelFactoryTest` |
| ✅ | `RepelDistance` | ✅ misc.groovy | ✅ `GroovyWeaponsLoader.loadRepel` | ✅ `RepelConfig.distancePixels` | — | ✅ `ConsumableSystem.createRepel` → `RepelDistance` component | ✅ `RepelFactoryTest` |

## [Rocket]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `RocketThrust` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `RocketSpeed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Shrapnel]

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ⚠️ | `ShrapnelSpeed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `InactiveShrapDamage` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `ShrapnelDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `Random` | ✅ misc.groovy | ❌ | ❌ | — | ❌ | ❌ |

## [Spawn]

12 keys (4 teams × 3 each: X, Y, Radius). Spawn-point selection not yet wired through ConfigRegistry; today driven by hardcoded centerOfArena in `WarpSystem`. In-scope but unstarted.

| C | Setting | Authored? | Loader | API config | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|
| ❌ | (all 12 keys) | ❌ | ❌ | ❌ | — | ❌ | ❌ |

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
typed DSL into `ShipConfig`), not `GroovyWeaponsLoader`. Authoritative
per-ship coverage lives in [`ship-config-dictionary.md`](ship-config-dictionary.md);
this table is the macro view.

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
| ⚠️ | `CloakStatus` | ✅ ships.groovy | ❌ (not yet read) | ❌ | `CloakStatus` (+ `Cloak` toggle) | `CloakPrizeApplier` ❌ stub | ❌ | ❌ |
| ⚠️ | `StealthStatus` | ✅ ships.groovy | ❌ | ❌ | `StealthStatus` (+ `Stealth` toggle) | `StealthPrizeApplier` ❌ stub | ❌ | ❌ |
| ⚠️ | `XRadarStatus` | ✅ ships.groovy | ❌ | ❌ | `XRadarStatus` (+ `XRadar` toggle) | `XRadarPrizeApplier` ❌ stub | ❌ | ❌ |
| ⚠️ | `AntiWarpStatus` | ✅ ships.groovy | ❌ | ❌ | `AntiwarpStatus` (+ `Antiwarp` toggle) | `AntiWarpPrizeApplier` ❌ stub | ❌ | ❌ |
| ⚠️ | `CloakEnergy` / `StealthEnergy` / `XRadarEnergy` / `AntiWarpEnergy` | ✅ ships.groovy | ❌ | ❌ | `CloakEnergy`/`StealthEnergy`/`XRadarEnergy`/`AntiwarpEnergy` | — (drain rates) | ❌ | ❌ |

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
| ✅ | `InitialGuns` / `MaxGuns` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.guns` (`GunStats`) | `GunCurrentLevel`/`GunMaxLevel` | `GunPrizeApplier` ✅ | `WeaponsSystem.createProjectileGun` | ❌ |
| ✅ | `InitialBombs` / `MaxBombs` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bombs` (`BombStats`) | `BombCurrentLevel`/`BombMaxLevel` | `BombPrizeApplier` ✅ | `WeaponsSystem.createProjectileBomb` | ❌ |

### Bullets / Bombs / Mines (per-ship cost & cadence)

| C | Setting | Authored? | Loader | API config | Component | Applier | Subsystem | Test |
|---|---|---|---|---|---|---|---|---|
| ✅ | `BulletFireEnergy` | ✅ ships.groovy | `GroovyShipLoader` | `GunStats.fireCost` | `GunCost` | — | `WeaponsSystem.deductCostOfAttackGun` | ❌ |
| ⚠️ | `BulletSpeed` | ✅ ships.groovy | ❌ (per-ship — ShipConfig field?) | ❌ | ❌ (no component) | — | ❌ (today inline `addLocal(0,0,50)` in WeaponsSystem) | ❌ |
| ✅ | `BulletFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `GunStats.fireDelayMs` | `GunFireDelay` | — | `WeaponsSystem` cooldown | ❌ |
| ⚠️ | `MultiFireEnergy` / `MultiFireDelay` / `MultiFireAngle` | ✅ ships.groovy | ❌ | ❌ | `Multishot` (toggle exists, fields ❌) | — | ❌ | ❌ |
| ⚠️ | `DoubleBarrel` | ✅ ships.groovy | ❌ | ❌ | `DoubleBarrel` (component exists) | — | ❌ | ❌ |
| ✅ | `BombFireEnergy` / `BombFireEnergyUpgrade` | ✅ ships.groovy | `GroovyShipLoader` | `BombStats.fireCost`/`fireCostUpgrade` | `BombCost` | — | `WeaponsSystem.deductCostOfAttackBomb` | ❌ |
| ⚠️ | `BombThrust` / `BombBounceCount` | ✅ ships.groovy | ❌ | ❌ | ❌ (no component) | — | ❌ | ❌ |
| ⚠️ | `BombSpeed` | ✅ ships.groovy | ❌ | ❌ | ❌ (no component) | — | ❌ (inline `25` today) | ❌ |
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
| ⚠️ | `BurstSpeed` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `BurstShrapnel` | ✅ ships.groovy | ❌ (lives in `BurstFireConfig.projectileCount`?) | partial | — | `WeaponsSystem.createProjectileBurst` | ❌ |
| ⚠️ | `TurretThrustPenalty` / `TurretSpeedPenalty` / `TurretLimit` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `RocketTime` | ✅ ships.groovy | ❌ | ❌ | (used by `RocketPrizeApplier` for active duration) | ❌ | ❌ |
| ⚠️ | `InitialBounty` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AttachBounty` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `AfterburnerEnergy` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ❌ | `DisableFastShooting` | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `Radius` | ✅ ships.groovy | ❌ | ❌ | — | ❌ (physics path) | ❌ |
| ⚠️ | `DamageFactor` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `PrizeShareLimit` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |
| ⚠️ | `SuperTime` | ✅ ships.groovy | ❌ | ❌ | (used by `SuperPrizeApplier`) | ❌ | ❌ |
| ⚠️ | `ShieldsTime` | ✅ ships.groovy | ❌ | ❌ | (used by `ShieldsPrizeApplier`) | ❌ | ❌ |
| ⚠️ | `Gravity` / `GravityTopSpeed` | ✅ ships.groovy | ❌ | ❌ | — | ❌ | ❌ |

---

## Summary

- **Wired tuning settings (`misc.groovy`):** `[Bullet]` (3 keys), `[Bomb]` (2), `[Mine]` (1), `[Burst]` (1), `[Prize]` (1) — **8 keys** total flow from preset → `*Config` → `WeaponsSystem`/`PrizeSystem`.
- **Wired prize appliers (out of 30 PrizeTypes):** 17 done, 13 stubs (mostly Status family, Shrapnel, MultiPrize).
- **Per-ship `ShipConfig`:** thrust/speed/rotation/recharge/energy stat triples + 8 inventory CountStats + 3 weapon stats — fully wired via `GroovyShipLoader`.
- **Status family `*Status` / `*Energy` ship keys:** authored in ships.groovy and the per-entity components (`CloakStatus`, `Cloak`, `CloakEnergy`, etc.) **already exist** — what's missing is the `GroovyShipLoader` read, the `*Config` field, and the prize applier. So these rows are 1 component-class step further along than they look at first glance.
- **Component-class gaps surfaced by the v2 column:** `Proximity`, `Super`, `Shields`, `Shrap` (Shrapnel/ShrapnelMax), `BouncingBullets`, `Glue` (EngineShutdown) all need new component classes before their stub appliers can do anything meaningful.
- **Out-of-scope (🚫):** see [`out-of-scope.md`](out-of-scope.md). Cut from this tracker and the `.groovy` sources in the typed-DSL migration sweep.
- **In-scope but unstarted (❌):** `[Spawn]` (12 keys, today driven by hardcoded `centerOfArena`), several `[Misc]` rows that are unauthored.
- **Test coverage:** 0 rows have any automated test today. Per the spawn-projection-test-gap, manual launch is the only verification path. Filling the Test column is a separate workstream.
