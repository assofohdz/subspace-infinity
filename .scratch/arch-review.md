# Architectural review — Subspace Infinity

Read-only review by team `s6-ship-radius` (5 reviewers across 5 concern slices). Branch: `slice/s7-mine-speed` HEAD `9c1abec4`. Date: 2026-05-09.

13 active tech-debt findings ranked below by **impact ÷ effort**. Findings are sourced from five parallel reviews:
- **planner** — module boundaries, api-contracts, cross-module layering
- **config-2** — config + settings pipeline (Pattern 4, Groovy adapters, prize-appliers)
- **spawn** — ECS architecture + server-side systems + EntitySet lifecycle
- **cleanup** — tests + tooling + dev workflow
- **client** (temporary 5th teammate, shut down post-review) — client / UI / rendering / input

## Tier 2 — High-impact, medium-effort

| # | Finding | Where | Notes |
|---|---|---|---|
| 1 | **`WeaponsSystem` god-class** at 854 lines, 8 EntitySets, fire dispatch + 5-projectile reaping all co-located. Three helper extractions exist (`WeaponsLogic`, `WeaponsDamageLogic`, `WeaponsEligibility`) but the class itself hasn't shrunk. | `infinity/src/main/java/infinity/systems/ship/WeaponsSystem.java` | **M/M** — `WeaponsFireSystem` + `WeaponsReaperSystem` + `WeaponsImpactSystem` are clean cuts along existing seams. **Pilot for the Replacement-as-Mutation pattern** ([rule](../.claude/rules/replacement-as-mutation.md), [PRD](./replacement-as-mutation/PRD.md)) — Impact emits damage/energy intents, canonical writers drain. |
| 2 | **`ConfigRegistry` wither + DISPATCH boilerplate** — adding a new `*Config` slot touches 4 files. ~140 lines of pass-through. Adding `ShrapnelConfig` etc. (planned per tracker) means touching every existing wither. | `infinity/src/main/java/infinity/settings/ConfigRegistry.java:183-285`, `ConfigRegistrySystem.java:65-151` | **M/M** — replace with `Map<Class<?>, Object>` slot store or `ConfigSlot<T>` enum. |
| 3 | **Adapter scaffolding duplication** across 12 fragment loaders + 16 NaN/Inf validation guards. ~600 LOC of pattern-not-info. | `infinity/src/main/java/infinity/settings/{Bomb,Bullet,Burst,Decoy,Mine,Portal,Prize,PrizeWeights,Repel,Rocket,Spawn,Brick}Adapter.java` | **M/M** — abstract `SingleClosureAdapter<C,B>` + `Validators` helper. Care with per-adapter Javadoc DSL blocks. |
| 4 | **Zero tests in `infinity.{client,server,net}` packages** — 27 test files cover api factories + settings + 8 systems; client AppStates / RMI / network glue have zero coverage. Manual launch is the only verification. | `infinity/src/test/java/infinity/{client,server,net}/` (don't exist) | **M/L** — full coverage is multi-slice. Seeding cost is small (one AppState-lifecycle smoke test). |
| 5 | **Spawn-projection test harness PRD started, slices stalled** — `SpawnerProjectionTest` itself notes "behavioural coverage sits behind the broader spawn-projection harness backlog (full PrizeSystem fixture is heavy — 4–5 dependent systems)." Memory `project_spawn_projection_test_gap.md` flags as recurring blocker. | `.scratch/spawn-projection-test-harness/PRD.md`, `infinity/src/test/java/infinity/sim/SpawnerProjectionTest.java:31` | **M/M** — template exists; each follow-on slice is small. |
| 6 | **Engine-tier hot-reload gap** — arena-scope tunables hot-reload via `ConfigRegistrySystem.replace()`; engine-tier (12 collision radii + scale knobs) requires server restart. Exactly the surface where iteration speed matters during physics tuning. | `api/src/infinity/config/EngineConfig.java:11`, `infinity/src/main/java/infinity/settings/GroovyEngineLoader.java:23` | **M/M** — extend `EngineConfigSystem` with watcher mirroring `ConfigRegistrySystem`. Single-file source — simpler than per-arena. |

## Tier 3 — Smaller wins or longer-term

| # | Finding | Where | Notes |
|---|---|---|---|
| 7 | **`getSystem + null-check + throw` boilerplate** ×7+ for `EngineConfigSystem` alone (more for `EntityData` / `MPhysSystem` / `ConfigRegistrySystem` / `ArenaSystem`). | 7 systems incl. `RepelSystem`, `WeaponsSystem`, `ConsumableSystem`, `MovementInputSystem`, `AvatarSystem` | **S/S** — `BaseInfinitySystem.requireSystem(Class<T>)` helper, retrofit incrementally via touched-files ratchet. |
| 8 | **`WatchedEntity` release lifecycle inconsistency** — `PositionHudState` releases in `onDisable`, others in `cleanup`. Detach-while-disabled may double-release or never-release depending on jME order. | `JitterState.java:67`, `PositionHudState.java:92`, `RadarState.java:250`, `AvatarMovementState.java:153`, `ModelViewState.java:284` | **S-M/S** — pin convention in `entity-sets.md`. |
| 9 | **`GameEntities` ABI churn** — 35 public-static methods, 13 commits in last 50, growing backward-compat-overload count. Works today; will calcify. | `api/src/infinity/sim/GameEntities.java` (976 LOC) | **M/M** — split into `ShipFactory`/`WeaponFactory`/`MapFactory` or introduce per-factory builders. |
| 10 | **`GroovyShipLoader` monolith** at 789 LOC owns FALLBACK 8-ship preset + `ShipConfigBuilder` + adapter. In-source 8-ship default has drift risk vs `*-04-2026/ships.groovy`. | `infinity/src/main/java/infinity/settings/GroovyShipLoader.java` | **M/M** — extract `ShipFallback`, `ShipConfigBuilder`. Defer until next ship-config field-add slice naturally touches the file. |
| 11 | **`infinity/` mega-module** — 243 files, server+client+systems+ai+sim+settings+net co-located. ArchUnit-test enforcement at boot vs compile-time enforcement via Gradle modules. Long-term cost of past iteration speed. | `infinity/src/main/java/infinity/**` | **L/L** — multi-week. Not a now-task. |
| 12 | **`api/` has no test sourceset** — 7 api-side factory tests (`BombFactoryTest`, `BulletFactoryTest`, etc.) live in `infinity/src/test/java/infinity/sim/`, coupling api-test runtime to full server-side dependency graph. | `api/src/` (no `test/`); tests live in `infinity/src/test/java/infinity/sim/` | **S-M/M** — add Gradle `test` configuration to `api/build.gradle`; move 7 factory-test files; ensure they don't reach into infinity-only types. Open Q: keep tests with fixtures, or split for stricter api isolation? |
| 13 | **`Main.java` carries upstream Simsilica copyright, not project SPDX** — CLAUDE.md rule #2 mandates SPDX BSD-3 + project copyright on all `*.java`. Spotless `licenseHeaderFile` is intentionally not configured (silent-relicense risk on upstream code is the right judgement) — but `Main.java` is unambiguously a project file. | `infinity/src/main/java/infinity/Main.java:1-35` | **S/S** — one file, one judgement call. |

## Patterns the team flagged as working well

- **api/ → infinity/ direction discipline holds** — `grep -rln "import infinity.\(systems\|server\|client\|modules\|ai\)" api/src` returns zero hits after 50+ slices.
- **`modules/` cleanliness** — all 6 module classes (`basicTester`, `doorTester`, `lightTester`, `prizeTester`, `warpTester`, `wangTester`) only import `infinity.sim.*` + jME math + Simsilica primitives.
- **Pattern 4 template→projection→component is consistently applied** — `ShipConfig` → `ShipSpawnSystem.project*` → ECS components. Recent S6/s7 slices land cleanly because the pattern is well-grooved.
- **Logic-extraction discipline is real and consistent** — `WeaponsLogic`, `WeaponsDamageLogic`, `WeaponsEligibility`, `ConsumableLogic`, `MapSystemLogic`, `ArenaLogic`, `ShipWeaponsProjector`, `ShipStatusProjector`. Systems delegate to static helpers rather than absorb complexity.
- **Decay/TTL canonicalization fully respected** — zero parallel `*Lifetime` / `*Ttl` / `*ExpiresAt` / `*Decay` components. `RocketTime` is correctly modeled (per-ship duration template → projection writes `Decay` deadline).
- **Client-side ECS reads only** — `grep ed.setComponent` in `infinity.client.*` returns zero hits. `client-read-only.md` honored.
- **`BodyPosition` (not RMI polling) drives avatar render** — `AvatarMovementState.getInterpolatedAvatarPosition:218-260` prefers SimEthereal interpolation, falls back to RMI only when buffer is unfilled.
- **Lazy-resolve avatar id in `update()`** — `JitterState`, `PositionHudState`, `InfinityCameraState`, `RadarState`, `ModelViewState.tryInitializeAvatar` all wait for `GameSessionState.getAvatarEntityId()` before binding watches. The Javadoc on `PositionHudState:108-148` is the canonical write-up.
- **Backward-compat overload pattern in `GameEntities`** — primary takes new param; no-param version forwards via `EngineConfig.DEFAULTS`. Predictable for module authors (despite TD-9's growth concern).
- **`pmdPath` task** (`infinity.java-conventions.gradle:137`) — surgical per-file PMD scoping; cleanly supports the touched-files ratchet rule.
- **Static-analysis ceilings wired** — per-tool, per-module strict pin via `gradle.properties` (Checkstyle + PMD); ceiling failure on any new violation. Error Prone + NullAway still warn-only (out of scope; need stdout-parsing).
- **`LayerDependencyTest`** — covers api/server/client/modules/ai layering AND api/sim+api/config (post-discipline-ratchet bundle), with FQN-anchored exemptions. Compact + gating + the ONE test that actually fails the build today.
- **PRD-as-folder pattern** in `.scratch/<feature>/PRD.md` — multi-document slice work stays organized.
- **Replacement-as-Mutation rule landed** ([rule](../.claude/rules/replacement-as-mutation.md), [PRD](./replacement-as-mutation/PRD.md)) — codifies single-writer-per-component + intent-queue discipline. WeaponsSystem extraction (TD-1) is the pilot migration slice.
- **Workflow vacuum is clean post-prune** — neither `CLAUDE.md` nor `.claude/rules/*.md` has any orphan reference to the deleted `multi-machine-workflow.md` / `active-work.md` / `config-consumers.md`.

## Open questions surfaced by the review

- `com.jme3.math.ColorRGBA` in `GameEntities.java` — api-contracts.md says jME math types are OK, but `ColorRGBA` is rendering-adjacent. Borderline call.
- TD-11's mega-module split — is there a file/LOC threshold to defend, or is "split when something hurts" the policy?
- Should `api/` get its own test sourceset (TD-12), or do api-side factory tests stay in `infinity/test` because that's where the fixtures live?
- TD-6 hot-reload — is engine-tier tuning expected to continue, or has it settled? Determines whether the watcher is worth the effort.
- TD-1 extraction direction — `WeaponsFireSystem` vs `WeaponsReaperSystem` vs `WeaponsImpactSystem` — three-way carve identified during the grilling for the spatial-query slice. Producer-audit + RaM pilot will resolve canonical writers as part of the slice.
- Settle on **one** project rule for `WatchedEntity` / `EntitySet` release lifecycle (`cleanup()` vs `onDisable()`) — extend `entity-sets.md`.
- Are `PostProcessingState` / `BloomPostState` / `SkyState` / `GridState` / `SettingsState` intended for restoration, or are they dead? They occupy ~1.5K LOC and aren't wired to `Main`. (Their commented-out instantiation in Main.java has been removed; the .java files are still on disk.)

## Recommendations — biggest wins

The Tier 1 same-day-fix and Tier 2 high-impact items (Main.java graveyard, SISpatialFactory dead code, spatial-query promotion, static-analysis ceilings) have all landed. Remaining items skew toward larger refactors and longer-term debts.

If you want the **next pilot for the Replacement-as-Mutation pattern**: Tier 2 #1 (`WeaponsSystem` god-class) — the three-way Fire/Reaper/Impact split is the natural first migration under the new rule.

If you want **lower-risk smaller-effort wins**: Tier 3 #7 (`requireSystem` helper, retrofit via touched-files ratchet) or #13 (`Main.java` SPDX header — one file, one judgement).
