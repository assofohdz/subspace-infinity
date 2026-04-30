# Migrate `conf/<preset>/*.conf` fragments to Groovy

Status: ready-for-human

The follow-on to [`zone-arena-to-groovy`](../../.scratch-archive/zone-arena-to-groovy/PRD.md), which deliberately scoped itself to the zone- and arena-tier files and called out the preset fragments as out-of-scope:

> Out of scope: the per-preset `conf/<preset>/*.conf` fragments under `infinity/zone/conf/`. Larger surface, separate migration; track as a future item if needed.

This PRD is that follow-up — finish the job so the `IniLoader`, `#include` preprocessor, and the string-keyed `SettingsSystem.getInt`/`getString` accessors can all be retired.

## Why

- Closes the loop on always-on rule #5: tuning knobs end up entirely in Groovy. Today operators pick between Groovy (zone, arena, ships) and INI (everything else); after this, there's only one authoring surface.
- Lets us delete `IniLoader`, the `#include` preprocessor, `org.ini4j` dependency, the `Section`/`Ini` plumbing in `SettingsSystem`, and probably most of `SettingsTypes` (it'll be replaced by typed records or shipped via the existing setting-listener channel).
- Type-checked editing — IDE autocomplete and Groovy compile errors instead of "did the key exist? was it spelled right? what's the default?".
- Each preset can carry its own typed shape (e.g. `BombConfig`, `ShipFragment`, `PrizeWeightTable`) instead of a flat string-keyed bag.

## Surface area

```
infinity/zone/conf/
├── base/                     # 8 fragments — current Infinity baseline
├── svs/                      # ~15 fragments — canonical VIE
├── svs-league/               # 5 fragments — league/duel variants
├── svs-pb/                   # 1 monolithic file
├── svs-tce/                  # 1 monolithic file
├── svs-turf/                 # 1 monolithic file
├── deva-04-2026/             # 1 file — deva preset
└── trench-04-2026/           # 1 file — trench preset
```

~92 files / ~412 KB at time of writing. Mostly per-ship sections and global rule sections (`[Bullet]`, `[Bomb]`, `[Mine]`, `[Prize]`, `[PrizeWeight]`, etc. — see `SettingsTypes.java` for the canonical list).

Two consumer shapes today:

1. **`SettingsSystem.getInt/getString/getBool(arena, section, key, default)`** — string-keyed reads scattered across systems. These need typed counterparts.
2. **`SettingListener.arenaSettingsChange(arenaId, section, setting)`** — change-notification API. Should keep a similar shape; the events just carry typed updates instead of `(section, key)` strings.

## Approach (sketch)

1. **Pick the typed shape.** Probably one record per `[Section]`: `BombConfig`, `BulletConfig`, `MineConfig`, `PrizeWeightTable`, `ShipSectionConfig` (per-ship), … . Group them on a per-arena `FragmentBundle` record carrying all of them. Keys with no current consumer can stay as untyped `extras: Map<String, String>` until a consumer wants them.
2. **Introduce a typed accessor surface on `SettingsSystem`** — `getBomb(arenaName)`, `getBullet(arenaName)`, … alongside the existing string-keyed methods. New code reads typed; old call sites keep working.
3. **Convert one preset at a time.** Each preset becomes one `<preset>.groovy` (or several, mirroring the current per-fragment split). The `arena.groovy` `includeFragment` directive expands to take Groovy paths too — the loader picks the right path based on extension.
4. **Migrate consumers from string-keyed to typed accessors** as each section's typed record lands. The setting-listener API gets a typed counterpart per record.
5. **Retire INI** once all string-keyed callers are gone: delete `IniLoader`, `Section`/`Ini` references in `SettingsSystem`, the `#include` preprocessor, and `org.ini4j`.

## Scoping notes

- **Don't try to do all 92 files at once.** Slice by preset (`base/`, then `svs/`, then variants). Each preset is independently shippable; arenas using older INI keep working until their preset migrates.
- **Don't try to type every section at once either.** `[Bomb]` / `[Bullet]` / `[Mine]` / `[Prize]` / `[PrizeWeight]` cover most gameplay; `[Owner]` / `[Custom]` / `[Periodic]` etc. can stay untyped longer.
- **Reference, not target:** `conf/svs/` is described as "canonical VIE, copied verbatim from SubspaceServer; treat as read-only reference" in the `arena-settings` skill. Migrating it means picking a Groovy shape that's still legible to operators familiar with Subspace setting names — keep section/key vocabulary intact in the DSL.

## Out of scope

- Changing the Subspace setting names or units. The DSL re-uses the canonical names and just types them.
- Migrating the `arena.conf`-tier or `zone.conf`-tier files — already done in [`zone-arena-to-groovy`](../../.scratch-archive/zone-arena-to-groovy/PRD.md).

## Comments
