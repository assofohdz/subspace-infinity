## v1.0.9 — 2026-05-02

Cleanup release focused on closing the conf-fragments-to-groovy migration, retiring `CoreGameConstants`, and seeding the first programmatic spawn-projection test harness.

New Features:
- **Hot-reload for Groovy preset fragments.** `ArenaSystem.registerFileWatch` watches every `.groovy` `includeFragment` per loaded arena. Edits trigger `SettingsSystem.reloadFragments`, which rebuilds the merged `Ini` and fires `SettingListener` events for each `(section, key)` whose value actually changed. Previously only `ships.groovy` hot-reloaded.
- **Spawn-projection test harness.** New `ShipSpawnSystemTest` boots a minimal `GameSystemManager` (`DefaultEntityData` + `ConfigRegistrySystem` + `ShipSpawnSystem`, no physics / map / network), projects a fully-specified `ShipConfig`, and asserts every documented per-entity component lands with the expected value. PRD at `.scratch/spawn-projection-test-harness/PRD.md` queues four follow-up slices.

Other:
- **`IniLoader` retired** along with the four dead `Groovy*Loader.resolveOnDisk` passthroughs and the `.ini` / `.cfg` / `.conf` asset-loader registration. `SettingsSystem.loadFragmentIni` is Groovy-only. `org.ini4j` stays as the merged-store shape until Phase B.
- **`CoreGameConstants` deleted** (-103 lines). 15 phantom constants with no consumers (all 6 `*PROJECTILESPEED`, all 3 health knobs, all 5 AI knobs, `THORDECAY`, `UPDATE_SETTINGS_INTERVAL_MS`) dropped; the 15 live knobs relocated to their consuming systems as private constants. The unused `THORCOOLDOWN` / `BURSTCOOLDOWN` constants and the dead `BURSTCOOLDOWN` enforcement gap are tracked in the refactor backlog.
- **Build sourcing flipped to `libs/m2/`.** Simsilica deps now resolve from the vendored repo instead of `~/.m2`, removing the developer-machine-state coupling that bit v1.0.7's CI matrix.
- **Heart dep dropped.** `SISpatialFactory` inlines the two `MyMesh.translate` / `scale` use-sites; the only remaining `jme3utilities` reference is gone.
- **`@NotNull` → `@Nonnull` migration.** `GameSounds` switched from JetBrains annotations to JSR-305; the `org.jetbrains:annotations` dependency is dropped.

## v1.0.8 — 2026-04-30

CI hotfix release. No gameplay changes.

Bug Fixes:
- Refreshed the vendored Moss `mphys` / `sio2-mphys` snapshots in `libs/m2/` so `release.yml`'s "Copy libs to Maven Local" step seeds CI runners with the `PhysicsSpace.setLargeStaticCollisionFilter` API used by `GameServer`. v1.0.7 release CI failed at compile time on the macOS / Linux / Windows matrix because the vendored snapshot lagged behind the developer's locally-built Moss.

Other:
- `createWormhole2` renamed to `createOver5` (api).
- `ShipSpawnSystem` moved into `infinity/systems/` to match the systems-package convention.
- `SettingsTypes` and `SimpleFileFilter` orphan classes deleted.

## v1.0.7 — 2026-04-30

The v1.0.7 release branch focused on per-arena content tuning, ECS hardening, and a multi-day cleanup arc.

New Features:
- **Per-arena prize spawner DSL.** `arena.groovy` now declares `prizeSpawners` with per-spawner TTL, region, and per-prize weight overrides. Arena content authors can shape the prize economy without touching Java.
- **`~check*` diagnostic chat commands.** Live ECS invariant audits accessible from the in-game chat — surfaces stale entity sets, dangling references, and projection mismatches at runtime.
- **Pattern 4 ship-tuning plumbing.** Ship weapon and inventory tuning split into typed `*Config` records (template) projected onto Zay-ES components (instance) at spawn. Hot-path consumers read components only; live-tune flows through Groovy edit + reload.

Bug Fixes:
- **Network serializer registration.** `MobType`, `ProbeInfo`, and `Speech` components now register with the network serializer. Without these, the first time a client `watchEntity` reached one of them the serializer would throw at runtime.
- **`RGBQuad` equality.** Added `equals(Object)` override with a matching `hashCode`, fixing a latent bug in tile-color deduplication during map decode.
- **Default arena spawn.** Arenas without an explicit spawn now default to `(512, 512)` (map center) instead of the NW corner.
- **Hyphenated INI keys** in Groovy fragment imports are now correctly quoted.
- **CodeQL.** Resolved 4 security alerts.

