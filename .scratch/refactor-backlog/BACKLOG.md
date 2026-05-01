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

### PMD residual cleanup — verify status

PMD wiring (commit `5bb2e71`) surfaced 99 violations on first run; six cleanup batches landed before v1.0.7 (commits `7bd3482`, `c0cd5d5`, `aee9acb`, `a826163`, `85d860c`, `5634e6b`). Re-run PMD against current `infinity` and confirm whether any of the original three categories still have stragglers:

- **~10 genuine dead-code one-liners** — most likely resolved in batches 1, 2, 3, 6.
- **~30 chat-command handler false positives** — annotated in batch 4.
- **3 empty-foreach EntitySet drain idioms** in `AvatarSystem.java:106-112` — possibly addressed by batch 5 (`AvatarSystem.update TODO scaffolding`); verify the idiom is now suppressed or refactored.

## Additional cleanup targets — added 2026-04-30

Surfaced by a follow-up scan for architecture / library / framework smells beyond the original eighteen-commit arc. The dep-cleanup batch landed in the same change as this update; everything below is still open.

### Large-file splits (not already covered above)

- **`ArenaSystem.java`** (950 lines) — three-way split. `ArenaSystem` keeps lifecycle (reconcile/load/unload/slot allocation/bootstrap); extract `ArenaSpatialIndex` (`findArenaAt`, `findArenaEntityAt`, `arenaToWorld`, `worldToArena`, `getArenaSpawn`, `getArenaMap`) for spatial queries; extract `ArenaScriptWatcher` (`registerScriptWatch`, `unregisterScriptWatch`, `pollScriptWatches`) for hot-reload. Chat command handlers (`loadArenaByNameCommand` etc.) optionally move to a `ArenaCommandHandler` if they grow further.
- **`PrizeSystem.java`** (812 lines) — clean two-way split. `PrizeSystem` keeps spawn/contact orchestration; the 10 `handleAcquireXxx` methods (~250 lines) collapse into a `PrizeApplier` interface with one impl per prize type. Same shape as the `WeaponHandler` interface split planned for `WeaponsSystem`. Coordinate with the active [`prizes/`](../prizes/) scratch dir.
- **`SISpatialFactory.java`** (811 lines) — two-way split: gameplay-entity spatials (ship/flag/door/base/mob/tower/bomb/bullet/bounty) stay in the main factory; effect spatials (explosion variants, over1/2/5, particle emitters, warp/repel/burst) move to `EffectSpatialFactory`. Also flagged: only one usage of `jme3utilities.MyMesh` lives here — see "Heart" under "Library follow-ups" below.
- **`ModelViewState.java`** (888 lines) — promote inner classes. Outer state is ~280 lines; the rest is `Model`, `LargeModelContainer`, `BodyContainer`/`ModelContainer` inner classes. Promote each to package-private top-level in the same package. Pure refactor, no behavior change.
- **`GameServer.java`** (690 lines) — partial. Extract `expandBlockTypeIndexForTiles` + `expandCollidersForTiles` (lines 573-661, ~140 lines) to a `BlockTypeExpander` utility. Leave the rest — entry-point boilerplate is unavoidable.

Skipped intentionally:
- `SettingsTypes.java` (489 lines) — flat string-key catalog, zero methods. One file is the right shape for a constants registry. Possible follow-up: convert to typed `SettingKey<T>` records, but no split.

### Architecture micro-refactors

- **Loose `api/src/infinity/` root files.** Four files at api top level have no clear package: `Ship.java`, `Bombs.java`, `Guns.java`, `BombRegistry.java`. Move into `api/src/infinity/types/` (or absorb into `es/` where applicable). Note ABI risk — these have ~30 importers across `api/` + `infinity/` + tests; check `modules/` doesn't reach in. ([`IEnum`](../../api/src/infinity/IEnum.java) lived here too; it was removed in commit `cdf762f` after inlining `next()` into the two callers.)

- **`MapState` block create/delete interaction.** [`MapState.java:470, 494`](../../infinity/src/main/java/infinity/client/states/MapState.java) wires left/right mouse click through a raycast and calls `session.map(MapSystem.CREATE / MapSystem.DELETE, vec3)` to mutate world blocks. Two smells: (1) `MapSystem.CREATE` / `DELETE` are loose `static final byte` constants on a server-side system that the client reaches into — they slip past `LayerDependencyTest` only because the Java compiler inlines them at compile time and erases the bytecode dependency. (2) `MapState` mixes rendering with arena-click input handling. Cleanup: promote the action codes to a proper RMI command surface (typed enum or RMI method per intent — `createBlock(Vec3)` / `deleteBlock(Vec3)`), and consider extracting the click-to-block input handling into its own input AppState if the rendering responsibilities of `MapState` keep growing.

