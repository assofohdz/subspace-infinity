# Refactor backlog

Bag of non-settings cleanup items — architecture splits, library audits, naming notes. Pull from this file when "what's next?" comes up for cleanup work that doesn't belong in the settings pipeline.

**Subspace-settings wiring lives in the pipeline tracker + work queue, not here:**
- [`settings-pipeline.md`](settings-pipeline.md) — what's wired, gate-by-gate
- [`settings-pipeline-slices.md`](settings-pipeline-slices.md) — what to work on next

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this file. For Subspace-settings wiring, defer to the pipeline tracker + slices file.

**Keep this file in sync** — when an item lands, **delete** it from this file in the same commit. Do not strikethrough; crossed-out content is context-window clutter for future sessions.

**Not in this file** — Subspace-settings wiring lives in [`settings-pipeline.md`](settings-pipeline.md) + [`settings-pipeline-slices.md`](settings-pipeline-slices.md); pick from the queue file when settings work is the focus. RaM-pattern follow-ons live in [`replacement-as-mutation/PRD.md`](replacement-as-mutation/PRD.md). Spawn-projection harness expansion lives in [`spawn-projection-test-harness/PRD.md`](spawn-projection-test-harness/PRD.md).

## Architecture refactors

Two sources:
- **Physics gaps** (S3 / S9 / S10) — relocated from [`physics-audit.md`](physics-audit.md) (Slice P2 deliverable).
- **Arch-review-2 findings** — fresh post-refactor sanity check by 5-reviewer team (planner / config-2 / spawn / cleanup / client lenses) after this session's heavy refactor run (Tier 2/3, megasplit, hot-reload-from-dist, backlog-cleanup, backlog-final).

Items grouped by category, not lens. Effort/impact tags are S/M/L. See "Recommended next work" at the bottom for impact ÷ effort ranking.

### Physics canon gaps

#### S3 — Bomb bounce mechanic (`BombBounceCount`)
**M/L. Open design question.** Per-ship `BombBounceCount` (already authored in `ships.groovy`) needs a consumer. Bombs would survive wall contact for N bounces before impact-explode, instead of detonating or decaying on first contact. Open: where does the bounce decrement live — `ContactSystem`, a new `BombBounceSystem`, or via a `Bounces(int)` component? Subspace players notice immediately when wall-glance bombs don't bounce. See REFERENCE.md `## Bomb`.

#### S9 — Wormhole `Gravity` per-ship
**M/M.** Per-ship `Gravity(int)` and `GravityTopSpeed` components projected from `ShipConfig`. `GravityWellSystem` reads each ship's per-ship gravity to compute pull radius `R = 1.325 × g^0.507`. Replace the hardcoded `5000` in `LegacyMapProjector` with a per-wormhole value. **Risk:** Subspace's gravity is per-ship-experiences-pull, not per-wormhole-emits-pull, which inverts the natural ECS shape — needs a design pass before sliceification.

#### S10 — Afterburner mechanic (`AfterburnerEnergy`)
**M/M.** Self-contained slice once the input-binding queue catches up. `AfterburnerEnergy` per-ship (already authored) + new client input + temporary `Speed`/`Thrust` boost while held. Sits with the per-ship-input-mechanic queue.

### Layer + module hygiene

