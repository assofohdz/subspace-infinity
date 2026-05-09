# Architectural review — Subspace Infinity

Read-only review by team `s6-ship-radius` (5 reviewers across 5 concern slices). Branch: `slice/s7-mine-speed` HEAD `9c1abec4`. Date: 2026-05-09.

23 tech-debt findings ranked below by **impact ÷ effort**. Findings are sourced from five parallel reviews:
- **planner** — module boundaries, api-contracts, cross-module layering
- **config-2** — config + settings pipeline (Pattern 4, Groovy adapters, prize-appliers)
- **spawn** — ECS architecture + server-side systems + EntitySet lifecycle
- **cleanup** — tests + tooling + dev workflow
- **client** (temporary 5th teammate, shut down post-review) — client / UI / rendering / input

## Tier 1 — Same-day fixes, high or material wins

| # | Finding | Where | Win |
|---|---|---|---|
| 1 | **`AvatarSystem.getShipCount` leaks `EntitySet` per call** — gets entities, never releases. Direct violation of `entity-sets.md`. | `infinity/src/main/java/infinity/systems/AvatarSystem.java:321` | **L/S** — 1-line `try/finally`. Only system in tree that fails the gets-vs-releases audit. |
| 2 | **F1 HelpState lies** — hardcoded `keyHelp[]` doesn't include thrust/turn/bomb/bullet/mine/burst/ships/repel; references commented-out `CameraMovementFunctions`. Player-facing UX bug. | `infinity/src/main/java/infinity/client/states/HelpState.java:79-95, 147-170, 257` | **M/S** — regenerate from `InputMapper.getFunctionIds()` (already iterated by `dumpInputMappings`). |
| 3 | **`ModelViewState.cleanup` leaks** — releases `flags` but not `avatarEntity` (WatchedEntity, line 358), doesn't remove its "Lobs" `DebugHud` label, doesn't null `posRef`. | `infinity/src/main/java/infinity/client/states/ModelViewState.java:277-286` | **M/S** — 4 lines. |
| 4 | **`LayerDependencyTest` archunit gap** — only enforces `infinity.es..` + `infinity.events..`. `infinity.sim..` (`GameEntities`, `AIEntities`, ~30 classes) and `infinity.config..` (`*Config` records) are uncovered despite api-contracts.md mandating them. | `infinity/src/test/java/infinity/architecture/LayerDependencyTest.java:23-33` | **M/S** — 2-line edit, structural correctness gain. |
| 5 | **`TileType` + `TileKey` non-final fields** — violates `components.md` immutability. Constructors already initialize once; refactor is mechanical. | `api/src/infinity/es/TileType.java:15-17`, `api/src/infinity/es/TileKey.java:16-18` | **S/S** — add `final` to 6 declarations across 2 files. |
| 6 | **`Main.java` AppState graveyard** — ~10 commented-out `new XState()` lines + 2 commented mapper-init calls. Several referenced classes (`PostProcessingState`, `BloomPostState`, `SkyState`, `GridState`, `SettingsState`) still exist as live `.java`. New devs can't tell dead from held-back. | `infinity/src/main/java/infinity/Main.java:85-109, 177-178, 205-207` | **M/S** — delete-or-restore each. |
| 7 | **25/30 prize-appliers missing REFERENCE.md anchors** — `.claude/rules/prize-applier.md` mandates Javadoc cite. Only `AntiWarp`, `Cloak`, `Stealth`, `XRadar`, `WarpPrize` comply; `Bomb`/`Mine`/`Brick`/`Burst`/`Repel`/`Shields`/`BouncingBullets`/`Portal`/`Recharge` etc. have no canon link. | `infinity/src/main/java/infinity/systems/ship/applier/` | **M/S** — half-day mechanical Javadoc batch. |
| 8 | **`SISpatialFactory` dead code** — 4 unused private methods (`createBase`, `createMob`, `createParticleEmitter`, plus one more) annotated `@SuppressWarnings("unused")`; `DEBUG_COG = false` constant gates 3 unreachable branches. | `infinity/src/main/java/infinity/client/states/SISpatialFactory.java:57, 152, 239, 247, 290, 309, 328, 432` | **S-M/S** — pure delete. |
| 9 | **`LayerDependencyTest` simple-name exemptions** — `MobDebugState` and `HostState` exempted by simple class name. A new class with that name silently absorbs the exemption. | `infinity/src/test/java/infinity/architecture/LayerDependencyTest.java:67-69` | **S/S** — change to `doNotHaveFullyQualifiedName(...)`. |