Breaking Changes:
- **All ECS components are immutable** with a no-arg constructor. Module authors writing their own components must conform — any non-final field or missing default constructor will fail Zay-ES deserialization.
- **`api/sim/GameEntities` ABI surface** narrowed: dead methods and ghost comments removed; inline ship-tuning stripped from `createShip`. Modules that called the removed overloads need to migrate to the trimmed surface.
- **`IEnum` renamed to `OrdinalEnum`**, then deleted entirely after `Bombs.next()` / `Guns.next()` were inlined into their two callers.

Other:
- **Big dead-code culls.** `SettingsSystem` 1149 → 316 lines, `MapSystem` 938 → 693 lines, `InfinityGeometryFactory` 783 → 622 lines. The hand-rolled `ByteArray` is gone; `MapSystem` now uses `java.nio.ByteBuffer`.
- **PMD wired into Gradle** with focused dead-code rules; six cleanup batches landed on top.
- **`infinity/` root tidied:** loose AppStates relocated; `AdaptiveLoader` retired (its PRD is preserved in scratch).
- **Dependency cleanup:** removed five unused libs (`gdx-ai`, `Clipper`, `noise4j`, `gson`, `commons-collections4`) and stale commented-out lines. Heart, ini4j, and commons-math 2.2 stay (use sites are catalogued in `.scratch/refactor-backlog/BACKLOG.md`).
- **Event surface namespaced** into `zone/` and `arena/`; stub events removed.

## v1.0.6 — 2026-04-29

The v1.0.6 release introduced the multi-arena world, the radar viewport, and the migration of zone/arena/ship configuration to Groovy.

New Features:
- **Multi-arena world.** A single server now hosts multiple arenas in the same world space — players literally fly from one arena to the next without a portal hop. Large arena objects (ghost-cubes) route through a coarse static collision index; ship-vs-arena contacts are filtered to ship bodies only via `CollidesWithLargeStatics`.
- **Radar viewport** (4-part feature). Top-corner mini-map showing nearby ships, arena bounds, and points of interest. `RadarTheme` record bundles palette and size knobs for theming.
- **Zone / arena / ship configuration in Groovy.** `zone.conf` and `arena.conf` retired in favor of `zone.groovy` and per-arena `arena.groovy`. Preset fragments (`ships.groovy`, `bullets.groovy`, etc.) load via `GroovyFragmentLoader` and support recursive `include`. Settings flow through a typed `GroovySettingsHost` seam (`ZoneLoader`, `ArenaLoader`, `ShipLoader`, `FragmentLoader`).
- **Per-arena `wallFriction` tuning knob** in `arena.groovy`. Adjusts ship-vs-wall friction without touching Moss.
- **Live ship-config hot-reload.** Edit a value in `ships.groovy`, save, see it applied in-game within ~5 seconds. Capability stats (energy/thrust/etc.) reproject correctly in tuning mode.
- **Prize-driven movement upgrades.** Movement prizes now actually upgrade ship movement stats.

Bug Fixes:
- **Large arenas no longer disappear** client-side at certain camera distances.
- **Tile shimmer** in the 3D world reduced.
- **Tile-count config** centralized — fixes a bug loading multiple maps.
- **Wall-bounce rotation** glitch resolved.
- **Energy / Health components** now follow the same convention as other prize stats: `Health`/`Energy` is the current value, `EnergyMax` is the absolute cap, upgrades raise current toward max.

Other:
- **Ship-projection logging** demoted to debug.
- **New physics constants** moved into `zone.groovy` (easier to tune).
- **`SCRIPT_POLL_INTERVAL_NANOS`** promoted from a Java constant to `zone.groovy`.
- **CI:** workflow lint cleanup (actionlint).
- **Dependency bumps:** Heart 9.1.0 → 9.3.0, slf4j 2.0.16 → 2.0.17, log4j 2.24.3 → 2.25.4, ArchUnit 1.3.0 → 1.4.2, Spotless 6.25.0 → 8.4.0.

---

## v0.0.1 - _In Development_
Milestone | Tag | Maven Release | GitHub Release

New Features:

Bug Fixes:

Deprecated:

Breaking Changes:

Other:
