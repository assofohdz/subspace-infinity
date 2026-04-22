---
name: arena-settings
description: Work with Subspace Infinity arena settings — the per-arena `arena.conf` INI files under `infinity/zone/arenas/`, the `SettingsSystem`, and the `SettingListener` API. Use when adding/reading settings, creating a new arena, or listening for setting changes.
---

# Arena Settings

A **Zone** (server) contains many **Arenas**. An Arena = one **Map** (`.lvl` + `.lvz`) + one **Settings** bundle (`arena.conf`). This skill covers the settings half.

## Where things live

```
infinity/zone/arenas/
├── (default)/arena.conf          # fallback when a map-specific conf is missing
└── trench/arena.conf             # per-arena config; folder name = map base name (no .lvl)
```

`zone/` is added as a resource source dir by [buildSrc/src/main/groovy/infinity.app-with-assets.gradle:16](../../../buildSrc/src/main/groovy/infinity.app-with-assets.gradle#L16), so JME's `AssetManager` sees everything under `zone/` on the classpath. The loader path used at runtime is `arenas/{mapBaseName}/arena.conf` — note the `zone/` prefix is **not** part of the asset key.

Supporting Java lives under [infinity/src/main/java/infinity/settings/](../../../infinity/src/main/java/infinity/settings/):

| File | Purpose |
|---|---|
| `IniLoader` | JME `AssetLoader` that parses `.ini` / `.cfg` / `.conf` into an `org.ini4j.Ini`. |
| `SSSLoader` | JME `AssetLoader` for `.sss` / `.set` (colon-delimited rows → `ArrayList<String[]>`). Used for setting-metadata files, not arena configs. |
| `SettingListener` | Callback: `arenaSettingsChange(ArenaId, section, setting)`. |
| `SettingsTypes` | Exhaustive enumeration of known Subspace setting keys — treat as reference, not runtime. |

Runtime entry point: [infinity/src/main/java/infinity/systems/SettingsSystem.java](../../../infinity/src/main/java/infinity/systems/SettingsSystem.java) (extends `AbstractGameSystem`).

## `arena.conf` format

Standard INI, parsed by `ini4j`. Sections fall into three kinds:

**1. Global rule sections** (one per arena): `[Bullet]`, `[Bomb]`, `[Mine]`, `[Shrapnel]`, `[Burst]`, `[Prize]`, `[PrizeWeight]`, `[Flag]`, `[Soccer]`, `[Radar]`, `[Team]`, `[Kill]`, `[Repel]`, `[Message]`, `[Wormhole]`, `[Latency]`, `[Brick]`, `[Rocket]`, `[Door]`, `[Misc]`, `[Custom]`, `[Territory]`, `[Periodic]`, `[Security]`, `[PacketLoss]`, `[Routing]`, `[King]`, `[Cost]`, `[Owner]`, `[Toggle]`.

**2. Per-ship sections** (one block per ship class): `[Warbird]`, `[Javelin]`, `[Spider]`, `[Leviathan]`, `[Terrier]`, `[Weasel]`, `[Lancaster]`, `[Shark]`. These share the same key set (`MaximumSpeed`, `InitialEnergy`, `BombFireEnergy`, …). `SettingsSystem` switches on `ShapeInfo.getShapeName()` to pick the section; see [SettingsSystem.updateShipSettings](../../../infinity/src/main/java/infinity/systems/SettingsSystem.java#L333).

**3. Notes / `[Owner]`** — free-form metadata.

For the canonical key list per section, read [SettingsTypes.java](../../../infinity/src/main/java/infinity/settings/SettingsTypes.java) top-to-bottom.

## Loading an arena's settings

`SettingsSystem.loadSettings(requester, mapBaseName)`:

1. Asset-loads `/arenas/{mapBaseName}/arena.conf` as an `Ini`.
2. On miss: reuses the cached `(default)` arena if present, else loads `/arenas/(default)/arena.conf`.
3. Caches under `arenaSettingsMap[mapBaseName]`.

```java
SettingsSystem settings = getSystem(SettingsSystem.class);
settings.loadSettings(playerId, "trench");      // note: base name, NO .lvl
Ini ini = settings.getIni("trench");            // retrieve by same key

int bombDamage = Integer.parseInt(ini.get("Bomb", "BombDamageLevel"));
int warbirdMaxSpeed = Integer.parseInt(ini.get("Warbird", "MaximumSpeed"));
```

**Key convention:** store and retrieve by the map's **base name** (no extension). [ArenaSystem.java:133-135](../../../infinity/src/main/java/infinity/systems/ArenaSystem.java#L133-L135) currently stores with the stripped key but retrieves with the full `.lvl` name — that path gets `null` back. If you're touching that code, make both sides consistent.

Overload: `loadSettings(requester, arenaEntityId)` resolves the map name from the entity's `ArenaId` component and delegates.

## Listening for changes

Implement `SettingListener`:

```java
public class MyModule implements SettingListener {
    @Override
    public void arenaSettingsChange(ArenaId arenaId, String section, String setting) {
        if ("Bomb".equals(section) && "BombDamageLevel".equals(setting)) {
            // re-read from SettingsSystem.getIni(arenaId.getArenaBaseName())
        }
    }
}

getSystem(SettingsSystem.class).addListener(myModule);   // in initialize()
// ...
getSystem(SettingsSystem.class).removeListener(myModule); // in terminate()
```

The listener fires from `SettingsSystem.settingChanged(...)`; today it's `private` and only called from future change hooks — wire your notifier when implementing a setting-edit path. Listeners are expected to **cache** their own copy and not reach back into `SettingsSystem` on every read.

## Asset-loader registration

`SettingsSystem.initialize()` registers loaders against the server-side `AssetLoaderService`:

```java
assetLoader.registerLoader(IniLoader.class, "ini", "cfg", "conf");
assetLoader.registerLoader(SSSLoader.class, "sss", "set");
```

Adding a new file extension? Register it here, not elsewhere — `AssetLoaderService` is one per `GameServer`.

## Common patterns

### Create a new arena

1. `mkdir infinity/zone/arenas/{mapBaseName}/` (folder name must match the `.lvl` base name).
2. Copy an existing `arena.conf` (`trench` is a solid starting template).
3. Put the matching `{mapBaseName}.lvl` on the asset path (`infinity/assets/Maps/`).
4. `~loadMap {mapBaseName}.lvl` at runtime — `ArenaSystem.loadArena` fires `MapSystem.loadMap` + `SettingsSystem.loadSettings`.

### Read a setting in a `AbstractGameSystem`

```java
Ini ini = getSystem(SettingsSystem.class).getIni(arenaBaseName);
if (ini == null) return;  // arena not loaded yet
Section kill = ini.get("Kill");
int enterDelay = Integer.parseInt(kill.get("EnterDelay"));
```

Cast once, cache the parsed value — `ini4j` parses on every `get`.

### Share settings across arenas via `(default)`

Leave `/arenas/(default)/arena.conf` as the baseline. Any map folder that lacks its own `arena.conf` will transparently inherit it on the first `loadSettings(map)` call.

## Anti-patterns

- **Don't hardcode game constants** that already exist in `arena.conf` — route through `SettingsSystem.getIni(...)` so zone operators can tune without recompiling.
- **Don't pass `.lvl` into `loadSettings` or `getIni`** — the cache key is the base name. See the consistency note above.
- **Don't bypass `SettingListener`** by polling `getIni` every tick — cache locally and update on callback.
- **Don't invent new section names** — prefer existing Subspace section/key conventions from `SettingsTypes` so saved configs stay interoperable with legacy SS tooling.

## When this skill applies

- Adding or editing values in an `arena.conf`.
- Creating a new arena folder under `infinity/zone/arenas/`.
- Implementing a system that reads ship/weapon/prize constants.
- Building a settings-edit command and notifying listeners.
- Debugging "setting not found" / `ArenaSettings` with a null `Ini`.
