---
name: arena-settings
description: Work with Subspace Infinity arena settings — the per-arena `arena.conf` INI files under `infinity/zone/arenas/`, the shared `conf/` fragment library, the `#include` preprocessor, the `SettingsSystem` typed accessors, the `~loadArena`/`~swapMap` commands, and the `SettingListener` API. Use when adding or reading settings, creating a new arena, splitting settings fragments, or listening for setting changes.
---

# Arena Settings

A **Zone** (server) contains many **Arenas**. An Arena = one **Map** (`.lvl`) + one **settings bundle** (the fragments its `arena.conf` pulls in via `#include`). This skill covers the settings half.

## Where things live

```
infinity/zone/
├── arenas/
│   ├── (default)/arena.conf          # thin: #include + Map= + overrides
│   └── trench/arena.conf
└── conf/
    ├── base/                         # PROJECT baseline — current Infinity tuning
    │   ├── base.conf                 # composite root (#includes all fragments)
    │   ├── ship-warbird              # extensionless fragments
    │   ├── ship-javelin ...          # (7 ships — no shark yet)
    │   ├── prizeweights
    │   ├── cost
    │   └── misc
    ├── svs/                          # CANONICAL Standard VIE Settings
    │   ├── svs.conf                  # verbatim from SubspaceServer
    │   ├── ship-warbird              # (8 ships — includes shark)
    │   ├── ship-javelin ...
    │   ├── prizeweights
    │   ├── cost
    │   └── misc
    ├── svs-league/                   # SVS league / duel variant
    │   ├── svs.conf                  # base league (includes league ship stats + misc)
    │   ├── svs-league.conf           # practice/league: ships start with items
    │   ├── svs-dueling.conf          # strict duel: no starting items, repels neutered
    │   ├── ship-items-league         # item-loadout fragment, included per ship
    │   └── ship-items-dueling
    ├── svs-pb/                       # PowerBall approximation (one ship-all blob)
    ├── svs-tce/                      # Turf Classic East
    └── svs-turf/                     # post-VIE Turf Zone
```

**Two baselines, four variants, one truth per folder.** `conf/base/` is what this project currently ships — diverges from canonical SVS (different prize weights, bounties, attach costs, burst damage). `conf/svs/` + variants are the community's canonical tunings copied verbatim from SubspaceServer; treat them as read-only reference. Arenas can `#include` any of them; today both shipping arenas pull from `/conf/base/base.conf`.

**Variant pick-up** — an arena that wants PowerBall-style tuning:
```ini
#include /conf/svs-pb/svs.conf
```
A duel arena:
```ini
#include /conf/svs-league/svs-dueling.conf
```