### Library follow-ups (still in use today)

- **commons-math 2.2 → commons-math3.** Used at one site ([`infinity/util/MathUtil.java`](../../infinity/src/main/java/infinity/util/MathUtil.java)) for `MathException`, `distribution.TDistributionImpl`, `stat.StatUtils`. The 2.x line shipped in 2010 and is end-of-life; `commons-math3:3.6.1` is the successor. Migration is mostly mechanical: package rename to `org.apache.commons.math3.*`, `MathException` → `MathRuntimeException`, `TDistributionImpl` → `TDistribution`. Worth doing before the next major version bump.
- **`'+'` version pinning audit.** Most non-Simsilica deps in [`build.gradle:8-23`](../../build.gradle) use `'+'` (latest). Pinned exceptions are JME (`3.9.0-stable`), log4j (`2.25.4`), slf4j (`2.0.17`), pager/sim-fx (`1.0.1-SNAPSHOT`), Heart (`9.3.0`), ini4j (`0.5.4`), commons-math (`2.2`). The `dependency-scout` agent tracks Simsilica drift; the rest deserve a one-pass review before a Maven Central cache flush moves the build under us.

## Historical: v1.0.7 dependency cleanup

Snapshot from commit [`cf811e0`](https://github.com/assofohdz/subspace-infinity/commit/cf811e0) (`chore(deps): drop unused libs`). Catalogues the five libraries removed in v1.0.7 with rationale for re-add. Kept here because "we tried X for Y" knowledge has a way of getting lost otherwise.

### Removed (zero imports across `*.java`)

These were declared in [`infinity/build.gradle`](../../infinity/build.gradle) but had no usages anywhere in the codebase. If gameplay needs any of them later, re-add the line with the version pinned at the date of the requirement.

- **`com.badlogicgames.gdx:gdx-ai:1.8.2`** — LibGDX's AI library: steering behaviours, behaviour trees, state machines, pathfinding. Originally pulled in to back the `infinity/ai/` Brain/Action/Strategy stack. The current AI stack in [`infinity/src/main/java/infinity/ai/`](../../infinity/src/main/java/infinity/ai/) is hand-rolled; gdx-ai was never wired up. If we ever want a "real" steering system or a battle-tested behavior-tree DSL, gdx-ai is mature and free of jME-incompatible deps.
- **`de.lighti:Clipper:6.4.2`** — Java port of [Angus Johnson's Clipper](http://www.angusj.com/delphi/clipper.php): 2D polygon boolean operations (union, intersection, difference, offset). Useful for arena boundary computation, line-of-sight polygon clipping, blocking-region merges. If we add geometric arena masks or LOS-based mechanics, Clipper or its successor `Clipper2` is the obvious dep.
- **`com.github.czyzby:noise4j:0.1.0`** — Procedural dungeon / map generation: cellular automata caves, Drunkard's Walk, room-and-corridor generators. Was likely intended for procedural arena generation that never landed. Subspace's gameplay leans on hand-authored `.lvl` maps, so this stayed unused. If we add a "generate a random arena" mode, noise4j is small and self-contained — the one-off 0.1.0 release suggests low maintenance though, so SquidLib or libnoise-java are alternatives.
- **`com.google.code.gson:gson:2.11.0`** — JSON parsing/serialization. The build comment said "Trying this for saving/loading configs" — that experiment was superseded by the Groovy `*.groovy` config layer. If we add a save-game format, savefile-to-server protocol, or external integration that demands JSON, Gson is the obvious choice.
- **`org.apache.commons:commons-collections4:4.4`** — Bidirectional maps (`BidiMap`), multi-key maps, `ListUtils`, `CollectionUtils`. Apparently never actually used; the JDK collections + Guava (also pulled in) cover the real usages. If we want `BidiMap` or `MultiValuedMap` later, commons-collections4 is the canonical choice.

### Stale commented-out dependency lines (also removed)

- `org.dyn4j:dyn4j:3.4.0` — old physics engine, replaced long ago by Moss/`mblock-physb`.
- `com.github.implicit-invocation:jwalkable:master-SNAPSHOT` — 2D polygonal pathfinding. Listed but never imported.
- `com.simsilica:mphys` — Moss raw physics. Code uses `mblock-physb` and `sio2-mphys`; raw `mphys` not needed.
- `com.badlogicgames.gdx:gdx-ai:1.8.1:sources` / `:javadoc` — IDE-only classifiers for gdx-ai (which we just removed).

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this list rather than re-running the friction scan from scratch. Keep the file in sync — when an item lands, delete it from this file in the same commit.
