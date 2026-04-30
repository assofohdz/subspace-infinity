# Migrate `conf/<preset>/*.conf` fragments to Groovy

Status: in-progress

The follow-on to [`zone-arena-to-groovy`](../../.scratch-archive/zone-arena-to-groovy/PRD.md), which deliberately scoped itself to the zone- and arena-tier files and called out the preset fragments as out-of-scope:

> Out of scope: the per-preset `conf/<preset>/*.conf` fragments under `infinity/zone/conf/`. Larger surface, separate migration; track as a future item if needed.

This PRD is that follow-up.

## Why

- Closes the loop on always-on rule #5: tuning knobs end up entirely in Groovy. Today operators pick between Groovy (zone, arena, ships) and INI (everything else); after this, there's only one authoring surface.
- Sets up the eventual deletion of `IniLoader`, the `#include` preprocessor, the `org.ini4j` dependency, and the `Section`/`Ini` plumbing in `SettingsSystem` (Phase B / cleanup PR — out of scope here).
- IDE autocomplete + Groovy compile errors instead of "did the key exist? was it spelled right? what's the default?".
- Per-preset hot-reload — operators can tune knobs and see the effect without restarting.

## Scope split — Phase A (this PRD) vs Phase B (deferred)

**Phase A — authoring surface to Groovy, untyped flat bag.**
The Groovy fragment loader produces an `Ini`-shaped result that merges into `SettingsSystem.arenaSettingsMap` exactly like INI does today. `getInt` / `getString` / `getBool` / `getEnum` accessors keep working unchanged. No consumer migration. Adds hot-reload for Groovy fragments.

**Phase B — typed records per `[Section]`.** Out of scope here. When a consumer materializes for `[Bomb]`, lift those keys to a typed `BombConfig` record in `api/src/infinity/config/`, expose `SettingsSystem.getBomb(arenaName)`, mirror per-section listener events. The Groovy DSL shape doesn't change. Then retire INI plumbing once all string-keyed callers are gone. The `ship-config-dictionary.md` already tracks the per-ship version of this graduation.

The user's framing for this PRD: "good enough for now" — Phase A only, defer per-section typing to whenever a real consumer needs it. Today's surface has **0 `SettingListener` implementations** and **only `ArenaSystem.getIni()` reads the merged store** at arena-load — so the migration is overwhelmingly an authoring-surface change, not a consumer rewire.

## Locked-in design decisions

1. **Composite `*.conf` files retire on migration.** Today `arena.groovy` does `includeFragment '/conf/<preset>/<preset>.conf'` and that file `#include`s the per-section leaves. After migration, `arena.groovy` lists each section file directly under `includeFragment`. One source of truth (`arena.groovy`) instead of two.
2. **Per-section file split, not per-preset.** A preset becomes 8–11 small Groovy files (`prizeweights.groovy`, `cost.groovy`, `misc.groovy`, `ship-warbird.groovy`, …) rather than one 250-line `<preset>.groovy`. Mirrors today's structure, smaller diffs, lets operators tune one concern in one file.
3. **Per-ship fragment block ≠ typed `ships.groovy`.** Two DSLs, two intents:
   - `ship(Ship.WARBIRD) { rotation initial: …, max: …, upgrade: …; … }` lives in `ships.groovy` and produces a typed `ShipConfig` (15 keys ported per [`ship-config-dictionary.md`](../ship-config-dictionary.md)).
   - `shipSection 'Warbird' { SuperTime 6000; BulletFireEnergy 20; … }` lives in fragment-land and is the untyped flat bag for unported keys (~69 per ship).
   - As keys graduate per the dictionary, they move from the latter to the former and disappear from fragments. The temporary duplication is the migration's own progress tracker.
4. **`shipSections('a','b','c') { … }` is the splat helper** for the INI shape `[Warbird] #include shared-block; [Javelin] #include shared-block; …` (used by `svs-league/svs-league.conf` and `svs-dueling.conf`). Names validate against the `Ship` enum at parse time so typos fail loudly.

## DSL shape

```groovy
// /conf/svs/cost.groovy
section('Cost') {
    PurchaseAnytime 0
    XRadar          0
    // …
}
```

```groovy
// /conf/svs/misc.groovy
section('Bomb') {
    BombDamageLevel  750
    BombAliveTime    6000
    // …
}
section('Mine') {
    MineAliveTime    12000
    TeamMaxMines     12
}
// …
```