`zone/` is added as a resource source dir by [buildSrc/.../infinity.app-with-assets.gradle:16](../../../buildSrc/src/main/groovy/infinity.app-with-assets.gradle#L16), so JME's `AssetManager` sees everything under `zone/` on the classpath. Asset keys are relative to that root: `arenas/{name}/arena.conf`, `conf/svs/svs.conf`.

Supporting Java in [infinity/src/main/java/infinity/settings/](../../../infinity/src/main/java/infinity/settings/):

| File | Purpose |
|---|---|
| `IniLoader` | `AssetLoader` for `.ini` / `.cfg` / `.conf`. Expands `#include` directives before parsing with `ini4j`. |
| `SSSLoader` | `AssetLoader` for `.sss` / `.set` (colon-delimited rows → `ArrayList<String[]>`). Setting-metadata sidecars, not arena configs. |
| `SettingListener` | Callback: `arenaSettingsChange(ArenaId, section, setting)` fired from `SettingsSystem.setSetting`. |
| `SettingsTypes` | Exhaustive enumeration of known Subspace setting keys — reference, not runtime. |

Runtime entry point: [infinity/src/main/java/infinity/systems/SettingsSystem.java](../../../infinity/src/main/java/infinity/systems/SettingsSystem.java).

## Identity rule

**An arena is identified by its folder name**, not its map. `arenas/trench/arena.conf` defines arena `trench`; its `[General] Map=` key decides which `.lvl` it loads. Two arenas can share a map file (different tuning, different settings bundle) but never the same folder name. Invariant: the `<arenaName, map, conf-bundle>` triple is always unique.

`ArenaId.getArena()` returns the arena name (e.g. `trench`, `(default)`). The map filename lives in the Ini: `SettingsSystem.getString(arenaName, "General", "Map", ...)`.

## `arena.conf` format

Standard INI, parsed by `ini4j` after `#include` expansion.

**Required:**
```ini
[General]
Map=trench.lvl
```

**Conventional baseline:**
```ini
#include /conf/base/base.conf      ; project default
; or
#include /conf/svs/svs.conf        ; canonical VIE tuning (read-only reference)
```

**Optional overrides** — redeclare a section below the `#include` to override specific keys (ini4j honors later definitions):
```ini
#include /conf/svs/svs.conf

[Bomb]
BombDamageLevel=1500   ; override the default
```

### Section categories

1. **Global rule sections** (one per arena): `[Bullet]`, `[Bomb]`, `[Mine]`, `[Shrapnel]`, `[Burst]`, `[Prize]`, `[PrizeWeight]`, `[Flag]`, `[Soccer]`, `[Radar]`, `[Team]`, `[Kill]`, `[Repel]`, `[Message]`, `[Wormhole]`, `[Latency]`, `[Brick]`, `[Rocket]`, `[Door]`, `[Misc]`, `[Custom]`, `[Territory]`, `[Periodic]`, `[Security]`, `[PacketLoss]`, `[Routing]`, `[King]`, `[Cost]`, `[Owner]`, `[Toggle]`, `[General]`.
2. **Per-ship sections**: `[Warbird]`, `[Javelin]`, `[Spider]`, `[Leviathan]`, `[Terrier]`, `[Weasel]`, `[Lancaster]`, `[Shark]`. Same key set, per ship — keys like `MaximumSpeed`, `InitialEnergy`, `BombFireEnergy`.
3. **`[Owner]`** — free-form metadata.

Canonical key list per section: [SettingsTypes.java](../../../infinity/settings/SettingsTypes.java).

## `#include` preprocessor

`IniLoader` runs a lightweight preprocessor before handing text to `ini4j`:

- `#include /conf/svs/svs.conf` — **absolute** path from asset root (preferred for library fragments)
- `#include flags.conf` — **relative** to the including file's folder (preferred for arena-local overrides)
- Quoted paths ok: `#include "some path.conf"`
- Cycles detected and rejected with the full chain
- Max depth 16

**Style convention:**
- Absolute paths for pulling from the `/conf/` library.
- Relative paths for sibling files inside an arena's own folder.

ini4j doesn't do `#include` natively; it's all in [IniLoader](../../../infinity/src/main/java/infinity/settings/IniLoader.java). Don't reach around it — put new shared fragments under `conf/` and `#include` them.

## Loading an arena's settings

`SettingsSystem.loadSettings(requester, arenaName)`:

1. Asset-loads `/arenas/{arenaName}/arena.conf` (through `IniLoader`, so `#include`s expand).
2. On miss: falls back to `arenas/(default)/arena.conf` (deep-copied per arena so mutations don't leak across fallback consumers).
3. Caches the resulting `Ini` under `arenaName`.

Programmatic load (e.g. server startup):
```java
getSystem(ArenaSystem.class).loadArena("trench");
```

Chat:
```
~loadArena trench                  → reads arenas/trench/arena.conf, loads its Map=
~loadMap trench.lvl                → shortcut: uses map base name as arena name
~swapMap <arenaName> <newMap>      → replace an open arena's map (same slot, same settings)
~unloadMap <mapFile>               → looks up the arena currently using that map, unloads
```

## Reading settings — typed accessors

Prefer the typed accessors on [SettingsSystem](../../../infinity/src/main/java/infinity/systems/SettingsSystem.java) over raw Ini reads:

```java
SettingsSystem s = getSystem(SettingsSystem.class);

int bombDamage = s.getInt(arenaName, "Bomb", "BombDamageLevel", 1000);
boolean carryFlags = s.getBool(arenaName, "Flag", "CarryFlags", true);
String mapFile = s.getString(arenaName, "General", "Map", arenaName + ".lvl");
MyMode mode = s.getEnum(arenaName, "Soccer", "Mode", MyMode.DEFAULT);
```

- `getInt` falls back to bool aliases on parse failure (`Y/Yes/True/On/1` → 1, `N/No/False/Off/0` → 0).
- `getBool` accepts the same aliases, case-insensitive.
- `getEnum` matches enum constant names case-insensitive.
- All accessors return the provided default when the key is absent.

## Writing & listening for changes

```java
SettingsSystem s = getSystem(SettingsSystem.class);
s.setSetting(arenaId, "Bomb", "BombDamageLevel", "1500");
```

`setSetting` mutates the in-memory Ini and fires `SettingListener.arenaSettingsChange(arenaId, section, setting)` on every registered listener. Changes are **not persisted to disk** — session-only.

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

## Common patterns

### Create a new arena

1. `mkdir infinity/zone/arenas/{arenaName}/` — folder name = arena identity (not necessarily a map basename).
2. Create `arena.conf`:
   ```ini
   [General]
   Map=your-map.lvl

   #include /conf/base/base.conf   ; or /conf/svs/svs.conf for canonical VIE

   [Bomb]
   BombDamageLevel=2000   ; overrides (optional)
   ```
3. Put the `.lvl` in `infinity/assets/Maps/`.
4. `~loadArena {arenaName}` at runtime.

### Create a new conf variant (e.g. `svs-arcade`)

1. `mkdir infinity/zone/conf/svs-arcade/`.
2. Inside, create a composite root (`svs-arcade.conf`) that `#include`s shared base fragments from `/conf/svs/` and overrides what differs:
   ```ini
   #include /conf/svs/prizeweights
   #include /conf/svs/ship-warbird
   ; ...
   #include /conf/svs-arcade/misc    ; arcade-tuned version instead of /conf/svs/misc
   ```
3. Arena configs `#include /conf/svs-arcade/svs-arcade.conf`.

### Read a setting in an `AbstractGameSystem`

```java
int enterDelay = getSystem(SettingsSystem.class)
    .getInt(arenaName, "Kill", "EnterDelay", 200);
```

Or — if you need many keys at once — pull the whole `Ini` once, cache what you need, re-fetch on `SettingListener` callback.

## Asset-loader registration

`SettingsSystem.initialize()` registers loaders on the server-side `AssetLoaderService`:

```java
assetLoader.registerLoader(IniLoader.class, "ini", "cfg", "conf");
assetLoader.registerLoader(SSSLoader.class, "sss", "set");
```

Adding a new settings file extension? Register it here — `AssetLoaderService` is one per `GameServer`.

## Anti-patterns

- **Don't hardcode game constants** that already exist in `arena.conf` — route through `SettingsSystem.getInt(...)` / `getBool(...)` so operators can tune without recompiling.
- **Don't duplicate the SVS baseline** inside arena configs — `#include /conf/svs/svs.conf` and layer overrides below.
- **Don't mint new section names** — prefer existing Subspace section/key conventions from `SettingsTypes` so configs stay interoperable with legacy Subspace tooling.
- **Don't bypass `SettingListener`** by polling `getIni` every tick — cache locally and update on callback.
- **Don't reach into `WorldGrids.*` for sizes** — go through `InfinityConstants.*` (see [subspace-moss-terminology](../subspace-moss-terminology/SKILL.md)).

## When this skill applies

- Adding or editing values in an `arena.conf`.
- Creating a new arena folder or a new `conf/{variant}/` family.
- Implementing a system that reads ship/weapon/prize constants.
- Building a setting-edit command or runtime tuning UI.
- Debugging "setting not found" or `ArenaSettings` with a null `Ini`.
