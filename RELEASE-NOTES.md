## v1.0.13 — 2026-05-04

Major release. Closes **gameplay slices 4 through 8** (Decoy / Portal plumbing, full Status family for Cloak / Stealth / XRadar / AntiWarp, MultiFire applier, per-team Spawn config, full Prize lifecycle: random lifetime range / death-drops / negative roll / player-scaled spawners with hidden mode + regen batch), introduces the **`testarena` showcase preset**, and adds a **`~arenas` chat command** for navigating the multi-arena zone.

New Features:
- **Slice 4 — Decoy plumbing.** `[Misc] DecoyAliveTime` wired end-to-end via the typed `decoy.groovy` adapter. `ConsumableSystem` PLACEDECOY branch decrements `Decoy` inventory and spawns a marker entity with `Decay`. Decoy-as-radar-fake (the canonical Subspace mechanic) deferred — crosses into client-side radar work.
- **Slice 5 — Portal plumbing.** `[Misc] WarpPointDelay` wired end-to-end via the typed `portal.groovy` adapter. `ConsumableSystem` PLACEPORTAL branch decrements `Portal` inventory and spawns a marker entity with `Decay`. Warp-to-placed-portal mechanic deferred. (`WarpRadiusLimit` re-scoped from Portal to Spawn after a REFERENCE.md lookup — section header was misleading.)
- **Slice 6a — Cloak + Stealth Status family.** Per-ship tri-state `*Status` (0=forbidden / 1=acquirable / 2=starts active) + per-ship `*Energy` drain rate authored in `ships.groovy`. New `StatusStats` record, `StatusDrainSystem` (parallels `RocketBuffSystem`), and rewritten `CloakPrizeApplier` / `StealthPrizeApplier` (canonical tri-state respect — no-op on status=0). Drain runs at canonical Subspace rate `energy × tpf / 10` per tick.
- **Slice 6b — XRadar + AntiWarp Status family.** Reuses 6a's `StatusStats` + `StatusDrainSystem` infrastructure. Two more per-ship `*Status` + `*Energy` blocks; `XRadarPrizeApplier` / `AntiWarpPrizeApplier` flipped from stub to canonical tri-state.
- **Slice 6c — MultiFire applier.** `MultiFirePrizeApplier` from stub to canonical: stamps `Multishot(true)` on the ship unconditionally per Subspace canon (no per-ship `*Status` for MultiFire). Per-ship MultiFire firing-mode plumbing (energy / delay / angle) deferred to a follow-up slice.
- **Slice 7 — Per-team Spawn selection.** `[Spawn]` per-team coords + radius wired end-to-end via the typed `spawn.groovy` adapter. New `SpawnConfig` + `TeamSpawn` records (api/), `SpawnAdapter`, `ArenaSystem.getArenaSpawn(arena, freq)` reads typed `SpawnConfig` with `freq % teams.size()` wraparound. Generalises the canonical Subspace 4-team layout to N teams. Two consumer call sites updated (`GameSessionHostedService` connect-time, `AvatarSystem` ship-change).
- **Slice 8a — Prize random-lifetime range.** `[Prize] PrizeMinExist` paired with already-wired `PrizeMaxExist` gives each prize a random per-prize lifetime in `[minExist, maxExist]` cs. `PrizeSystem.sampleDecayMs` samples uniformly; collapses to `[max, max]` for un-migrated arenas (preserves 1:1 behaviour). Per-spawner explicit `ttlMs` remains a fixed override.
- **Slice 8b — Death-dropped prizes.** `[Prize] DeathPrizeTime` wired end-to-end. On ship death, `PrizeSystem.spawnDeathPrize` drops one weighted prize at the ship's `BodyPosition`. No-op when `deathPrizeTimeMs == 0`. Threshold gating + ship-bounty growth deferred (Infinity has no ship-bounty tracking today).
- **Slice 8c — Negative-prize roll via DUD substitution.** `[Prize] PrizeNegativeFactor` wired: 1-in-N roll at prize-spawn time replaces the selected prize-type with `Dud` (existing no-op applier). Preserves canonical probability semantics so a future inverse-applier matrix can swap DUD for proper stat-degradation effects without re-authoring presets. `spawnBounty` + `spawnDeathPrize` both route through the helper.
- **Slice 8d — Player-scaled spawners.** Generalised the per-spawner DSL with player-count scaling, regen batch, and hidden mode. New `SpawnerSpec` fields: `countPerPlayer` (additive scaling: effective max = `maxCount + countPerPlayer × playersInArena`), `radiusPerPlayer` (additive radius scaling), `regenBatch` (prizes per `intervalMs` tick), `hidden` (server keeps prize alive; client doesn't render it). `PrizeSystem.update` reads them per tick — per-arena player count via new `countPlayersInArena(ArenaId)` helper. New `Hidden` empty-marker component (registered for the wire); client-side `ModelContainer.addObject`/`updateObject` early-return when present. Math + scoping extracted to four static helpers (`computeEffectiveMaxCount`, `computeEffectiveRadius`, `computeRegenAmount`, `countPlayersInArena`) — 16-case `PrizeSystemScalingTest` covers additive formulas, regen edge cases, cross-arena scoping, null-arena fallback.
- **Subspace canon divergence absorbed into per-spawner DSL.** `[Prize] PrizeFactor` / `PrizeDelay` / `PrizeHideCount` / `MinimumVirtual` / `UpgradeVirtual` are not wired through `PrizeConfig` — instead absorbed conceptually into the per-spawner DSL with `countPerPlayer` / `intervalMs` / `regenBatch` / `radius` / `radiusPerPlayer`. Documented per-key in `SpawnerSpec` Javadoc divergence table; settings-pipeline tracker rows flipped to new `🔀 (diverged)` status.
- **`testarena` + `testconf` showcase preset.** New "everything live" arena auto-loaded alongside trench + deva. testconf clones trench-04-2026 with selected deva-pace value swaps (decoy / mine / portal lifetimes, prize lifetime range, repel feel, rocket speed, spawn at arena centre) for faster smoke-test feedback. testarena's `arena.groovy` adds the Slice 8d C3 opt-in on its `spawners {}` block — centre spawner exercises additive scaling + regen batch, NE-corner spawner exercises hidden mode. Going-forward policy: every new completed slice's active-arena migration lands here first.
- **`~arenas` chat command.** Lists every loaded arena with world bounds + centre, marks the player's current arena with `[you]`. Helps operators navigate a multi-arena zone now that three arenas auto-load.
- **Project rule `player-scaling.md`.** "When designing a spawn cadence, balance threshold, or any other gameplay quantity, ask whether it should scale with active player count." Soft phrasing, additive default, per-arena scope. Loaded automatically on system file reads.

Bug Fixes:
- **4 ECS components missing `implements EntityComponent`.** `CloakStatus`, `StealthStatus`, `XRadarStatus`, `AntiwarpStatus` declared the field set but didn't extend `EntityComponent` — silently un-storable. Surfaced during Slice 6a fixture work; all four fixed.
- **`AntiwarpEnergy` had a `boolean enabled` field.** Copy-paste error from the parallel toggle component — Javadoc and sibling `*Energy` components held an int drain rate. Fixed to int + `getEnergy()` accessor; no other callers existed (Slice 6b).

Breaking Changes:
- **`PrizeSpawnerSpec` → `SpawnerSpec` rename** (Slice 8d C1). Record renamed; `ArenaConfig.prizeSpawners()` → `spawners()`; `arena.groovy` block `prizeSpawners { … }` → `spawners { … }`; `GameEntities.createWeightedPrizeSpawner` (both overloads) → `createSpawner`; `GroovyArenaLoader.PrizeSpawnersBlock` → `SpawnersBlock`. Cascade through 4 prod callsites + 3 module testers + ArenaConfig field. Only structural rename — no behaviour change.
- **`Spawner` ECS component constructor extended** from 6 → 10 args. Single internal caller (`GameEntities.createSpawner`); ABI-safe.
- **Behaviour change in active arenas (Slice 6a).** trench/weasel and trench/leviathan now drain energy while cloaked/stealthed at the canonical Subspace rate (was: silently no-op since the appliers threw `UnsupportedOperationException` and the drain system didn't exist). Operators can dial the drain or set status=0 in `ships.groovy` if the rates feel wrong.
- **Behaviour change in active arenas (Slice 6b).** trench/spider has start-active xradar (energy=200/1000 per cs = 20/sec drain); trench/lancaster + shark + leviathan + terrier can acquire xradar via prize. Most deva ships have xradar with energy=0 → drain math is 0 → no actual energy cost (matches operator intent in legacy fragments).
- **`zone.groovy autoLoad` gained `'testarena'`.** Three arenas now boot on server start (was: trench + deva). Use `~arenas` to navigate.
- **`ShipConfig` gained `cloak` / `stealth` / `xradar` / `antiwarp` slots.** Existing presets that author the legacy `[Ship] *Status` / `*Energy` keys via `misc.groovy` no longer work — values must move into `ships.groovy` `cloak status: ..., energy: ...` etc. blocks. Active arenas (trench + deva) migrated; SVS-family deferred until those presets activate.
- **`StealthSystem` + `CloakSystem` + `XRadarSystem` + `AntiwarpSystem` deleted.** `StatusDrainSystem` replaces all four drain loops with one shared infrastructure (Slice 6a/6b).

Other:
- **Settings-pipeline trackers stayed in sync** with rules #6/#7. Five canon `[Prize]` keys (PrizeFactor / PrizeDelay / PrizeHideCount / MinimumVirtual / UpgradeVirtual) flipped from misleading `⚠️` to new `🔀 (diverged)` marker. Slice 4–8 rows flipped to `✅` as each shipped.
- **`config-consumers.md` gained 4 rows** for the new `SpawnerSpec` fields per always-on rule #3.
- **Per-arena player-count scoping is per-arena, not global.** `PrizeSystem.countPlayersInArena` filters the `Player` EntitySet by matching `ArenaId` — a key off-by-one / cross-arena leak guard tested in `PrizeSystemScalingTest`.
- **`SKILL.md` (arena-settings) updated** with the 4 new spawner-DSL parameters + their Subspace canon analogues.
- **Slice 8d test closure.** Static helper extraction enabled unit-testing without a `PrizeSystem` integration fixture (heavy: 4–5 dependent systems). Closes a longstanding gap noted in the spawn-projection harness backlog.
- **`SpawnerProjectionTest`** (5 cases) + **`GroovyArenaLoaderTest` extension** (2 cases) cover the factory + DSL-parse seams for Slice 8d's new fields.

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
