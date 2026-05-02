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

`CoreGameConstants` is gone. The original 30-knob list collapsed two ways: half were phantoms (no consumers, deleted), the other half were relocated to their consuming systems as private constants so each cluster's Pattern 4 promotion can be handled independently without touching a centralized dumping ground. The clusters below are still Pattern 4 candidates — same shape (typed `*Config` record + Groovy DSL extension + system projection), now scoped per consumer.

**⚠ Dual source of truth today.** The Groovy fragments under `infinity/zone/conf/<preset>/` already declare operator-facing equivalents — `BulletDamageLevel`, `BombAliveTime`, `MineAliveTime`, `PrizeFactor`, `PrizeDelay`, etc. — populated into the per-arena `Ini` store at arena-load. **No Java code reads them** today (except `PrizeSystem` for `[PrizeWeight]`). The Java constants below and the Groovy values are parallel: same concepts, disconnected scales (Java `BOMB_DAMAGE = 10`; Groovy `BombDamageLevel 750`). Each Pattern 4 promotion below is therefore not just "move Java constant to Groovy" — it's "wire the Groovy value that already exists into a typed `*Config` record, then retire the Java constant." This is the deferred Phase B from [`conf-fragments-to-groovy/PRD.md`](../conf-fragments-to-groovy/PRD.md).

**Live clusters — co-located with consumers, awaiting per-arena promotion:**

- **Damage** — `WeaponsSystem.{BULLET,BOMB,GRAVBOMB}_DAMAGE` + `ActionSystem.THOR_DAMAGE`. Read at projectile-creation time; per-arena lookup would use the attacker's `ArenaId`.
- **Projectile decays** — `WeaponsSystem.{BULLET,GRAVBOMB,MINE}_DECAY_MS` + `ActionSystem.THOR_DECAY_MS` (split out from a shared bullet-decay reference; same value today, can diverge in a future tuning PR).
- **Prize defaults** — `GameEntities.PRIZE_DEFAULT_DECAY_MS` + `GameEntities.PRIZE_DEFAULT_MAX_COUNT`. `PrizeSpawnerSpec.ttlMillis` already overrides per-spawner; the constants are the fallback. `BOUNTY_VALUE` is co-located.
- **Burst count** — `WeaponsSystem.BURST_PROJECTILE_COUNT`.
- **Grav bomb knobs** — `WeaponsSystem.{GRAVBOMB_DELAY_MS, GRAVBOMB_WORMHOLE_FORCE}`.
- **Resource / tower** — `ResourceSystem.{RESOURCE_UPDATE_INTERVAL, GOLD_PER_SECOND, TOWER_COST}`. Gold / tower mechanics are inactive gameplay, so promotion isn't urgent.

**Spatial-name prefixes** — `WeaponsSystem.{BOMB,BULLET,MINE}_LEVEL_PREFIX` form `ShapeNames` strings the client maps to spatials. Framework convention; not a Pattern 4 candidate.

**Default arena id** — `ArenaSystem.DEFAULT_ARENA_ID = "default"`. Framework convention.

**Done — phantom culls (constants deleted, no behavioral change):**

- ~~Cooldowns (`THORCOOLDOWN`, `BURSTCOOLDOWN`)~~ — `THORCOOLDOWN` was already migrated (`ActionSystem.setCoolDownThor` reads `ThorFireDelay` projected from `ShipConfig.thors.fireDelayCs`); `BURSTCOOLDOWN` had no consumer (`WeaponsSystem.setCoolDown` for `BURST` is `// No delay on this for now`). The unenforced burst-cooldown gap is a feature item below.
- ~~Projectile speeds (`BASE/BOMB/BULLET/GRAVBOMB/THOR/BURSTPROJECTILESPEED`)~~ — none had a consumer; actual velocities are inline hardcodes in `WeaponsSystem.getAttackInfo` and `ActionSystem.getActionPosition` with a long-standing `// TODO: Look these settings up in SettingsSystem` comment. Wiring up tunable projectile speeds is a feature add — see below.
- ~~Health (`SHIPHEALTH`, `BASEHEALTH`, `MOBHEALTH`)~~ — none had a consumer; ship `Health` is projected from `ShipConfig.energy.initial()` already, and base/mob health were never tunable.
- ~~AI knobs (`MOBSPEED`, `MOBMAXFORCE`, `PATHWAYPOINTDISTANCE`, `PATHHELPERHEIGHT`, `PATHHELPERWIDTH`)~~ — entire `infinity/ai/` stack is on the shelf; if the AI is revived these come back as a typed config first.
- ~~`THORDECAY`~~ — no consumer.
- ~~`UPDATE_SETTINGS_INTERVAL_MS`~~ — no consumer.

