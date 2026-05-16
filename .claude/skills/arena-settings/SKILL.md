---
name: arena-settings
description: Work with Subspace Infinity arena settings — the per-arena `arena.groovy` files under `zone/arenas/`, the Groovy `conf/` preset fragment library (section/shipSection/shipSections DSL), the recursive `include` directive, the `SettingsSystem` typed accessors, and the `~loadArena`/`~swapMap` commands. Use when adding or reading settings, creating a new arena, or splitting settings fragments.
---

# Arena Settings

A **Zone** (server) contains many **Arenas**. An Arena = one **Map** (`.lvl`) + one **settings bundle** (the fragments its `arena.groovy` pulls in via `includeFragment`). This skill covers the settings half.

## Where things live

```
zone/
├── zone.groovy                       # zone-wide config (autoLoad, enterSpawn) — see GroovyZoneLoader
├── arenas/
│   ├── (default)/arena.groovy        # fallback when arena loaded by unknown name — mirrors testarena content
│   ├── testarena/arena.groovy        # canonical "everything live" showcase + zone.groovy enterSpawn target
│   ├── trench/arena.groovy
│   └── deva/arena.groovy
└── conf/
    ├── base/                         # PROJECT baseline — current Infinity tuning (Groovy, 7 ships)
    │   ├── ship-warbird.groovy
    │   ├── ship-javelin.groovy ...   # (7 ships — no shark)
    │   ├── prizeweights.groovy
    │   ├── cost.groovy
    │   └── misc.groovy
    ├── svs/                          # CANONICAL Standard VIE Settings (Groovy, 8 ships)
    │   ├── ship-warbird.groovy
    │   ├── ship-javelin.groovy ...   # (8 ships — includes shark)
    │   ├── prizeweights.groovy
    │   ├── cost.groovy
    │   └── misc.groovy
    ├── svs-league/                   # SVS league / duel variant
    │   ├── svs-league.groovy         # composite root: include directives + shipSections splat
    │   ├── svs-dueling.groovy
    │   ├── ship-warbird.groovy ...   # per-ship overrides
    │   └── misc.groovy, prizeweights.groovy, cost.groovy
    ├── svs-pb/                       # PowerBall approximation
    ├── deva-04-2026/                 # the deva preset (per-arena tuning)
    └── trench-04-2026/               # the trench preset
```

**All authoring is Groovy.** The zone-scope, arena-scope, and preset fragment tiers are all `.groovy` files. Arenas list individual per-section fragment paths under `includeFragment`; composite `.conf` files, the `IniLoader` `#include` preprocessor, and the `.ini`/`.cfg`/`.conf` asset-loader registration are all gone (deleted in v1.0.10). `SettingsSystem.loadFragmentIni` now rejects non-`.groovy` paths.

**Variant pick-up** — an arena that wants PowerBall-style tuning lists per-section fragments:
```groovy
arena {
    map 'pb-map.lvl'
    includeFragment '/conf/svs-pb/misc.groovy'
    includeFragment '/conf/svs-pb/prizeweights.groovy'
    includeFragment '/conf/svs-pb/cost.groovy'
}
```
A league arena that uses the composite root (which `include`s its section files internally):
```groovy
arena {
    map 'duel-map.lvl'
    includeFragment '/conf/svs-league/svs-league.groovy'
}
```