```groovy
// /conf/svs/ship-warbird.groovy
shipSection('Warbird') {
    SuperTime          6000
    ShieldsTime        4000
    BulletFireEnergy   20
    // …
}
```

```groovy
// /conf/svs-league/items.groovy
shipSections('Warbird', 'Javelin', 'Spider', 'Leviathan',
             'Terrier', 'Weasel', 'Lancaster', 'Shark') {
    InitialBurst   1
    InitialDecoy   2
    // …
}
```

`include '/conf/svs/cost.groovy'` (recursive) and relative-path resolution come **later** — not in PR #1. The first preset port (`svs/cost`) doesn't need either, and adding cycle detection for `include` belongs alongside the first preset that actually composes (`svs-league`, last in the queue).

## Surface area

```
infinity/zone/conf/
├── base/                     # 10 files — current Infinity baseline
├── svs/                      # 11 files — canonical VIE
├── svs-league/               # 13 files — league + duel variants
├── svs-pb/                   # 6 files — PowerBall approximation
├── svs-tce/                  # 12 files — Turf Classic East
├── svs-turf/                 # 12 files — post-VIE Turf Zone
├── deva-04-2026/             # 11 files — deva preset
└── trench-04-2026/           # 10 files — trench preset
```

~85 files (including 8 composite `*.conf` files that retire on migration).

## PR plan

| # | Scope | Files touched (rough) | Notes |
|---|---|---|---|
| **1** | **Loader infra + smoke port** | `GroovyFragmentLoader.java` (new), `SettingsSystem.loadFragments` dispatch, `svs/cost.groovy` fixture, unit tests | This PRD's first deliverable. No live-arena impact — `svs/cost` (INI) stays in place as the unmigrated path; `svs/cost.groovy` exercises the loader in tests. |
| **2** | **Hot-reload for fragments** | `ArenaSystem.registerScriptWatch` / `pollScriptWatches` generalized to watch every `.groovy` `includeFragment` path the arena resolves; `SettingListener` events fired for changed `(section, key)` pairs | Today only `ships.groovy` is watched. After this, every Groovy fragment hot-reloads. INI fragments still don't hot-reload (out of scope; the goal is to retire INI). |
| **3** | **Recursive `include` directive** | `GroovyFragmentLoader.evaluate` adds `include '/conf/.../x.groovy'` keyword + cycle detection (max depth 16) | Needed before `svs-league/` migrates (it `#include`s `svs/svs.groovy` and overrides on top). |
| **4** | Port `base/` | 10 files | Project baseline. Used by `arenas/(default)`. Replace `arena.groovy`'s `includeFragment '/conf/base/base.conf'` with the per-section list. |
| **5** | Port `trench-04-2026/` | 10 files | Live arena. 8 `ship-<name>` files port to `shipSection 'X' { … }` blocks, keeping flat-bag for unported keys. |
| **6** | Port `deva-04-2026/` | 11 files | Same shape as trench. |
| **7** | Port `svs/` | 11 files | Canonical reference; mirror the existing "treat as read-only-ish" notice in the Groovy file headers. |
| **8** | Port `svs-pb`, `svs-tce`, `svs-turf` | small, monolithic | One PR or three tiny ones. |
| **9** | Port `svs-league/` | 13 files | Last because it depends on PR #3 (recursive include) + PR #7 (svs/). |
| **10** | Cleanup (separate PRD or ad-hoc) | retire `IniLoader`, `#include` preprocessor, `org.ini4j` dep, `Section`/`Ini` plumbing | Triggers when the last INI fragment is gone. Conditional on Phase B typing or string-key callers all being typed-equivalent. |

## Out of scope (Phase A)

- Typed records per `[Section]` (`BombConfig`, `BulletConfig`, `PrizeWeightTable`, …) — Phase B.
- Typed `getBomb(arenaName)` accessors on `SettingsSystem` — Phase B.
- Migrating the per-ship typed `ShipConfig` surface to absorb the ~69 unported keys — out of scope here; tracked in [`ship-config-dictionary.md`](../ship-config-dictionary.md).
- Changing Subspace setting names or units. The DSL re-uses canonical names verbatim.
- Migrating the `arena.conf`-tier or `zone.conf`-tier files — already done in [`zone-arena-to-groovy`](../../.scratch-archive/zone-arena-to-groovy/PRD.md).

## Comments
