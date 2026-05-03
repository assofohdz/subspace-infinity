## v1.0.12 — 2026-05-03

Major release. Closes the multi-day **typed settings-pipeline migration** (Pre-B0 → B0 → B1a → B1-Bullet/Bomb/Mine/Burst/Repel/Prize → B2 → B3), ships **gameplay slices 1–3** (Repel feel, Rocket feel, Brick feel), introduces a **programmatic spawn-projection test harness**, and switches the project to **BSD-3-Clause + SPDX-only** licensing.

New Features:
- **Typed Groovy preset DSL.** Per-section `*Adapter` classes (`BulletAdapter`, `BombAdapter`, `MineAdapter`, `BurstAdapter`, `RepelAdapter`, `RocketAdapter`, `BrickAdapter`, `PrizeAdapter`, `PrizeWeightsAdapter`) replace the INI-mirror `section('X') { Key V }` shape. Each fragment file (`bullet.groovy`, `bomb.groovy`, ...) carries one typed slot in `ConfigRegistry`, populated via a centralized dispatch table inside `ConfigRegistrySystem`. `GroovyWeaponsLoader` deleted (-158 LOC).
- **Slice 1 — Repel feel.** `[Repel] RepelSpeed`/`RepelTime`/`RepelDistance` wired end-to-end. `ConsumableSystem` REPEL branch composes the effect entity via `GameEntities.createRepel`; spawned entity carries `Decay` + `RepelSpeed` + `RepelDistance` for the (deferred) impulse system.
- **Slice 2 — Rocket feel (full-loop).** `[Rocket]` arena-global tuning + per-ship `RocketTime` lifetime. `ConsumableSystem` FIREROCKET branch snapshots ship `Thrust`/`Speed`, swaps to `RocketConfig` overrides, spawns a buff entity with `Decay`. New `RocketBuffSystem` watches the buff EntitySet and reverts the ship via the canonical Decay-removal seam when the buff expires.
- **Slice 3 — Brick feel (plumbing).** `[Brick] BrickSpan`/`BrickTime` wired end-to-end. `ConsumableSystem` PLACEBRICK branch decrements `Brick` inventory and spawns a marker entity with `BrickSpan` + `Decay`. Solid-wall geometry, collision filter, and client visual deferred to a follow-up slice.
- **Per-ship typed inventory consolidation** (trench + deva). Inventory blocks (`bombs`/`guns`/`mines`/`bursts`/`thors`/`repels`/`decoys`/`bricks`/`rockets`/`portals`) now flow through the typed `ship(Ship.X) { … }` DSL exclusively. Subspace `*Max 0` correctly maps to `null` (= disallow per canon) — fixes a silent dual-pipeline override that gave trench warbird 10 repels / 5 bursts / `BOMB_4` bombs from permissive defaults.
- **Decoy / Brick / Rocket / Portal prize types activated.** Their appliers existed but no `*Max` component was projected — pickups silently no-op'd. `ShipSpawnSystem` now projects all four when the preset declares the corresponding inventory block.
- **Programmatic spawn-projection test harness** — three pillars: `ShipSpawnSystemTest` (template → component projection), `RepelPrizeApplierTest` (Count-family applier), `BulletFactoryTest` (projectile spawn). Slice 2/3 each added a fourth (`RocketBuffActivationTest`, `BrickFactoryTest`). `ConfigRegistrySystemLoadTest` exercises the full dispatch chain against the real trench preset.
- **Hot-reload for Groovy preset fragments** (`ArenaSystem.pollScriptWatches` every 5s). Edits trigger `ConfigRegistrySystem.load`; `ships.groovy` changes also reproject every live ship via `ShipSpawnSystem.reprojectAll`.
- **`Warp` prize applier** (INSTANT family) — wires `WarpPrizeApplier` → `WarpSystem.warpToCenter`.
- **Count-family prize appliers** for Repel / Decoy / Brick / Rocket / Portal — uniform `PrizeApplier` shape (cap-aware increment, no-op when `*Max` absent).

Bug Fixes:
- **Phase 1 INI-mirror loader skips typed fragments.** `ConfigRegistrySystem.load` now filters fragment paths against the dispatch table before passing to the legacy `Ini` loader — typed-DSL files (e.g. `bullet { damageLevel 200 }`) no longer crash the INI parser. Caught by trench smoke after B1-Bullet shipped.
- **`MobSystem` nanoTime stats overflow.** Cast `long`s to `double` before subtraction in the per-tick AI stats reducer, fixing intermittent negative-millis readings.

