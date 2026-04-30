---
name: arena-settings
description: Work with Subspace Infinity arena settings — the per-arena `arena.groovy` files under `infinity/zone/arenas/`, the Groovy `conf/` preset fragment library (section/shipSection/shipSections DSL), the recursive `include` directive, the `SettingsSystem` typed accessors, the `~loadArena`/`~swapMap` commands, and the `SettingListener` API. Use when adding or reading settings, creating a new arena, splitting settings fragments, or listening for setting changes.
---

# Arena Settings

A **Zone** (server) contains many **Arenas**. An Arena = one **Map** (`.lvl`) + one **settings bundle** (the fragments its `arena.groovy` pulls in via `includeFragment`). This skill covers the settings half.

## Where things live

```
infinity/zone/
├── zone.groovy                       # zone-wide config (autoLoad, enterSpawn) — see GroovyZoneLoader
├── arenas/
│   ├── (default)/arena.groovy        # thin: includeFragment list + map
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
    ├── svs-tce/                      # Turf Classic East
    ├── svs-turf/                     # post-VIE Turf Zone
    ├── deva-04-2026/                 # the deva preset (per-arena tuning)
    └── trench-04-2026/               # the trench preset
```

**All authoring is Groovy.** The zone-scope, arena-scope, and preset fragment tiers are all `.groovy` files. Arenas list individual per-section fragment paths under `includeFragment`; composite `.conf` files and the `IniLoader` `#include` preprocessor are gone from the preset tier. The `IniLoader` itself remains registered for `.ini`/`.cfg`/`.conf` files (operator-supplied settings; legacy compatibility) but no preset fragments use it.

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

Supporting Java in [infinity/src/main/java/infinity/settings/](../../../infinity/src/main/java/infinity/settings/):

| File | Purpose |
|---|---|
| `GroovyZoneLoader` | Loads `zone.groovy` → `ZoneConfig`. Filesystem-first dev mode, classpath fallback. |
| `GroovyArenaLoader` | Loads `arenas/<name>/arena.groovy` → `ArenaConfig`. Same pattern as `GroovyZoneLoader`. |
| `GroovyShipLoader` | Loads each arena's `ships.groovy` (referenced via `arena { shipsScript ... }`) → per-arena `ConfigRegistry`. |
| `GroovyFragmentLoader` | Evaluates `.groovy` preset fragments (`section`, `shipSection`, `shipSections`, `include`). Returns an `Ini`-shaped result for `SettingsSystem`. Dispatched from `SettingsSystem.loadFragments` when the path ends in `.groovy`. |
| `IniLoader` | `AssetLoader` for `.ini` / `.cfg` / `.conf`. Expands `#include` directives before parsing with `ini4j`. Still registered for operator-supplied or legacy files; no preset fragment uses it any longer. |
| `SSSLoader` | `AssetLoader` for `.sss` / `.set` (colon-delimited rows → `ArrayList<String[]>`). Setting-metadata sidecars. |
| `SettingListener` | Callback: `arenaSettingsChange(ArenaId, section, setting)` fired from `SettingsSystem.setSetting`. |
| `SettingsTypes` | Exhaustive enumeration of known Subspace setting keys — reference, not runtime. |

Runtime entry point: [infinity/src/main/java/infinity/systems/SettingsSystem.java](../../../infinity/src/main/java/infinity/systems/SettingsSystem.java).

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
    spawn 1000, 20                                              // player spawn point in this arena, arena-local: (0,0)=NW, (512,512)=center, (1024,1024)=SE
    includeFragment '/conf/trench-04-2026/prizeweights.groovy'  // multiple allowed; last-wins on key conflict
    includeFragment '/conf/trench-04-2026/ship-warbird.groovy'
    // …
    includeFragment '/conf/trench-04-2026/misc.groovy'
    wallFriction 0.1
}
```

**Required for a playable arena:** `map`. Without `shipsScript` the arena gets `GroovyShipLoader.FALLBACK`; without `includeFragment` no rule sections are loaded; without `spawn` players spawn at the arena's center `(512, 512)`.

### Where each directive lands

| Directive | Type | Stored on | Read by |
|---|---|---|---|
| `map '...'` | String | `ArenaConfig.mapFile()` | `ArenaSystem` (load / unload / swap / `findArenaByMap`) |
| `shipsScript '...'` | String | `ArenaConfig.shipsScript()` | `GroovyShipLoader.apply()` at arena-load |
| `spawn x, z` | int, int | `ArenaConfig.spawnX()`/`spawnZ()` | `ArenaSystem.getArenaSpawn()` — player spawn point in this arena, arena-local; defaults to `(512, 512)` (arena center) when omitted |
| `wallFriction N` | double [0,1] | `ArenaConfig.wallFriction()` | `ContactSystem` (body-vs-static contacts: damps tangential velocity; friction=0 prevents torque from off-center contacts) |
| `includeFragment '...'` | String (repeatable) | `ArenaConfig.fragmentIncludes()` → forwarded to `SettingsSystem.loadFragments` | Anything that calls `SettingsSystem.getInt/getString(arenaName, section, key, default)` |

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

Canonical Subspace key list per section: [SettingsTypes.java](../../../infinity/src/main/java/infinity/settings/SettingsTypes.java).

### Section categories

1. **Global rule sections** (one per arena): `Bullet`, `Bomb`, `Mine`, `Shrapnel`, `Burst`, `Prize`, `PrizeWeight`, `DPrizeWeight`, `Flag`, `Soccer`, `Radar`, `Team`, `Kill`, `Repel`, `Message`, `Wormhole`, `Latency`, `Brick`, `Rocket`, `Door`, `Misc`, `Custom`, `Territory`, `Periodic`, `Security`, `PacketLoss`, `Routing`, `King`, `Cost`, `Toggle`, `Spawn`, `Spectator`, `General`.
2. **Per-ship sections** (use `shipSection` / `shipSections`): `Warbird`, `Javelin`, `Spider`, `Leviathan`, `Terrier`, `Weasel`, `Lancaster`, `Shark`. Same key set, per ship — `MaximumSpeed`, `InitialEnergy`, `BombFireEnergy`, etc.
3. **`Owner`** — free-form metadata.

## Loading an arena's settings

Arena-load flow in [ArenaSystem.doLoad](../../../infinity/src/main/java/infinity/systems/ArenaSystem.java):

1. `GroovyArenaLoader.load(arenaName)` reads `arenas/{arenaName}/arena.groovy`.
2. Parsed `ArenaConfig` is cached on the per-arena `ArenaRecord` (single source of truth for in-memory arena state — `getArenaSpawn`, `swapMap`, etc. all read from it).
3. `SettingsSystem.loadFragments(arenaName, cfg.fragmentIncludes())` loads each fragment and merges into the per-arena `Ini` for `getInt`/`getString` consumers. Each include path is dispatched on extension: `.groovy` → `GroovyFragmentLoader` (recurses on `include` directives), anything else → `IniLoader` (legacy / operator-supplied INI).

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
Vec3d spawn = arenas.getArenaSpawn(arenaName);
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

## Writing & listening for changes

`setSetting` mutates the in-memory fragment Ini (the merged-fragment store under `arenaName`) and fires `SettingListener.arenaSettingsChange(arenaId, section, setting)` on every registered listener. Changes are **not persisted to disk** — session-only.

```java
SettingsSystem s = getSystem(SettingsSystem.class);
s.setSetting(arenaId, "Bomb", "BombDamageLevel", "1500");
```

Implement `SettingListener`:
```java
public class MyModule implements SettingListener {
  @Override
  public void arenaSettingsChange(ArenaId arenaId, String section, String setting) {
    if ("Bomb".equals(section) && "BombDamageLevel".equals(setting)) {
      int v = getSystem(SettingsSystem.class).getInt(
          arenaId.getArena(), section, setting, 1000);
      // update cached copy
    }
  }
}

