# Refactor backlog

Open follow-ups from the multi-day cleanup arc through commits c2209c2 → 31d2de6 (eighteen-commit session that landed `GroovySettingsHost`, the component immutability sweep, big-file culls, ByteArray → ByteBuffer migration, PMD wiring, and Pattern-4 ship-tuning promotion).

**Subspace-settings wiring lives in the pipeline tracker + work queue, not here:**
- [`settings-pipeline.md`](../settings-pipeline.md) — what's wired, gate-by-gate
- [`settings-pipeline-slices.md`](../settings-pipeline-slices.md) — what to work on next

Pull from this file when "what's next?" comes up for **non-settings** cleanup (architecture splits, libraries, tooling). Some items still need a design call before any work happens.

## Architecture refactors

### `modules/` directory — design call

Six `*Tester` stubs (`basicTester`, `doorTester`, `lightTester`, `prizeTester`, `wangTester`, `warpTester`) exist with no runtime instantiator after `AdaptiveLoader` retirement (commit `b0e7911`). Decision pending: delete them (the [`groovy-module-loader/PRD.md`](../groovy-module-loader/PRD.md) preserves the gameplay intent), or keep them as scaffolding that the future Groovy module loader will attach to.

Surface to user before doing any work.

### `MapSystem` 2-job split

The 938-line file culled in commit `5582cfd` (now ~693 lines after dead-code removal) still mixes two responsibilities:

- **`LegacyMapProjector`** — pure-function lvl-decode → world cell writes (lines ~421-561 of pre-cull). Extract as standalone class taking `(LevelFile, Vec3d offset, int tileBase, long createdTime, World, EntityData, PhysicsSpace) → HashSet<Vec3d>`.
- **`WallLightDecorator`** — wall-run light emitter generator (lines ~563-659 of pre-cull). Extract as standalone strategy taking `(short[][] tiles, Vec3d offset, World, HashSet<Vec3d>)`.

After: `MapSystem` keeps the cohesive "what maps are loaded where" story (load/unload/swap, spiral placement, async orchestration). The projector and decorator become independently testable — the projector via fixture `.lvl` files, asserting cells + entities. Block constants (`INVISIBLE_BLOCK_TYPE`, `LIGHT_EMITTER_BLOCK_TYPE`) hoisted to `InfinityConstants`.

### `WeaponsSystem` strong split

849-line system mixing the fire pipeline (per-weapon `canAttackX` / `setCoolDownX` / `deductCostOfAttackX` / `createProjectileX` × 5) with contact resolution (`newContact` for projectile-vs-ship + projectile-vs-world). Two splits:

1. Pull `newContact()` and `damageEntities`/`energyEntities` sets into a separate `WeaponContactSystem`. Removes `WeaponsSystem`-as-`ContactListener` shape and ~80 lines.
2. Collapse the 5 parallel weapon types into a `WeaponHandler` interface with `Bullet`/`Bomb`/`GravBomb`/`Mine`/`Burst` impls. `attack()` becomes `handlers.get(flag).fire(...)`. Kills the per-type switch duplication. Sets the polymorphism-collapse template the `PrizeApplier` registry already established.

### `ArenaSystem` 3-way split

950 lines. `ArenaSystem` keeps lifecycle (reconcile/load/unload/slot allocation/bootstrap); extract `ArenaSpatialIndex` (`findArenaAt`, `findArenaEntityAt`, `arenaToWorld`, `worldToArena`, `getArenaSpawn`, `getArenaMap`) for spatial queries; extract `ArenaScriptWatcher` (`registerScriptWatch`, `unregisterScriptWatch`, `pollScriptWatches`) for hot-reload. Chat command handlers (`loadArenaByNameCommand` etc.) optionally move to a `ArenaCommandHandler` if they grow further.

### `SISpatialFactory` 2-way split

811 lines. Gameplay-entity spatials (ship/flag/door/base/mob/tower/bomb/bullet/bounty) stay in the main factory; effect spatials (explosion variants, over1/2/5, particle emitters, warp/repel/burst) move to `EffectSpatialFactory`. Also flagged: only one usage of `jme3utilities.MyMesh` lives here — see "Library follow-ups" below.

### `GameEntities` split + parameter records

The audit's "split GameEntities into themed files" recommendation was deferred when Asser pointed out the file is the **module ABI**. The location is correct, but the file is still 690 lines (post commit `d87adf1`) with mixed concerns. Two improvements still open:

1. **Themed sub-files within `api/sim/`** — `WeaponEntities.java`, `WorldEntities.java`, `EffectEntities.java`. Module authors still find them via the package; navigation gets cleaner. Top-level `GameEntities` becomes a thin re-exporter or pure-aggregate.
2. **Parameter records** — `createMine(EntityData, EntityId, PhysicsSpace, long, Vec3d, long, String)` is 7 positional args. Builder or parameter records would help. ABI-breaking, so coordinate with module authors before doing it.

### `MapState` block create/delete interaction

[`MapState.java:470, 494`](../../infinity/src/main/java/infinity/client/states/MapState.java) wires left/right mouse click through a raycast and calls `session.map(MapSystem.CREATE / MapSystem.DELETE, vec3)` to mutate world blocks. Two smells: (1) `MapSystem.CREATE` / `DELETE` are loose `static final byte` constants on a server-side system that the client reaches into — they slip past `LayerDependencyTest` only because the Java compiler inlines them at compile time and erases the bytecode dependency. (2) `MapState` mixes rendering with arena-click input handling. Cleanup: promote the action codes to a proper RMI command surface (typed enum or RMI method per intent — `createBlock(Vec3)` / `deleteBlock(Vec3)`), and consider extracting the click-to-block input handling into its own input AppState if the rendering responsibilities of `MapState` keep growing.

## Tooling

### PMD residual cleanup — verify status

PMD wiring (commit `5bb2e71`) surfaced 99 violations on first run; six cleanup batches landed before v1.0.7. Re-run PMD against current `infinity` and confirm whether any of the original three categories still have stragglers:

- **~10 genuine dead-code one-liners** — most likely resolved in batches 1, 2, 3, 6.
- **~30 chat-command handler false positives** — annotated in batch 4.
- **3 empty-foreach EntitySet drain idioms** in `AvatarSystem.java:106-112` — possibly addressed by batch 5 (`AvatarSystem.update TODO scaffolding`); verify the idiom is now suppressed or refactored.

## Library follow-ups

### `'+'` version pinning audit

Most non-Simsilica deps in [`build.gradle:8-23`](../../build.gradle) use `'+'` (latest). Pinned exceptions are JME (`3.9.0-stable`), log4j (`2.25.4`), slf4j (`2.0.17`), pager/sim-fx (`1.0.1-SNAPSHOT`), and ini4j (`0.5.4`). The `dependency-scout` agent tracks Simsilica drift; the rest deserve a one-pass review before a Maven Central cache flush moves the build under us.

## Naming / convention notes (kept for reference)

- **Spatial-name prefixes** — `WeaponsSystem.{BOMB,BULLET,MINE}_LEVEL_PREFIX` form `ShapeNames` strings the client maps to spatials. Framework convention; not a Pattern 4 candidate.
- **Default arena id** — `ArenaSystem.DEFAULT_ARENA_ID = "default"`. Framework convention.

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this file. For Subspace-settings wiring, defer to the pipeline tracker + slices file.

**Keep this file in sync** — when an item lands, **delete** it from this file in the same commit. Do not strikethrough; crossed-out content is context-window clutter for future sessions.