#### B1 — `AvatarMovementState` reaches into `infinity.systems.*` for protocol-byte constants
**M/M. Cross-lens-corroborated** (client #2 + planner #3). Imports `infinity.systems.AvatarSystem.WARBIRD/JAVELIN/...` + `ConsumableSystem.WARP/REPEL/...` as RMI dispatch bytes. `LayerDependencyTest` exemption list excludes `MobDebugState` + `HostState` only — this is a real layer violation that the test currently misses (Java inlines the byte primitives so no bytecode dependency). **Promote to `api/src/main/java/infinity/net/` or `api/src/main/java/infinity/events/` as enums** (mirrors the WeaponType + MapAction precedents).

#### B2 — `modules/` subproject empty but still wired in 5 places with wrong (Lemur/UI) deps
**S/M.** Post-`backlog-final` *Tester delete, `modules/src/` is zero `.java` files. `modules/build.gradle` still declares Lemur, lemur-proto, lemur-props, sim-ethereal, zay-es-net — wrong even for a server-side modules subproject. Both modules carry `runtimeOnly project(":modules")` + `testImplementation project(":modules")`. **Decision needed:** delete the subproject (drop from `settings.gradle`, remove all 5 references) OR keep with corrected slim deps if `groovy-module-loader/PRD.md` is imminent. [planner #1]

#### B4 — `zone/` shipped three times (server JAR + client JAR + dist root)
**S/M.** `zone/` ends up inside both module JARs *and* at dist root. `FileLocator` reads dist-root first; in-JAR copies are dead weight + a "did my edit take?" footgun if packaging order ever flips. **Pick one canonical shape:** in-JAR-only (drop dist-root copy + let `FileLocator` resolve through classpath fallback) OR dist-root-only (drop the `srcDirs` additions in `app-with-assets.gradle` + `infinity-server/build.gradle:58-64`). [planner #5]

### RaM single-writer violations

#### C1 — `Thrust` + `Speed` now have THREE writers post-pilot (RocketBuff race)
**M/L.** Rule pins `ShipSpawnSystem` as sole writer, but `ConsumableSystem.createRocketBuff:348-349` (fire-time override) and `RocketBuffSystem.onBuffRemoved:125-126` (revert from `RocketSnapshot`) both call `setComponent` directly. Same-tick reproject + buff-revert race produces order-dependent values. **Pilot extracted DamageSource for Health but left RocketBuff as a direct-mutation hole.** Funnel through `RocketBuffIntent` drained inside `ShipSpawnSystem.update`. [spawn #1]

#### C2 — Inventory + status component families have unresolved multi-writer collisions
**L/L.** ~15 prize appliers (`{Brick,Burst,Decoy,Portal,Rocket,Repel,AntiWarp,Cloak,Stealth,XRadar,MultiFire,Energy,Rotation,Thruster,TopSpeed,Recharge}PrizeApplier.java`) write components that `ShipSpawnSystem`/`ShipWeaponsProjector`/`ShipStatusProjector` also write. Pickup two `RepelPrizeApplier` + fire one repel in same tick → final `Repel` count is ordering-dependent. RaM PRD migration backlog #1 is the canonical fix; ready to land now that pilot proved the shape. [spawn #2 + config-2 #1]

#### C4 — RaM rule-file "live snapshot" stale post-pilot — most components un-ledgered
**M/M.** `replacement-as-mutation.md` lists only `ShipSpawnSystem`, `EnergySystem`, mphys integrator, decay reaper. Components like `Jitter`, `WarpTo` (already 2 writers!), `Bounce`, `Repellable`, `RocketActive`, `ProximityArmed`, `Dead`, `Captain`, `Frequency`, every inventory component — none documented. Without the ledger, reviewers can't tell legitimate canonical-writer from new-violation. RaM PRD slice 5; could partially automate via `grep setComponent.*new \w+\(`. [spawn #5]

### Settings pipeline gaps

#### D2 — `SpawnConfig.warpRadiusLimit` fully wired but has zero runtime consumer
**S/M.** Authored ✅ + Loader ✅ + Config ✅ + Subsystem ❌. Operator authoring `warpRadiusLimit 256` sees no behaviour change. **Either land the consumer (`WarpPrizeApplier` randomization within radius via `ArenaSpatialIndex`) or strip the field+parser until it does.** Half-wired knobs are a maintenance trap. [config-2 #3]

#### D3 — Dead SVS preset directories (`zone/conf/{svs,svs-league,svs-pb,svs-tce,svs-turf,base}/`)
**S/M.** None referenced from active `arena.groovy` files. Author keys with no loader, no config slot, no consumer — patterns future contributors may copy assuming they work. **Either delete the unreferenced presets OR wire one as a CI fixture asserting every authored key resolves.** [config-2 #6]

### Tests / tooling debt

#### E1 — `LegacyMapProjector` + `WallLightDecorator` extracted "as testable" — zero tests written
**M/M.** Both classes' Javadoc explicitly says "independently testable strategy" — the *whole rationale* for the extraction. `RadarStateLogic` from the same era got 6 unit tests in `backlog-final`. These got none. Gameplay regressions in tile projection or wall-run light decoration are silent today. **Mirror `BulletFactoryTest` shape: synthetic `World` + fake `EngineConfigProvider`, project a fixture, assert.** [cleanup #1]

#### E2 — Spawn-projection harness slices 2-5 stale; `hot-reload-from-dist` shipped without slice-3 coverage
**M/M.** Slice 3 ("Hot-reload diff event surface") is *exactly* the seam the conf-fragments hot-reload depends on. The hot-reload-from-dist commit was verified by manual `sed`-and-watch-the-log; nothing automated guards regression. The `EnergySystemIntentTest` fixture from slice 1d makes slice 3 nearly mechanical. [cleanup #2]

#### E4 — `pmdTest` hard-disabled across all modules
**S/M.** `infinity.java-conventions.gradle:118-120` says "too noisy for early adoption" — stale rationale. Test count has grown to 34 java files with no PMD discipline. **Drop the disable, capture a `max<Project>PmdTestViolations` baseline, let the existing ratchet apply.** [cleanup #4]

### Naming / convention

#### F1 — `*Spec` namespace overlap forces `SpawnerCreateSpec` rename
**M/M.** `api.config.SpawnerSpec` (template tier, arena DSL declaration) collides with `api.sim.specs.SpawnerCreateSpec` (factory-call argument). The 18-record namespace introduced in `backlog-final` overloaded the `Spec` suffix that `*Config` already used. **Rename `*Spec` → `*Args` in `api/sim/specs/`** while it's recent (18 records, mechanical import updates). [config-2 #5]

#### F2 — `requireSystem` retrofit incomplete — canonical writers + hot-path systems still on `getSystem`
**S/M.** 14 systems extend `BaseInfinitySystem`, but `EnergySystem`, `ShipSpawnSystem`, `PrizeSystem`, `ArenaSystem`, `DeathSystem`, `WorldSystem`, `ChecksWorldSystem`, `RegionSystem`, `GravitySystem`, `MovementInputSystem` still use raw `getSystem(...)`. The retrofit's value (uniform throw + greppable message) is undermined for the systems most likely to throw at boot. **Mechanical base-class swap + 1-line per `getSystem` site.** [spawn #3]

## Recommended next work

Ranked by impact ÷ effort given the post-arch-review-2 finding set. Items in the same band are roughly interchangeable.

### Tier 2 — small wins (S/S–S/M)

1. **F2** — Complete `requireSystem` retrofit on the 10 holdout systems (`EnergySystem`, `ShipSpawnSystem`, `PrizeSystem`, `ArenaSystem`, `DeathSystem`, …).

### Tier 3 — focused slices (S–M / M)

2. **B1** — Promote `AvatarMovementState` protocol bytes to api enums (cross-lens-corroborated; closes a real layer leak).
3. **F1** — `*Spec` → `*Args` rename (mechanical now, expensive later as the 18 records calcify).
4. **B4** — `zone/` ship-once cleanup (pick canonical packaging shape).
5. **E4** — Re-enable `pmdTest` + capture per-module test baselines.
6. **E1** — Land tests for `LegacyMapProjector` + `WallLightDecorator` (collect the carrot the BACKLOG dangled).
7. **E2** — Spawn-projection harness slice 3 (hot-reload diff event surface; guards the seam manual-tested in 1f1be383).
8. **C4** — Audit + populate the RaM rule "live snapshot" (~40 component types one-line each; partially automatable).
9. **B2** — `modules/` subproject decision (delete, OR land Groovy module loader, OR slim deps with explicit "future loader payload" status).
10. **D2** — `SpawnConfig.warpRadiusLimit` consumer (or strip until consumer lands).
11. **D3** — Dead SVS preset directories — delete OR wire as CI fixture.

### Tier 4 — bigger refactors (M/L)

12. **C1** — RocketBuff `Thrust`/`Speed` canonical writer migration. Closes a real RaM violation post-pilot; pairs naturally with the next item.
13. **C2** — Inventory + status family multi-writer migration (RaM PRD slice 1). ~15 applier sites + new intent components; the largest live RaM cluster.

### Physics canon gaps (separate pile, see top of section)

S3 (bomb bounce), S9 (wormhole gravity), S10 (afterburner) — gameplay-faithfulness work, not architecture cleanup. Pick when you want to close a player-noticed canon gap rather than a contributor-noticed code smell.