getSystem(SettingsSystem.class).addListener(myModule);   // in initialize()
// ...
getSystem(SettingsSystem.class).removeListener(myModule); // in terminate()
```

Listeners are expected to **cache** their own copy — don't reach back into `SettingsSystem` on every gameplay read.

The arena-scope core (map / shipsScript / spawn / wallFriction) is not editable through `setSetting` — it lives on the typed `ArenaConfig` and is set at load-time only. `~swapMap` is the chat command for changing the map of an already-open arena; it updates `ArenaConfig` directly, no `SettingListener` fires.

## Common patterns

### Create a new arena

1. `mkdir infinity/zone/arenas/{arenaName}/` — folder name = arena identity (not necessarily a map basename).
2. Create `arena.groovy`:
   ```groovy
   arena {
       map 'your-map.lvl'
       shipsScript '/conf/{preset}/ships.groovy'        // or omit for the GroovyShipLoader.FALLBACK
       spawn 100, 100                                   // player spawn point in this arena, arena-local: NW=(0,0), center=(512,512), SE=(1024,1024)
       includeFragment '/conf/base/prizeweights.groovy' // list each section file you want
       includeFragment '/conf/base/ship-warbird.groovy'
       // …
       includeFragment '/conf/base/misc.groovy'
   }
   ```
3. Put the `.lvl` in `infinity/assets/Maps/`.
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
infinity/zone/conf/svs-arcade/
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

`SettingsSystem.initialize()` registers loaders on the server-side `AssetLoaderService`:

```java
assetLoader.registerLoader(IniLoader.class, "ini", "cfg", "conf");
assetLoader.registerLoader(SSSLoader.class, "sss", "set");
```

Adding a new settings file extension? Register it here — `AssetLoaderService` is one per `GameServer`. Groovy files are read directly by the Groovy loaders (filesystem dev path + classpath fallback), they don't go through `AssetLoaderService`.

## Anti-patterns

- **Don't hardcode game constants** that already exist in a fragment — route through `SettingsSystem.getInt(...)` / `getBool(...)` so operators can tune without recompiling.
- **Don't put tuning knobs in `arena.conf`** — that file is gone. Tuning knobs go in the appropriate Groovy preset (e.g. `conf/<preset>/ships.groovy` for ship stats, `arena.groovy` for arena-scope core, `zone.groovy` for zone-wide).
- **Don't duplicate the SVS baseline** inside arena fragments — list the per-section files from `/conf/svs/` under `includeFragment` (or `include` them from a per-preset composite root) and layer overrides below.
- **Don't mint new section names** in fragments — prefer existing Subspace section/key conventions from `SettingsTypes` so configs stay interoperable with legacy Subspace tooling.
- **Don't bypass `SettingListener`** by polling `getIni` every tick — cache locally and update on callback.
- **Don't reach into `WorldGrids.*` for sizes** — go through `InfinityConstants.*` (see [subspace-moss-terminology](../subspace-moss-terminology/SKILL.md)).

## When this skill applies

- Adding or editing values in an `arena.groovy` or a fragment.
- Creating a new arena folder or a new `conf/{variant}/` family.
- Implementing a system that reads ship/weapon/prize constants.
- Building a setting-edit command or runtime tuning UI.
- Debugging "setting not found", "no arena.groovy found", or `ArenaSettings` with a null `Ini`.
