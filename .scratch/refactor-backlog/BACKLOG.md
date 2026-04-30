# Refactor backlog — items surfaced 2026-04-30

These are "still on the table" follow-ups from the multi-day cleanup arc that ran through commits c2209c2 → 31d2de6 (the eighteen-commit session that landed the GroovySettingsHost, component immutability sweep, big-file culls, ByteArray → ByteBuffer migration, PMD wiring, and Pattern-4 ship-tuning promotion).

Pull from this list when "what's next?" comes up. Not all of them deserve to be done — at least one (`modules/` deletion) needs a design call before any work happens.

## Architecture refactors (from the friction scan)

### `modules/` directory — design call

Six `*Tester` stubs (`basicTester`, `doorTester`, `lightTester`, `prizeTester`, `wangTester`, `warpTester`) exist with no runtime instantiator after `AdaptiveLoader` retirement (commit `b0e7911`). Decision pending: delete them (the [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) preserves the gameplay intent), or keep them as scaffolding that the future Groovy module loader will attach to.

Surface to user before doing any work.

### `MapSystem` 2-job split

The 938-line file culled in commit `5582cfd` (now ~693 lines after dead-code removal) still mixes two responsibilities the audit flagged:
- **`LegacyMapProjector`** — pure-function lvl-decode → world cell writes (lines ~421-561 of pre-cull). Extract as standalone class taking `(LevelFile, Vec3d offset, int tileBase, long createdTime, World, EntityData, PhysicsSpace) → HashSet<Vec3d>`.
- **`WallLightDecorator`** — wall-run light emitter generator (lines ~563-659 of pre-cull). Extract as standalone strategy taking `(short[][] tiles, Vec3d offset, World, HashSet<Vec3d>)`.

After: `MapSystem` keeps the cohesive "what maps are loaded where" story (load/unload/swap, spiral placement, async orchestration). The projector and decorator become independently testable — the projector via fixture `.lvl` files, asserting cells + entities. Block constants (`INVISIBLE_BLOCK_TYPE`, `LIGHT_EMITTER_BLOCK_TYPE`) hoisted to `InfinityConstants`.

### `WeaponsSystem` strong split

849-line system mixing the fire pipeline (per-weapon `canAttackX` / `setCoolDownX` / `deductCostOfAttackX` / `createProjectileX` × 5) with contact resolution (`newContact` for projectile-vs-ship + projectile-vs-world). Two splits:

1. Pull `newContact()` and `damageEntities`/`energyEntities` sets into a separate `WeaponContactSystem`. Removes `WeaponsSystem`-as-`ContactListener` shape and ~80 lines.
2. Collapse the 5 parallel weapon types into a `WeaponHandler` interface with `Gun`/`Bomb`/`GravBomb`/`Mine`/`Burst` impls. `attack()` becomes `handlers.get(flag).fire(...)`. Kills the per-type switch duplication.

Damage values (`BULLETDAMAGE`, `BOMBDAMAGE`, etc.) are tuning knobs — promote to Groovy as part of the split. See "More Pattern 4 migrations" below.

### `GameEntities` split + parameter records

The audit's "split GameEntities into themed files" recommendation was deferred when Asser pointed out the file is the **module ABI**. The location is correct, but the file is still 690 lines (post commit `d87adf1`) with mixed concerns. Two improvements still open:

1. **Themed sub-files within `api/sim/`** — `WeaponEntities.java`, `WorldEntities.java`, `EffectEntities.java`. Module authors still find them via the package; navigation gets cleaner. Top-level `GameEntities` becomes a thin re-exporter or pure-aggregate.
2. **Parameter records** — `createMine(EntityData, EntityId, PhysicsSpace, long, Vec3d, long, String)` is 7 positional args. Builder or parameter records would help. ABI-breaking, so coordinate with module authors before doing it.

## Pattern 4 migrations (multi-PR campaign)

`CoreGameConstants` still holds ~25 tuning knobs after commit `31d2de6` removed the six ship-spawn ones. Each cluster is its own future Pattern 4 migration following the shape established for ship weapon/inventory:

- **Damage** — `BOMBDAMAGE`, `BULLETDAMAGE`, `THORDAMAGE`, `GRAVBOMBDAMAGE` → projectile-config records, projected by `WeaponsSystem` per-fire
- **Projectile speeds** — `BASEPROJECTILESPEED`, `BOMBPROJECTILESPEED`, `BULLETPROJECTILESPEED`, `GRAVBOMBPROJECTILESPEED`, `THORPROJECTILESPEED`, `BURSTPROJECTILESPEED`
- **Decays** — `BULLETDECAY`, `THORDECAY`, `GRAVBOMBDECAY`, `MINEDECAY`, `PRIZEDECAY`
- **Cooldowns** — `THORCOOLDOWN`, `BURSTCOOLDOWN` (the others — gun/bomb/mine — are already done)
- **Health** — `SHIPHEALTH`, `BASEHEALTH`, `MOBHEALTH`
- **Burst count** — `BURSTPROJECTILECOUNT`
- **Bounty / prize** — `BOUNTYVALUE`, `PRIZEMAXCOUNT`
- **Grav bomb knobs** — `GRAVBOMBDELAY`, `GRAVBOMBWORMHOLEFORCE`
- **Resource rates** — `RESOURCE_UPDATE_INTERVAL`, `GOLD_PER_SECOND`
- **AI knobs** — `MOBSPEED`, `MOBMAXFORCE`, `PATHWAYPOINTDISTANCE`, `PATHHELPERHEIGHT`, `PATHHELPERWIDTH`
- **Tower** — `TOWERCOST`

Goal: each cluster moves to a `*Config` record + Groovy DSL extension + system projection. When all clusters are done, `CoreGameConstants` can be deleted.

True magic numbers stay (DEFAULTARENAID, BOMBLEVELPREPENDTEXT, BULLETLEVELPREPENDTEXT, MINELEVELPREPENDTEXT, UPDATE_SETTINGS_INTERVAL_MS) — these are framework convention strings / system tick intervals, not gameplay tuning.

## Tooling / test infrastructure

### Test harness for spawn / projection / prize flows

Stored in memory: [`project_spawn_projection_test_gap.md`](../../../../.claude/projects/-home-assofohdz-github-assofohdz-subspace-infinity/memory/project_spawn_projection_test_gap.md). End-to-end ECS flows have zero automated coverage; manual game-launch is the only verification path. Each Pattern 4 / spawn-system / projection refactor adds another piece to verify by hand. The cost compounds.

Suggested shape: minimal SiO2 `GameSystemManager` test fixture booting `EntityData` + `ConfigRegistrySystem` + system-under-test only, with a fixture `ConfigRegistry` for a synthetic arena. Synthesize a ship entity, advance one tick, assert components projected correctly.

The harness is a vertical slice in itself — write a PRD when picked up.

### PMD residual cleanup

PMD wiring (commit `5bb2e71`) surfaced 99 violations on first run:

- **~10 genuine dead-code one-liners** — `BasicEnvironment.world`, `EmptyLeafDb.worldData`, duplicate imports in `GameServer`, `allocatedSlot` initializer overwritten in `ArenaSystem`, `shipGravBombCost` in `WeaponsSystem`, `settings` fields in `prizeTester`/`wangTester`. Easy mop-up.
- **~30 chat-command handler false positives** — `UnusedFormalParameter` flags `playerEntityId`/`avatarEntityId`/`matcher` on chat handlers whose signature is fixed by a framework contract. Need per-method `@SuppressWarnings("PMD.UnusedFormalParameter")` or rule relaxation in the ruleset.
- **3 empty-foreach EntitySet drain idioms** in `AvatarSystem.java:106-112` — likely intentional Zay-ES drain pattern; verify and either suppress or refactor.

### `createWormhole2` rename