## Tooling / test infrastructure

### Test harness for spawn / projection / prize flows — in-progress

Now tracked in [`spawn-projection-test-harness/PRD.md`](../spawn-projection-test-harness/PRD.md). Slice 1 (`ShipSpawnSystem` respawn projection) shipped — establishes the `GameSystemManager` + `DefaultEntityData` + `ConfigRegistrySystem` fixture pattern. Remaining slices: tuning projection (no live-pool reset), hot-reload diff event surface, no-config fallback, and one-per-cluster Pattern 4 candidates as those migrations land.

Memory still tracks the gap at [`project_spawn_projection_test_gap.md`](../../../../.claude/projects/-home-assofohdz-github-assofohdz-subspace-infinity/memory/project_spawn_projection_test_gap.md) — surface the PRD when proposing similar work; the gap shrinks one slice at a time.

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
- ~~**`ModelViewState.java`** (888 lines) — promote inner classes~~ — done. The six inner classes (`MarkVisible`, `Model`, `Body`, `BodyContainer`, `ModelContainer`, `LargeModelContainer`) were promoted to package-private siblings in [`infinity.client.states`](../../infinity/src/main/java/infinity/client/states/). Outer dropped from 888 → 509 lines; each promoted class takes a `ModelViewState owner` reference in its constructor and reaches outer state through it. Outer fields used across the new files (`ed`, `centerWorld`, `timeSource`, `flags`, `avatarFrequency`, `markerQueue`, `modelIndex`, `VIS_DELAY`) bumped from `private` to package-private; `updateSingleFlagMaterial` likewise. Pure refactor; behaviour identical.
- ~~**`GameServer.java`** (690 lines) — extract `expandBlockTypeIndexForTiles` + `expandCollidersForTiles`~~ — done. The two methods + their `BlockType` / `Field` / `CubeCollider` / `Arrays` / `MapTypes` imports moved into [`BlockTypeExpander`](../../infinity/src/main/java/infinity/server/BlockTypeExpander.java) (171 lines, server-side utility class). `GameServer` now delegates via `BlockTypeExpander.expandBlockTypeIndex()` and `BlockTypeExpander.expandCollidersForTiles(baseColliders)`, dropping from 690 → 593 lines. Entry-point boilerplate unchanged.

Skipped intentionally:
- `SettingsTypes.java` (489 lines) — flat string-key catalog, zero methods. One file is the right shape for a constants registry. Possible follow-up: convert to typed `SettingKey<T>` records, but no split.

### Feature gaps surfaced during cleanup

- **Burst cooldown is unenforced.** [`WeaponsSystem.setCoolDown`](../../infinity/src/main/java/infinity/systems/WeaponsSystem.java) for `BURST` is `// No delay on this for now`; there's no `BurstFireDelay` component, and the `CountStats` record for bursts has no `fireDelayCs` field. To wire it: replace `CountStats bursts` with `CountWithDelayStats bursts` in `ShipConfig`, add a `BurstFireDelay` component (mirror `ThorFireDelay`), project it from `ShipSpawnSystem.projectBursts`, and read it in `WeaponsSystem.setCoolDownBurst`. Adds a real feature (burst cadence cap), so it's a feature add rather than a Pattern 4 cleanup — surfaced here because the dead `BURSTCOOLDOWN = 250` constant referenced this gap before it was removed.

- **Projectile speeds are inline hardcodes.** [`WeaponsSystem.getAttackInfo`](../../infinity/src/main/java/infinity/systems/WeaponsSystem.java) and [`ActionSystem.getActionPosition`](../../infinity/src/main/java/infinity/systems/ActionSystem.java) compute projectile velocities with literal `addLocal(0, 0, 50)` etc. and a long-standing `// TODO: Look these settings up in SettingsSystem` comment. The six dead `*PROJECTILESPEED` constants in `CoreGameConstants` were the never-realized first attempt; they're gone now. Wiring up tunable speeds is a Pattern 4 candidate: add per-arena per-weapon-type projectile speeds (probably alongside damage in a unified projectile-config record), look them up at projectile-creation time using the attacker's `ArenaId`. Today's inline values: gun=50, bomb=25, gravbomb/mine=0 (inherits ship velocity), thor=50 (drifted from the deleted `THORPROJECTILESPEED=30`), burst is created with its own velocity logic. Treat any chosen "default" as a balance decision — the inline values won the drift, so they're production behavior.

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