Breaking Changes:
- **License switch to BSD-3-Clause** (was BSD-2-Clause). Source files carry SPDX-only `BSD-3-Clause` headers; full text moved to `LICENSE.md`. Third-party material (Subspace/Continuum game files, community maps, MillionthVector textures) carved out into `THIRD-PARTY-NOTICES.md` — these assets are NOT covered by the new project license and retain their original terms.
- **`WeaponsConfig` deleted** (B1a). Sub-records (Bullet/Bomb/GravBomb/Mine/Burst/Repel/Thor) promoted to direct `ConfigRegistry` slots; consumers that read `registry.weapons().bullet()` now read `registry.bullet()` directly.
- **`GroovyWeaponsLoader` deleted** (B1-Prize). Per-fragment typed adapters replace it.
- **`ShipConfig.rockets` type** changed from `CountStats` to `RocketStats(start, max, activeTimeCs)` to carry per-ship buff lifetime alongside inventory caps.
- **API enums renamed:** `Bombs` → `BombLevel`, `Guns` → `GunLevel`. Client visuals split out of `Bombs`/`Guns`/`BombRegistry` into their own classes; api-side enums are now data-only.
- **Pre-B0 dead-code purge.** Deleted `ArenaSettings` ECS component, `SettingListener` interface, `SettingsSystem.setSetting`. None had callers; surfaced during the B0 grilling session.
- **Cut out-of-scope Subspace sections** from preset fragments (`[Cost]`, `[Flag]`, `[Kill]`, `[King]`, `[Latency]`, `[PacketLoss]`, `[Periodic]`, `[Routing]`, `[Security]`, `[Soccer]`, `[Team]`, `[Territory]`, `[Message] MessageDistance`, per-ship Soccer keys). Documented in `.scratch/out-of-scope.md` with a promotion path.

Other:
- **Massive documentation refresh.** `README.md` rewritten as a best-practice contributor README (no screenshots — those moved to the GitHub Pages player landing). `CONTRIBUTING.md` rewritten as a proper contributor guide. `LICENSE.md` (was `LICENSE`) reformatted as Markdown. `THIRD-PARTY-NOTICES.md` documents external assets. GitHub Pages site replaced with a player-facing landing (`/conf/<preset>` content, screenshots, install steps).
- **Settings pipeline trackers.** `.scratch/settings-pipeline.md` (master state-of-keys table, ~80 rows across 14 Subspace sections) + `.scratch/settings-pipeline-slices.md` (kanban work queue) + `.scratch/ship-config-dictionary.md` (per-ship ported-vs-pending ledger). Always-on rules #6/#7/#8 in `CLAUDE.md` keep them in sync.
- **`systems.ship` cluster.** Player-driver / weapons / consumable / spawn / energy systems moved under `infinity.systems.ship.*` to match the package-by-feature convention.
- **`BlockTypeExpander` extracted from `GameServer`** — cuts the monolithic boot path; tile-color / shape registration now lives in its own class.
- **`BombRegistry` / `Bombs` / `Guns` split** — client visuals moved out of api/ enums into their own classes (api/ stays data-only per the `api-contracts.md` rule).
- **`ModelViewState` inner classes promoted to siblings.**
- **Build sourcing: dropped `commons-math` + `MathUtil`** — unused.
- **CI: `dependabot` weekly grouped GitHub Actions updates** + Lint Workflows action. `gradle.yml` + `release.yml` aligned to skip build on docs-only changes.
- **Dispatch table for typed fragment installers.** `ConfigRegistrySystem` now owns the single `(filename → installer)` map. Each per-section slice adds one entry; B4 will delete the legacy compat shim once every section migrates.

## v1.0.11 — 2026-05-02

Crash fix for packaged Windows builds on NVIDIA GPUs.

Bug Fixes:
- **MiniMap shader crashed at join-game on NVIDIA GPUs.** `MatDefs/MiniMap/MiniMap.{frag,vert}` used legacy GLSL syntax (`varying`, `attribute`, `gl_FragColor`) but the j3md offered a `GLSL150` variant. NVIDIA's strict compiler rejected the deprecated syntax (`C7555`/`C7533`); Mesa on Linux accepted it silently. `RadarState` loads the MiniMap material when the ship enters an arena, producing the "black-then-close" symptom ~70 ms after spawn. Added `#import "Common/ShaderLib/GLSLCompat.glsllib"` to both files (matches the convention already used by `Lighting`, `Unshaded`, the `AnimateSprite`/`Multiline`/`StaticSprite` shaders in this repo).
- **`AnimateOnceSpriteShader.{frag,vert}` defensively patched** with the same shim. Currently unwired, but the intended one-shot-sprite-animation use would have hit the same NVIDIA wall when enabled.

Other:
- Audit confirms all 27 `.frag`/`.vert` files in `infinity/assets/MatDefs/` now import `GLSLCompat.glsllib`, and none use the rarer deprecated built-ins (`gl_FragData`, `gl_TexCoord`, `ftransform`, `gl_ModelViewProjectionMatrix`, `gl_Vertex`, `gl_Color`, `gl_Normal`) that would be the next class of strict-mode failures.

## v1.0.10 — 2026-05-02

Diagnostics release. No gameplay changes.

Other:
- **Bridge `java.util.logging` → log4j2.** JME's render-thread errors and other JUL output were going to stderr — which the itch.io launcher discards — making packaged-build crashes (e.g. the "black-then-close" join-game crash on Windows) invisible in `infinity.log`. Added the `log4j-jul` runtime dep and `-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager` to `applicationDefaultJvmArgs` and both `jpackageImage` / `jpackage` tasks, so JME's `SEVERE` records now flow through the existing `Root level="error"` config into both `infinity.log` and `infinity-errors.log`. The logger name is `com.jme3.*` so the failing class is unambiguous.

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