Numeric-suffix smell flagged in commit `d87adf1`. Both `createWormhole` and `createWormhole2` have callers — one is likely an experimental variant. Investigate, rename the experimental one to something semantic (e.g. `createWarpGate` if it's a different shape), or merge if they're redundant.

## Additional cleanup targets — added 2026-04-30

Surfaced by a follow-up scan for architecture / library / framework smells beyond the original eighteen-commit arc. The dep-cleanup batch landed in the same change as this update; everything below is still open.

### Large-file splits (not already covered above)

- **`ArenaSystem.java`** (950 lines) — three-way split. `ArenaSystem` keeps lifecycle (reconcile/load/unload/slot allocation/bootstrap); extract `ArenaSpatialIndex` (`findArenaAt`, `findArenaEntityAt`, `arenaToWorld`, `worldToArena`, `getArenaSpawn`, `getArenaMap`) for spatial queries; extract `ArenaScriptWatcher` (`registerScriptWatch`, `unregisterScriptWatch`, `pollScriptWatches`) for hot-reload. Chat command handlers (`loadArenaByNameCommand` etc.) optionally move to a `ArenaCommandHandler` if they grow further.
- **`PrizeSystem.java`** (812 lines) — clean two-way split. `PrizeSystem` keeps spawn/contact orchestration; the 10 `handleAcquireXxx` methods (~250 lines) collapse into a `PrizeApplier` interface with one impl per prize type. Same shape as the `WeaponHandler` interface split planned for `WeaponsSystem`. Coordinate with the active [`prizes/`](../prizes/) scratch dir.
- **`SISpatialFactory.java`** (811 lines) — two-way split: gameplay-entity spatials (ship/flag/door/base/mob/tower/bomb/bullet/bounty) stay in the main factory; effect spatials (explosion variants, over1/2/5, particle emitters, warp/repel/burst) move to `EffectSpatialFactory`. Also flagged: only one usage of `jme3utilities.MyMesh` lives here — see Heart entry in [`dep-cleanup-issue.md`](./dep-cleanup-issue.md).
- **`ModelViewState.java`** (888 lines) — promote inner classes. Outer state is ~280 lines; the rest is `Model`, `LargeModelContainer`, `BodyContainer`/`ModelContainer` inner classes. Promote each to package-private top-level in the same package. Pure refactor, no behavior change.
- **`GameServer.java`** (690 lines) — partial. Extract `expandBlockTypeIndexForTiles` + `expandCollidersForTiles` (lines 573-661, ~140 lines) to a `BlockTypeExpander` utility. Leave the rest — entry-point boilerplate is unavoidable.

Skipped intentionally:
- `SettingsTypes.java` (489 lines) — flat string-key catalog, zero methods. One file is the right shape for a constants registry. Possible follow-up: convert to typed `SettingKey<T>` records, but no split.

### Architecture micro-refactors

- **Loose `api/src/infinity/` root files.** Four files at api top level have no clear package: `Ship.java`, `Bombs.java`, `Guns.java`, `BombRegistry.java`. Move into `api/src/infinity/types/` (or absorb into `es/` where applicable). Note ABI risk — these have ~30 importers across `api/` + `infinity/` + tests; check `modules/` doesn't reach in. ([`IEnum`](../../api/src/infinity/IEnum.java) lived here too; it was removed in commit `cdf762f` after inlining `next()` into the two callers.)
- **Retire custom `Preconditions`.** [`infinity/util/Preconditions.java`](../../infinity/src/main/java/infinity/util/Preconditions.java) — 230 lines, likely re-implements `java.util.Objects.requireNonNull` and a few `IllegalArgumentException` helpers. Audit call sites, replace with stdlib (or Guava's `Preconditions` if the contract is more elaborate), delete.
- **Nullable annotation consolidation.** Project pulls in both `javax.annotation.Nullable` (jsr305, 8 files) and `org.jetbrains.annotations.Nullable` (1 file: [`api/sim/GameSounds.java`](../../api/src/infinity/sim/GameSounds.java)). Migrate the single jetbrains site to `javax.annotation`, drop the `org.jetbrains:annotations` dep. See [`dep-cleanup-issue.md`](./dep-cleanup-issue.md).

### Library follow-ups (still in use today)

- **commons-math 2.2 → commons-math3** — used at one site ([`infinity/util/MathUtil.java`](../../infinity/src/main/java/infinity/util/MathUtil.java)). 2.x is end-of-life. Mostly mechanical: package rename + a couple of class renames. See [`dep-cleanup-issue.md`](./dep-cleanup-issue.md).
- **Heart `jme3utilities.MyMesh` — single usage.** [`SISpatialFactory.java:64`](../../infinity/src/main/java/infinity/client/states/SISpatialFactory.java). 30-min check whether it can be replaced with raw `com.jme3.scene.Mesh` calls; if so, drop the Heart dep.
- **`'+'` version pinning audit.** Most non-Simsilica deps in [`build.gradle:8-23`](../../build.gradle) use `'+'`. The `dependency-scout` agent tracks Simsilica drift; the rest deserve a one-pass review before a Maven Central cache flush moves the build under us.

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this list rather than re-running the friction scan from scratch. Keep the file in sync — when an item lands, delete it from this file in the same commit.
