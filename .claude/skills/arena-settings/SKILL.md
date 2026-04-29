---
name: arena-settings
description: Work with Subspace Infinity arena settings — the per-arena `arena.groovy` files under `infinity/zone/arenas/`, the still-INI `conf/` fragment library, the `#include` preprocessor (used inside fragments), the `SettingsSystem` typed accessors, the `~loadArena`/`~swapMap` commands, and the `SettingListener` API. Use when adding or reading settings, creating a new arena, splitting settings fragments, or listening for setting changes.
---

# Arena Settings

A **Zone** (server) contains many **Arenas**. An Arena = one **Map** (`.lvl`) + one **settings bundle** (the fragments its `arena.groovy` pulls in via `includeFragment`). This skill covers the settings half.

## Where things live

```
infinity/zone/
├── zone.groovy                       # zone-wide config (autoLoad, enterSpawn) — see GroovyZoneLoader
├── arenas/
│   ├── (default)/arena.groovy        # thin: includeFragment + map (override)
│   ├── trench/arena.groovy
│   └── deva/arena.groovy
└── conf/
    ├── base/                         # PROJECT baseline — current Infinity tuning (still INI)
    │   ├── base.conf                 # composite root (#includes all fragments)
    │   ├── ship-warbird              # extensionless fragments
    │   ├── ship-javelin ...          # (7 ships — no shark yet)
    │   ├── prizeweights
    │   ├── cost
    │   └── misc
    ├── svs/                          # CANONICAL Standard VIE Settings (still INI)
    │   ├── svs.conf                  # verbatim from SubspaceServer
    │   ├── ship-warbird              # (8 ships — includes shark)
    │   ├── ship-javelin ...
    │   ├── prizeweights
    │   ├── cost
    │   └── misc
    ├── svs-league/                   # SVS league / duel variant
    ├── svs-pb/                       # PowerBall approximation
    ├── svs-tce/                      # Turf Classic East
    ├── svs-turf/                     # post-VIE Turf Zone
    ├── deva-04-2026/                 # the deva preset (per-arena tuning)
    └── trench-04-2026/               # the trench preset
```

**Two authoring formats, by tier.** The arena-scope core (map / shipsScript / spawn / fragment list) and zone-scope config (`autoLoad`, `enterSpawn`) are typed Groovy. The preset fragments under `conf/<preset>/` are still INI (out of scope of the zone-arena-to-groovy migration; tracked as a future item). Arenas pull the fragments in via `includeFragment`; the fragments themselves keep using `#include` recursively.

**Variant pick-up** — an arena that wants PowerBall-style tuning:
```groovy
arena {
    map 'pb-map.lvl'
    includeFragment '/conf/svs-pb/svs.conf'
}
```
A duel arena:
```groovy
arena {
    map 'duel-map.lvl'
    includeFragment '/conf/svs-league/svs-dueling.conf'
}
```

`zone/` is added as a resource source dir by [buildSrc/.../infinity.app-with-assets.gradle:16](../../../buildSrc/src/main/groovy/infinity.app-with-assets.gradle#L16), so JME's `AssetManager` and Groovy-classpath reads see everything under `zone/` on the classpath. Asset keys are relative to that root: `arenas/{name}/arena.groovy`, `conf/svs/svs.conf`.

Supporting Java in [infinity/src/main/java/infinity/settings/](../../../infinity/src/main/java/infinity/settings/):

| File | Purpose |
|---|---|
| `GroovyZoneLoader` | Loads `zone.groovy` → `ZoneConfig`. Filesystem-first dev mode, classpath fallback. |
| `GroovyArenaLoader` | Loads `arenas/<name>/arena.groovy` → `ArenaConfig`. Same pattern as `GroovyZoneLoader`. |
| `GroovyShipLoader` | Loads each arena's `ships.groovy` (referenced via `arena { shipsScript ... }`) → per-arena `ConfigRegistry`. |
| `IniLoader` | `AssetLoader` for `.ini` / `.cfg` / `.conf`. Expands `#include` directives before parsing with `ini4j`. Used for the still-INI preset fragments. |
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
    spawn 1000, 20                                    // arena-local; (0,0)=NW, (1024,1024)=SE
    includeFragment '/conf/trench-04-2026/trench.conf' // still-INI preset fragment
    // includeFragment '/conf/another.conf'           // multiple allowed; last-wins on key conflict
}
```

**Required for a playable arena:** `map`. Without `shipsScript` the arena gets `GroovyShipLoader.FALLBACK`; without `includeFragment` no INI rule sections are loaded; without `spawn` the arena spawns at `(0, 0)`.

### Where each directive lands

| Directive | Type | Stored on | Read by |
|---|---|---|---|
| `map '...'` | String | `ArenaConfig.mapFile()` | `ArenaSystem` (load / unload / swap / `findArenaByMap`) |
| `shipsScript '...'` | String | `ArenaConfig.shipsScript()` | `GroovyShipLoader.apply()` at arena-load |
| `spawn x, z` | int, int | `ArenaConfig.spawnX()`/`spawnZ()` | `ArenaSystem.getArenaSpawn()` |
| `includeFragment '...'` | String (repeatable) | `ArenaConfig.fragmentIncludes()` → forwarded to `SettingsSystem.loadFragments` | Anything that calls `SettingsSystem.getInt/getString(arenaName, section, key, default)` |

### Section categories (in fragments)

The fragments themselves are still INI and obey the standard Subspace section layout:

1. **Global rule sections** (one per arena): `[Bullet]`, `[Bomb]`, `[Mine]`, `[Shrapnel]`, `[Burst]`, `[Prize]`, `[PrizeWeight]`, `[Flag]`, `[Soccer]`, `[Radar]`, `[Team]`, `[Kill]`, `[Repel]`, `[Message]`, `[Wormhole]`, `[Latency]`, `[Brick]`, `[Rocket]`, `[Door]`, `[Misc]`, `[Custom]`, `[Territory]`, `[Periodic]`, `[Security]`, `[PacketLoss]`, `[Routing]`, `[King]`, `[Cost]`, `[Owner]`, `[Toggle]`, `[General]`.
2. **Per-ship sections**: `[Warbird]`, `[Javelin]`, `[Spider]`, `[Leviathan]`, `[Terrier]`, `[Weasel]`, `[Lancaster]`, `[Shark]`. Same key set, per ship — keys like `MaximumSpeed`, `InitialEnergy`, `BombFireEnergy`.
3. **`[Owner]`** — free-form metadata.

Canonical key list per section: [SettingsTypes.java](../../../infinity/src/main/java/infinity/settings/SettingsTypes.java).

## `#include` preprocessor (fragments only)

`IniLoader` runs a lightweight preprocessor inside fragments before handing text to `ini4j`. The arena-scope `includeFragment` directive is not the same thing — it's the Groovy-side handle that names which fragments to feed in.

- `#include /conf/svs/svs.conf` — **absolute** path from asset root (preferred for library fragments)
- `#include flags.conf` — **relative** to the including fragment's folder (preferred for sibling overrides)
- Quoted paths ok: `#include "some path.conf"`
- Cycles detected and rejected with the full chain
- Max depth 16

ini4j doesn't do `#include` natively; it's all in [IniLoader](../../../infinity/src/main/java/infinity/settings/IniLoader.java). Don't reach around it — put new shared fragments under `conf/` and `#include` them from a sibling fragment, or feed them via `includeFragment` in `arena.groovy`.

## Loading an arena's settings

Arena-load flow in [ArenaSystem.doLoad](../../../infinity/src/main/java/infinity/systems/ArenaSystem.java):

1. `GroovyArenaLoader.load(arenaName)` reads `arenas/{arenaName}/arena.groovy`.
2. Parsed `ArenaConfig` is cached on the per-arena `ArenaRecord` (single source of truth for in-memory arena state — `getArenaSpawn`, `swapMap`, etc. all read from it).
3. `SettingsSystem.loadFragments(arenaName, cfg.fragmentIncludes())` loads each fragment via `IniLoader` (so each fragment's own `#include`s expand) and merges them into the per-arena `Ini` for `getInt`/`getString` consumers.

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

For the **arena-scope core** (map / shipsScript / spawn), prefer the typed `ArenaConfig` access via `ArenaSystem` rather than reaching into `SettingsSystem`:

```java
ArenaSystem arenas = getSystem(ArenaSystem.class);
Vec3d spawn = arenas.getArenaSpawn(arenaName);
// Direct ArenaConfig access if you need other fields — currently package-private
// to ArenaSystem; expose getters as needed.
```

For **fragment data** (the rule sections in the included `conf/<preset>/*.conf` files), use `SettingsSystem`'s typed accessors:

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

The arena-scope core (map / shipsScript / spawn) is not editable through `setSetting` — it lives on the typed `ArenaConfig` and is set at load-time only. `~swapMap` is the chat command for changing the map of an already-open arena; it updates `ArenaConfig` directly, no `SettingListener` fires.

## Common patterns

### Create a new arena

1. `mkdir infinity/zone/arenas/{arenaName}/` — folder name = arena identity (not necessarily a map basename).
2. Create `arena.groovy`:
   ```groovy
   arena {
       map 'your-map.lvl'
       shipsScript '/conf/{preset}/ships.groovy'   // or omit for the GroovyShipLoader.FALLBACK
       spawn 100, 100                              // arena-local NW=(0,0), SE=(1024,1024)
       includeFragment '/conf/base/base.conf'      // or /conf/svs/svs.conf for canonical VIE
   }
   ```
3. Put the `.lvl` in `infinity/assets/Maps/`.
4. Add to `zone.groovy`'s `autoLoad` if it should boot automatically, or `~loadArena {arenaName}` at runtime.

### Override a fragment value for one arena

`arena.groovy`'s `includeFragment` doesn't support inline overrides today (the fragments are merged whole). To override a single key, author a small sibling fragment that does the overriding:

```
infinity/zone/conf/{your-preset}/
├── overrides.conf       ; e.g. [Bomb] BombDamageLevel = 2000
└── ...
```

Then in `arena.groovy`:
```groovy
arena {
    includeFragment '/conf/base/base.conf'
    includeFragment '/conf/{your-preset}/overrides.conf'   // last-wins
}
```

### Create a new conf variant (e.g. `svs-arcade`)

Same as before — fragment library is still INI:

1. `mkdir infinity/zone/conf/svs-arcade/`.
2. Inside, create a composite root (`svs-arcade.conf`) that `#include`s shared base fragments from `/conf/svs/` and overrides what differs:
   ```ini
   #include /conf/svs/prizeweights
   #include /conf/svs/ship-warbird
   ; ...
   #include /conf/svs-arcade/misc    ; arcade-tuned version instead of /conf/svs/misc
   ```
3. Arena `arena.groovy` references it:
   ```groovy
   arena {
       map '...'
       includeFragment '/conf/svs-arcade/svs-arcade.conf'
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
- **Don't duplicate the SVS baseline** inside arena fragments — `includeFragment '/conf/svs/svs.conf'` and layer overrides below.
- **Don't mint new section names** in fragments — prefer existing Subspace section/key conventions from `SettingsTypes` so configs stay interoperable with legacy Subspace tooling.
- **Don't bypass `SettingListener`** by polling `getIni` every tick — cache locally and update on callback.
- **Don't reach into `WorldGrids.*` for sizes** — go through `InfinityConstants.*` (see [subspace-moss-terminology](../subspace-moss-terminology/SKILL.md)).

## When this skill applies

- Adding or editing values in an `arena.groovy` or a fragment.
- Creating a new arena folder or a new `conf/{variant}/` family.
- Implementing a system that reads ship/weapon/prize constants.
- Building a setting-edit command or runtime tuning UI.
- Debugging "setting not found", "no arena.groovy found", or `ArenaSettings` with a null `Ini`.