`zone/` is added as a resource source dir by [buildSrc/.../infinity.app-with-assets.gradle:16](../../../buildSrc/src/main/groovy/infinity.app-with-assets.gradle#L16), so JME's `AssetManager` and Groovy-classpath reads see everything under `zone/` on the classpath. Asset keys are relative to that root: `arenas/{name}/arena.groovy`, `conf/svs/cost.groovy`.

Supporting Java in [infinity-server/src/main/java/infinity/settings/](../../../infinity-server/src/main/java/infinity/settings/):

| File | Purpose |
|---|---|
| `GroovySettingsHost` | Stateless pipeline that evaluates a Groovy settings file end to end: filesystem-first source resolution, sandboxed `GroovyShell` build, eval, error classification. Shared by every loader below. |
| `GroovySettingsAdapter` | Per-file DSL contract: each loader implements this to declare allowed imports, bind script variables, and extract its typed result from the accumulator. |
| `GroovyZoneLoader` | Loads `zone.groovy` → `ZoneConfig`. |
| `GroovyArenaLoader` | Loads `arenas/<name>/arena.groovy` → `ArenaConfig`. |
| `GroovyShipLoader` | Loads each arena's `ships.groovy` (referenced via `arena { shipsScript ... }`) → per-arena `ConfigRegistry`. |
| `GroovyFragmentLoader` | Evaluates `.groovy` preset fragments (`section`, `shipSection`, `shipSections`, `include`). Returns an `Ini`-shaped result for `SettingsSystem`. Dispatched from `SettingsSystem.loadFragments`; Groovy is the only fragment format supported (no `.ini` / `.cfg` / `.conf` ingest path remains). |
| `BulletAdapter`, `BombAdapter`, `MineAdapter`, `BurstAdapter`, `RepelAdapter`, `PrizeAdapter`, `SpawnAdapter` | Typed-DSL adapters (B1). Each parses its named typed fragment (`bullet.groovy`, `bomb.groovy`, `spawn.groovy`, …) and writes the result directly into the matching `ConfigRegistry` slot via `with*`. Registered in `ConfigRegistrySystem.DISPATCH` keyed by basename. |
| `ConfigRegistry` | Immutable per-arena snapshot of typed `*Config` records. Direct slots: `ShipConfig` map (via `GroovyShipLoader`), plus per-section `BulletConfig`, `BombConfig`, `GravBombConfig`, `MineConfig`, `BurstFireConfig`, `RepelConfig`, `ThorConfig`, `PrizeConfig`, `SpawnConfig` (each populated by its typed adapter). Per-slot `with*` updaters return a new immutable copy. |
| `ConfigRegistrySystem` | Holds one `ConfigRegistry` per arena. Owns load orchestration via `load(arenaId, arenaConfig)`: two-phase sequence — legacy fragment `Ini` load (still needed for unmigrated polish-bag sections) → typed dispatch (ship + six weapon/prize adapters via `DISPATCH`). Single entry point for both initial arena load and hot-reload. Atomic-swap installs ensure readers see either the old or new snapshot, never a torn state. |

Runtime entry point: [infinity-server/src/main/java/infinity/systems/SettingsSystem.java](../../../infinity-server/src/main/java/infinity/systems/SettingsSystem.java).

## Identity rule

**An arena is identified by its folder name**, not its map. `arenas/trench/arena.groovy` defines arena `trench`; its `map` directive decides which `.lvl` it loads. Two arenas can share a map file (different tuning, different settings bundle) but never the same folder name. Invariant: the `<arenaName, map, conf-bundle>` triple is always unique.

`ArenaId.getArena()` returns the arena name (e.g. `trench`, `(default)`). The map filename, ships script, and spawn live on the typed `ArenaConfig` cached per-arena in `ArenaSystem`; the per-arena `Ini` cached in `SettingsSystem` carries only the included-fragment data.

## `arena.groovy` format

Typed Groovy DSL evaluated by `GroovyArenaLoader`. Every directive is optional; partial scripts are valid (omitted directives default to `ArenaConfig.EMPTY` values).

**Conventional shape:**
```groovy
arena {
    map '04-2026-trench/pub2025.lvl'
    shipsScript '/conf/trench-04-2026/ships.groovy'
    includeFragment '/conf/trench-04-2026/spawn.groovy'         // typed [Spawn] — per-team coords + radius; parsed by SpawnAdapter into SpawnConfig
    includeFragment '/conf/trench-04-2026/prizeweights.groovy'  // multiple allowed; last-wins on key conflict
    includeFragment '/conf/trench-04-2026/ship-warbird.groovy'
    // …
    includeFragment '/conf/trench-04-2026/misc.groovy'
    wallFriction 0.1
    shipRestrictions {
        // allow-list wins over deny-list; omit both to allow all ships
        allow Ship.WARBIRD, Ship.JAVELIN   // only these ships are allowed
        deny Ship.SPIDER                    // or: deny specific ships (when allow is empty)
        maxPerTeam Ship.WARBIRD, 4         // optional per-team cap; -1 = unrestricted
    }
    spawners {
        // Minimal: uses arena [PrizeWeight] defaults, global prize TTL
        spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 10000
        // With per-spawner weight overrides (sparse — other types keep arena defaults)
        spawn x: 900, z: 100, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 10000,
              weights: [Bomb: 100, Gun: 100, Rotation: 50]
    }
}
```

**`spawners` parameters:**
| Parameter | Type | Effect |
|---|---|---|
| `x`, `z` | int (arena-local) | Spawner center; `(0,0)` = NW, `(1024,1024)` = SE |
| `radius` | double (world units) | Base spawn radius. Effective = `radius + radiusPerPlayer × playersInArena` |
| `maxCount` | int | Base prize cap. Effective = `maxCount + countPerPlayer × playersInArena` |
| `intervalMs` | double | Milliseconds between spawn attempts |
| `ttlMs` | long | Prize `Decay` duration; `0` falls back to `PrizeConfig.defaultDecayMs` |
| `onRing` | boolean (default `false`) | `true` = spawn on ring edge; `false` = uniform within disc |
| `weights` | Map (optional) | Per-type weight overrides merged atop arena `[PrizeWeight]` defaults; sparse |
| `countPerPlayer` | int (default `0`) | Slice 8d: additive per-player count scaling. `0` = no scaling. Subspace `[Prize] PrizeFactor` analogue |
| `radiusPerPlayer` | double (default `0.0`) | Slice 8d: additive per-player radius scaling, world units. `0` = no scaling. Subspace `[Prize] UpgradeVirtual` analogue |
| `regenBatch` | int (default `1`) | Slice 8d: prizes spawned per `intervalMs` tick when below cap. Subspace `[Prize] PrizeHideCount` analogue |
| `hidden` | boolean (default `false`) | Slice 8d: spawned prizes carry an `infinity.es.Hidden` marker; client doesn't render them but server still owns collision / pickup. Reusable forward-compat marker for future cloak/decoy mechanics |

Each `spawn` entry materializes into a real spawner entity at arena-load. `PrizeWeightsOverride` is set on the entity only when `weights` is non-empty. Player-count for scaling is per-arena (filtered on `ArenaId`); spawners without an `ArenaId` (legacy `BasicEnvironment`, test modules) collapse to no-scaling.

**Required for a playable arena:** `map`. Without `shipsScript` the arena gets `GroovyShipLoader.FALLBACK`; without `includeFragment` no rule sections are loaded; without a `spawn.groovy` typed fragment (or a legacy `ArenaConfig.spawnX/spawnZ` fallback) players spawn at the arena's center `(512, 512)`. `spawners` is optional; omitting it leaves prize spawning to any globally-configured spawners.

### Where each directive lands

| Directive | Type | Stored on | Read by |
|---|---|---|---|
| `map '...'` | String | `ArenaConfig.mapFile()` | `ArenaSystem` (load / unload / swap / `findArenaByMap`) |
| `shipsScript '...'` | String | `ArenaConfig.shipsScript()` | `GroovyShipLoader.apply()` at arena-load |
| `includeFragment '/conf/.../spawn.groovy'` | String | `ConfigRegistry.spawn()` → `SpawnConfig` | `ArenaSystem.getArenaSpawn(arenaName, freq)` — per-team spawn point selected by `freq % teams.size()`; falls back to `ArenaConfig.spawnX()/spawnZ()` when `SpawnConfig.teams` is empty |
| `wallFriction N` | double [0,1] | `ArenaConfig.wallFriction()` | `ContactSystem` (body-vs-static contacts: damps tangential velocity; friction=0 prevents torque from off-center contacts) |
| `includeFragment '...'` | String (repeatable) | `ArenaConfig.fragmentIncludes()` → forwarded to `SettingsSystem.loadFragments` | Anything that calls `SettingsSystem.getInt/getString(arenaName, section, key, default)` |
| `shipRestrictions { allow / deny / maxPerTeam }` | Block (optional) | `ArenaConfig.shipRestrictions()` → `ShipRestrictionsConfig` → installed into `ConfigRegistry`; Infinity-only, no Subspace canon | `ConfigShipRestrictor` (called from `AvatarSystem` on ship-change; also enforces full-energy gate per `EnterShipEnergy=100%`) |
| `spawners { spawn ... }` | Block (repeatable) | `ArenaConfig.spawners()` → `List<SpawnerSpec>` → materialized into spawner entities by `ArenaSystem.doLoad` | `PrizeSpawnerSystem` (picks prizes; reads per-spawner `PrizeWeightsOverride` merged atop arena `[PrizeWeight]` defaults) |

> **Forward-ref:** [ADR-0008](../../../docs/adr/0008-arena-composition-and-modules.md) designs additional `arena.groovy` statements for arena-composition modules — `scoring '…'`, `winCondition '…'`, `mechanic '…'`, `teamSetup '…'`, `roster '…'`, `respawnPolicy '…'`, `roundStructure '…'`, `matchStructure '…'`, `spawnPlacement '…'`, `shop '…'`, plus `usePreset '…'` for module bundles. **Not yet wired** — directives above are the v1 surface. When the loader lands, this table extends to cover the module statements.

## Groovy fragment DSL

Each `.groovy` fragment under `conf/<preset>/` declares one or more sections. Loaded by `GroovyFragmentLoader` and merged into the arena's `Ini` (last-wins on key conflict, same as the legacy `#include`).

```groovy
// /conf/svs/misc.groovy — generic [Section] form
section('Bomb') {
    BombDamageLevel 750
    BombAliveTime   6000
    JitterTime      72
}
section('Mine') {
    MineAliveTime 12000
    TeamMaxMines  12
}
```

```groovy
// /conf/svs/ship-warbird.groovy — per-ship section
shipSection('Warbird') {
    SuperTime         6000
    BulletFireEnergy  20
    MaximumSpeed      3250
    // …
}
```

```groovy
// /conf/svs-pb/ships.groovy — splat one block over multiple ship sections
shipSections('Warbird', 'Javelin', 'Spider', 'Leviathan',
             'Terrier', 'Weasel', 'Lancaster', 'Shark') {
    MaximumSpeed   3250
    InitialBounty  100
}
```

```groovy
// /conf/svs-league/svs-league.groovy — composite + override (uses include)
include '/conf/svs-league/ship-warbird.groovy'
include '/conf/svs-league/misc.groovy'
include '/conf/svs-league/cost.groovy'

shipSections('Warbird', 'Javelin', /* …8 ships */) {
    RepelMax 2
}
section('Prize') { UseDeathPrizeWeights 1 }
```

**Authoring rules:**
- Section names: any string in `section(...)`; for `shipSection` and `shipSections`, names are validated against the `Ship` enum at parse time so typos fail loudly.
- Inside a section block, every line is `KeyName value` — Groovy command-style call. Negatives need parens: `DoorMode(-1)` (bare `DoorMode -1` parses as subtraction).
- Strings are quoted: `SheepMessage 'Baaah'`.
- `include '/conf/.../foo.groovy'` recurses (cycle-detected, max depth 16). Used for compose-and-override patterns; otherwise list section files directly under `arena.groovy`'s `includeFragment` instead.

Canonical Subspace key list per section: [`.scratch/subspace-ini-reference/server-defaults.md`](../../../.scratch/subspace-ini-reference/server-defaults.md) — extracted from the historical `SettingsTypes.java` catalog (deleted in v1.0.8) plus on-disk `.sss` / `.set` server defaults.

### Section categories

1. **Global rule sections** (one per arena): `Bullet`, `Bomb`, `Mine`, `Shrapnel`, `Burst`, `Prize`, `PrizeWeight`, `DPrizeWeight`, `Flag`, `Soccer`, `Radar`, `Team`, `Kill`, `Repel`, `Message`, `Wormhole`, `Latency`, `Brick`, `Rocket`, `Door`, `Misc`, `Custom`, `Territory`, `Periodic`, `Security`, `PacketLoss`, `Routing`, `King`, `Cost`, `Toggle`, `Spawn`, `Spectator`, `General`.
2. **Per-ship sections** (use `shipSection` / `shipSections`): `Warbird`, `Javelin`, `Spider`, `Leviathan`, `Terrier`, `Weasel`, `Lancaster`, `Shark`. Same key set, per ship — `MaximumSpeed`, `InitialEnergy`, `BombFireEnergy`, etc.
3. **`Owner`** — free-form metadata.

## Loading an arena's settings

Arena-load flow in [ArenaSystem.doLoad](../../../infinity-server/src/main/java/infinity/systems/ArenaSystem.java):

1. `GroovyArenaLoader.load(arenaName)` reads `arenas/{arenaName}/arena.groovy`.
2. Parsed `ArenaConfig` is cached on the per-arena `ArenaRecord` (single source of truth for in-memory arena state — `getArenaSpawn`, `swapMap`, etc. all read from it).
3. `SettingsSystem.loadFragments(arenaName, cfg.fragmentIncludes())` loads each fragment and merges into the per-arena `Ini` for `getInt`/`getString` consumers. Only `.groovy` paths are supported — `GroovyFragmentLoader` recurses on `include` directives (cycle-detected, max depth 16). Non-`.groovy` paths log a warning and are skipped.

A missing `arena.groovy` fails the load with a clear "No arena.groovy found" error — the legacy `arena.conf` path was retired in zone-arena-to-groovy #3.

Programmatic load (e.g. server startup):
```java
getSystem(ArenaSystem.class).loadArena("trench");
```

Chat:
```
~loadArena trench                  → reads arenas/trench/arena.groovy, loads its map
~loadMap trench.lvl                → shortcut: uses map base name as arena name
~swapMap <arenaName> <newMap>      → replace an open arena's map (same slot, same settings)
~unloadMap <mapFile>               → looks up the arena currently using that map, unloads
```

## Reading settings — typed accessors

For the **arena-scope core** (map / shipsScript / spawn / wallFriction), prefer the typed `ArenaConfig` access via `ArenaSystem` rather than reaching into `SettingsSystem`:

```java
ArenaSystem arenas = getSystem(ArenaSystem.class);
Vec3d spawn = arenas.getArenaSpawn(arenaName, freq); // freq = player's team frequency; wraps via SpawnConfig.forFreq
ArenaConfig cfg = arenas.getArenaConfig(arenaName); // returns ArenaConfig.EMPTY when not loaded
double friction = cfg.wallFriction();
```

For **fragment data** (the rule sections in the included `conf/<preset>/*.groovy` files), use `SettingsSystem`'s typed accessors:

```java
SettingsSystem s = getSystem(SettingsSystem.class);

int bombDamage = s.getInt(arenaName, "Bomb", "BombDamageLevel", 1000);
boolean carryFlags = s.getBool(arenaName, "Flag", "CarryFlags", true);
MyMode mode = s.getEnum(arenaName, "Soccer", "Mode", MyMode.DEFAULT);
```

- `getInt` falls back to bool aliases on parse failure (`Y/Yes/True/On/1` → 1, `N/No/False/Off/0` → 0).
- `getBool` accepts the same aliases, case-insensitive.
- `getEnum` matches enum constant names case-insensitive.
- All accessors return the provided default when the key is absent.

## Hot-reloading edits

Edits to a fragment file (e.g. `misc.groovy`) are picked up automatically by the file watcher in `ArenaSystem` — default poll interval 5s, configurable via `zone.groovy`'s `scriptPollIntervalNanos`. On change, `ConfigRegistrySystem.load` re-runs the full two-phase orchestration and atomic-swaps a fresh snapshot. Consumers re-read on next consumption — no event/callback fires (the listener API was retired pre-B0). For ship config (`ships.groovy`), the watcher additionally calls `ShipSpawnSystem.reprojectAll()` so live ships pick up the new stats without respawning.

For mid-session ad-hoc tuning without editing files, the typed `~set` admin command is planned as a future follow-on; not available today.

The arena-scope core (map / shipsScript / spawn / wallFriction) lives on the typed `ArenaConfig` and is set at load-time only. `~swapMap` is the chat command for changing the map of an already-open arena; it updates `ArenaConfig` directly without re-running the fragment loader.

## Common patterns

### Create a new arena

1. `mkdir zone/arenas/{arenaName}/` — folder name = arena identity (not necessarily a map basename).
2. Create `arena.groovy`:
   ```groovy
   arena {
       map 'your-map.lvl'
       shipsScript '/conf/{preset}/ships.groovy'        // or omit for the GroovyShipLoader.FALLBACK
       includeFragment '/conf/{preset}/spawn.groovy'    // per-team spawn coords; omit to fall back to ArenaConfig.spawnX/spawnZ default (512, 512)
       includeFragment '/conf/base/prizeweights.groovy' // list each section file you want
       includeFragment '/conf/base/ship-warbird.groovy'
       // …
       includeFragment '/conf/base/misc.groovy'
   }
   ```
3. Put the `.lvl` in `assets/Maps/`.
4. Add to `zone.groovy`'s `autoLoad` if it should boot automatically, or `~loadArena {arenaName}` at runtime.

### Override a fragment value for one arena

`arena.groovy`'s `includeFragment` is positional, last-wins on key conflict. Author a small sibling fragment that overrides only the keys you want changed and list it after the base in `arena.groovy`:

```groovy
// /conf/{your-preset}/overrides.groovy
section('Bomb') {
    BombDamageLevel 2000
}
```

```groovy
// arenas/your-arena/arena.groovy
arena {
    map 'your-map.lvl'
    includeFragment '/conf/base/misc.groovy'
    includeFragment '/conf/{your-preset}/overrides.groovy'   // wins on key conflict
}
```

### Create a new preset variant (e.g. `svs-arcade`)

Two shapes — pick whichever is cleaner.

**A. Per-section split** (the shape `svs/`, `trench-04-2026/`, etc. use today). One file per section / per ship; arenas list each one under `includeFragment`.

```
zone/conf/svs-arcade/
├── ship-warbird.groovy
├── ship-javelin.groovy
├── …
├── misc.groovy
├── prizeweights.groovy
└── cost.groovy
```

**B. Composite-with-overrides** (the shape `svs-league/svs-league.groovy` uses). One root `.groovy` that `include`s the section files of a base preset and layers overrides on top. Arenas reference just the root file.

```groovy
// /conf/svs-arcade/svs-arcade.groovy
include '/conf/svs/ship-warbird.groovy'
include '/conf/svs/misc.groovy'
include '/conf/svs/prizeweights.groovy'
include '/conf/svs/cost.groovy'

// Arcade overrides — written after the includes so they win on key conflict.
section('Bomb') {
    BombDamageLevel 2500
}
shipSections('Warbird', 'Javelin', /* …8 ships */) {
    InitialBounty 200
}
```
```groovy
// arenas/arcade/arena.groovy
arena {
    map 'arcade-map.lvl'
    includeFragment '/conf/svs-arcade/svs-arcade.groovy'
}
```

### Read a setting in an `AbstractGameSystem`

```java
int enterDelay = getSystem(SettingsSystem.class)
    .getInt(arenaName, "Kill", "EnterDelay", 200);
```

Or — if you need many keys at once — pull the whole `Ini` once via `SettingsSystem.getIni(arenaName)`, cache what you need, re-fetch on `SettingListener` callback.

## Asset-loader registration

`SettingsSystem.initialize()` is currently a no-op — there are no settings-file `AssetLoader` registrations to maintain. Groovy files are read directly by `GroovySettingsHost` (filesystem dev path + classpath fallback); the formerly-registered `IniLoader` (`.ini`/`.cfg`/`.conf`) and `SSSLoader` (`.sss`/`.set`) were both retired in v1.0.10 once Groovy became the only supported authoring format. If a future settings file needs registration, do it here and add the corresponding loader class next to `GroovyFragmentLoader`.

## Anti-patterns

- **Don't hardcode game constants** that already exist in a fragment — route through `SettingsSystem.getInt(...)` / `getBool(...)` so operators can tune without recompiling.
- **Don't put tuning knobs in `arena.conf`** — that file is gone. Tuning knobs go in the appropriate Groovy preset (e.g. `conf/<preset>/ships.groovy` for ship stats, `arena.groovy` for arena-scope core, `zone.groovy` for zone-wide).
- **Don't duplicate the SVS baseline** inside arena fragments — list the per-section files from `/conf/svs/` under `includeFragment` (or `include` them from a per-preset composite root) and layer overrides below.
- **Don't mint new section names** in fragments — prefer existing Subspace section/key conventions catalogued in [`.scratch/subspace-ini-reference/server-defaults.md`](../../../.scratch/subspace-ini-reference/server-defaults.md) so configs stay interoperable with legacy Subspace tooling.
- **Don't poll `getIni` every tick** — cache locally; rely on the hot-reload pipeline to re-derive typed records into `ConfigRegistry` when fragments change.
- **Don't reach into `WorldGrids.*` for sizes** — go through `InfinityConstants.*` (see [subspace-moss-terminology](../subspace-moss-terminology/SKILL.md)).

## When this skill applies

- Adding or editing values in an `arena.groovy` or a fragment.
- Creating a new arena folder or a new `conf/{variant}/` family.
- Implementing a system that reads ship/weapon/prize constants.
- Building a setting-edit command or runtime tuning UI.
- Debugging "setting not found" or "no arena.groovy found" errors.