## Tier 2 — High-impact, medium-effort

| # | Finding | Where | Notes |
|---|---|---|---|
| 10 | **Manual O(N) scans across projectiles × victims** every tick (audit's old S4). Quadratic in playerload. ProximityFuseSystem, splash damage, bomb-safety-clear all walk full EntitySets per projectile per tick. | `ProximityFuseSystem.java:150`, `WeaponsDamageLogic.java:104`, `WeaponsSystem.bombSafetyClear` | **L/M** — needs 30-min spike on `mphys.PhysicsSpace.queryBounds` to confirm spatial-query plumbing exists. **Highest gameplay-impact** in the entire review IF the api is there. |
| 11 | **Static-analysis tools all `ignoreFailures = true`** — Checkstyle/PMD/Error Prone/NullAway warn-only with no ceiling. `./gradlew :infinity:check` exits green with 943 WARN. The "ratchet to failing once clean" comment exists; the ceiling doesn't. | `buildSrc/src/main/groovy/infinity.java-conventions.gradle:83, 106, 147, 163` | **L/S-M** — capture per-tool baselines; add `failOnExcess` property. Locks in the touched-files ratchet rule structurally. |
| 12 | **`WeaponsSystem` god-class** at 854 lines, 8 EntitySets, fire dispatch + 5-projectile reaping all co-located. Three helper extractions exist (`WeaponsLogic`, `WeaponsDamageLogic`, `WeaponsEligibility`) but the class itself hasn't shrunk. | `infinity/src/main/java/infinity/systems/ship/WeaponsSystem.java` | **M/M** — `WeaponsFireSystem` + `WeaponsReaperSystem` are clean cuts along existing seams. |
| 13 | **`ConfigRegistry` wither + DISPATCH boilerplate** — adding a new `*Config` slot touches 4 files. ~140 lines of pass-through. Adding `ShrapnelConfig` etc. (planned per tracker) means touching every existing wither. | `infinity/src/main/java/infinity/settings/ConfigRegistry.java:183-285`, `ConfigRegistrySystem.java:65-151` | **M/M** — replace with `Map<Class<?>, Object>` slot store or `ConfigSlot<T>` enum. |
| 14 | **Adapter scaffolding duplication** across 12 fragment loaders + 16 NaN/Inf validation guards. ~600 LOC of pattern-not-info. | `infinity/src/main/java/infinity/settings/{Bomb,Bullet,Burst,Decoy,Mine,Portal,Prize,PrizeWeights,Repel,Rocket,Spawn,Brick}Adapter.java` | **M/M** — abstract `SingleClosureAdapter<C,B>` + `Validators` helper. Care with per-adapter Javadoc DSL blocks. |
| 15 | **Zero tests in `infinity.{client,server,net}` packages** — 27 test files cover api factories + settings + 8 systems; client AppStates / RMI / network glue have zero coverage. Manual launch is the only verification. | `infinity/src/test/java/infinity/{client,server,net}/` (don't exist) | **M/L** — full coverage is multi-slice. Seeding cost is small (one AppState-lifecycle smoke test). |
| 16 | **Spawn-projection test harness PRD started, slices stalled** — `SpawnerProjectionTest` itself notes "behavioural coverage sits behind the broader spawn-projection harness backlog (full PrizeSystem fixture is heavy — 4–5 dependent systems)." Memory `project_spawn_projection_test_gap.md` flags as recurring blocker. | `.scratch/spawn-projection-test-harness/PRD.md`, `infinity/src/test/java/infinity/sim/SpawnerProjectionTest.java:31` | **M/M** — template exists; each follow-on slice is small. |
| 17 | **Engine-tier hot-reload gap** — arena-scope tunables hot-reload via `ConfigRegistrySystem.replace()`; engine-tier (12 collision radii + scale knobs) requires server restart. Exactly the surface where iteration speed matters during physics tuning. | `api/src/infinity/config/EngineConfig.java:11`, `infinity/src/main/java/infinity/settings/GroovyEngineLoader.java:23` | **M/M** — extend `EngineConfigSystem` with watcher mirroring `ConfigRegistrySystem`. Single-file source — simpler than per-arena. |

## Tier 3 — Smaller wins or longer-term

| # | Finding | Where | Notes |
|---|---|---|---|
| 18 | **`getSystem + null-check + throw` boilerplate** ×7+ for `EngineConfigSystem` alone (more for `EntityData` / `MPhysSystem` / `ConfigRegistrySystem` / `ArenaSystem`). | 7 systems incl. `RepelSystem`, `WeaponsSystem`, `ConsumableSystem`, `MovementInputSystem`, `AvatarSystem` | **S/S** — `BaseInfinitySystem.requireSystem(Class<T>)` helper, retrofit incrementally via touched-files ratchet. |
| 19 | **`WatchedEntity` release lifecycle inconsistency** — `PositionHudState` releases in `onDisable`, others in `cleanup`. Detach-while-disabled may double-release or never-release depending on jME order. | `JitterState.java:67`, `PositionHudState.java:92`, `RadarState.java:250`, `AvatarMovementState.java:153`, `ModelViewState.java:284` | **S-M/S** — pin convention in `entity-sets.md`. |
| 20 | **`GameEntities` ABI churn** — 35 public-static methods, 13 commits in last 50, growing backward-compat-overload count. Works today; will calcify. | `api/src/infinity/sim/GameEntities.java` (976 LOC) | **M/M** — split into `ShipFactory`/`WeaponFactory`/`MapFactory` or introduce per-factory builders. |
| 21 | **`GroovyShipLoader` monolith** at 789 LOC owns FALLBACK 8-ship preset + `ShipConfigBuilder` + adapter. In-source 8-ship default has drift risk vs `*-04-2026/ships.groovy`. | `infinity/src/main/java/infinity/settings/GroovyShipLoader.java` | **M/M** — extract `ShipFallback`, `ShipConfigBuilder`. Defer until next ship-config field-add slice naturally touches the file. |
| 22 | **`infinity/` mega-module** — 243 files, server+client+systems+ai+sim+settings+net co-located. ArchUnit-test enforcement at boot vs compile-time enforcement via Gradle modules. Long-term cost of past iteration speed. | `infinity/src/main/java/infinity/**` | **L/L** — multi-week. Not a now-task. |
| 23 | **`api/` has no test sourceset** — 7 api-side factory tests (`BombFactoryTest`, `BulletFactoryTest`, etc.) live in `infinity/src/test/java/infinity/sim/`, coupling api-test runtime to full server-side dependency graph. | `api/src/` (no `test/`); tests live in `infinity/src/test/java/infinity/sim/` | **S-M/M** — add Gradle `test` configuration to `api/build.gradle`; move 7 factory-test files; ensure they don't reach into infinity-only types. Open Q: keep tests with fixtures, or split for stricter api isolation? |
| 24 | **`Main.java` carries upstream Simsilica copyright, not project SPDX** — CLAUDE.md rule #2 mandates SPDX BSD-3 + project copyright on all `*.java`. Spotless `licenseHeaderFile` is intentionally not configured (silent-relicense risk on upstream code is the right judgement) — but `Main.java` is unambiguously a project file. | `infinity/src/main/java/infinity/Main.java:1-35` | **S/S** — one file, one judgement call. |

## Patterns the team flagged as working well

- **api/ → infinity/ direction discipline holds** — `grep -rln "import infinity.\(systems\|server\|client\|modules\|ai\)" api/src` returns zero hits after 50+ slices.
- **`modules/` cleanliness** — all 6 module classes (`basicTester`, `doorTester`, `lightTester`, `prizeTester`, `warpTester`, `wangTester`) only import `infinity.sim.*` + jME math + Simsilica primitives.
- **Pattern 4 template→projection→component is consistently applied** — `ShipConfig` → `ShipSpawnSystem.project*` → ECS components. Recent S6/s7 slices land cleanly because the pattern is well-grooved.
- **Logic-extraction discipline is real and consistent** — `WeaponsLogic`, `WeaponsDamageLogic`, `WeaponsEligibility`, `ConsumableLogic`, `MapSystemLogic`, `ArenaLogic`, `ShipWeaponsProjector`, `ShipStatusProjector`. Systems delegate to static helpers rather than absorb complexity.
- **Decay/TTL canonicalization fully respected** — zero parallel `*Lifetime` / `*Ttl` / `*ExpiresAt` / `*Decay` components. `RocketTime` is correctly modeled (per-ship duration template → projection writes `Decay` deadline).
- **Client-side ECS reads only** — `grep ed.setComponent` in `infinity.client.*` returns zero hits. `client-read-only.md` honored.
- **`BodyPosition` (not RMI polling) drives avatar render** — `AvatarMovementState.getInterpolatedAvatarPosition:218-260` prefers SimEthereal interpolation, falls back to RMI only when buffer is unfilled.
- **Lazy-resolve avatar id in `update()`** — `JitterState`, `PositionHudState`, `InfinityCameraState`, `RadarState`, `ModelViewState.tryInitializeAvatar` all wait for `GameSessionState.getAvatarEntityId()` before binding watches. The Javadoc on `PositionHudState:108-148` is the canonical write-up.
- **Backward-compat overload pattern in `GameEntities`** — primary takes new param; no-param version forwards via `EngineConfig.DEFAULTS`. Predictable for module authors (despite TD-20's growth concern).
- **`pmdPath` task** (`infinity.java-conventions.gradle:137`) — surgical per-file PMD scoping; cleanly supports the touched-files ratchet rule.
- **`LayerDependencyTest`** (74 lines, 3 ArchUnit rules) — covers api/server/client/modules/ai layering. Compact + gating + the ONE test that actually fails the build today (despite TD-4/TD-9 gaps).
- **PRD-as-folder pattern** in `.scratch/<feature>/PRD.md` — multi-document slice work stays organized.
- **Workflow vacuum is clean post-prune** — neither `CLAUDE.md` nor `.claude/rules/*.md` has any orphan reference to the deleted `multi-machine-workflow.md` / `active-work.md` / `config-consumers.md`.

## Open questions surfaced by the review

- Should api-contracts.md cover `infinity.sim..` + `infinity.config..` as enforced layers (resolves TD-4)? Or is the current narrow scope intentional?
- `com.jme3.math.ColorRGBA` in `GameEntities.java` — api-contracts.md says jME math types are OK, but `ColorRGBA` is rendering-adjacent. Borderline call.
- TD-22's mega-module split — is there a file/LOC threshold to defend, or is "split when something hurts" the policy?
- TD-11 ratchet flip — when does `ignoreFailures = false` flip for each tool? Need per-tool baseline + acceptance criterion.
- Should `api/` get its own test sourceset (TD-23), or do api-side factory tests stay in `infinity/test` because that's where the fixtures live?
- TD-17 hot-reload — is engine-tier tuning expected to continue, or has it settled? Determines whether the watcher is worth the effort.
- TD-10 spatial-query spike — does `mphys.PhysicsSpace.queryBounds` actually exist with the signatures the audit assumes? 26 unsurveyed mphys files (incl. `Hit`, `HitResults`, `QueryVolume`, `SphereVolume`, `Frustum`) need a 30-min Explore pass before sliceifying.
- TD-12 extraction direction — `WeaponsFireSystem` vs `WeaponsReaperSystem` carve is clean, but do they share an EntitySet? Two systems reading the same set is fine; two systems writing to the same component type is forbidden by `systems.md`. Needs a producer-audit.
- Settle on **one** project rule for `WatchedEntity` / `EntitySet` release lifecycle (`cleanup()` vs `onDisable()`) — extend `entity-sets.md`.
- Are `PostProcessingState` / `BloomPostState` / `SkyState` / `GridState` / `SettingsState` intended for restoration, or are they dead? They occupy ~1.5K LOC and aren't wired to `Main`.

## Recommendations — biggest wins

If you want to land 3-4 quick fixes that address the broadest surface:

- **"Discipline-ratchet" PR** — Tier 1 #1, #4, #5, #9 as one bundle (~30 min total). Closes the EntitySet leak, extends archunit to api/sim+api/config, fixes TileType/TileKey immutability, hardens archunit exemptions. All four are mechanical, no behaviour change, prevent classes of regressions structurally.
- **"Client-leak-cleanup" PR** — Tier 1 #2 + #3 (~1 hour). User-visible fix (F1 Help) + leak fix (ModelViewState).
- **"Prize-applier canon anchors" PR** — Tier 1 #7 as a half-day mechanical pass. Satisfies an existing rule for 25 files.

If you want the **biggest gameplay impact**: Tier 2 #10 (spatial-query promotion) — but only after a 30-min spike on the unsurveyed `mphys` files to confirm `queryBounds` plumbing.

If you want the **biggest discipline lock-in**: Tier 2 #11 (static-analysis ceilings) — single-day infra work that converts every future commit's PMD ratchet into a build-failure ceiling.
