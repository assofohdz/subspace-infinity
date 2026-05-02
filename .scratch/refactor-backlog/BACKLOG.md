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

**Live clusters — typed `*Config` migration done; per-arena content (Phase B) deferred:**

- ~~**Damage** — `WeaponsSystem.{BULLET,BOMB,GRAVBOMB}_DAMAGE` + `ConsumableSystem.THOR_DAMAGE`~~ — promoted.
- ~~**Projectile decays** — `WeaponsSystem.{BULLET,GRAVBOMB,MINE}_DECAY_MS` + `ConsumableSystem.THOR_DECAY_MS`~~ — promoted.
- ~~**Burst count** — `WeaponsSystem.BURST_PROJECTILE_COUNT`~~ — promoted.
- ~~**Grav bomb knobs** — `WeaponsSystem.{GRAVBOMB_DELAY_MS, GRAVBOMB_WORMHOLE_FORCE}`~~ — promoted.
- ~~**Prize defaults** — per-arena resolution in `PrizeSystem`~~ — promoted. `GameEntities.{PRIZE_DEFAULT_DECAY_MS, PRIZE_DEFAULT_MAX_COUNT, BOUNTY_VALUE}` retained as api-factory fallback constants for module-author callers without server context (per `api-contracts.md`'s factory-ABI rule).

**The five clusters above migrated together** in a single sweep: new typed records [`BulletConfig`](../../api/src/infinity/config/BulletConfig.java) / [`BombConfig`](../../api/src/infinity/config/BombConfig.java) / [`GravBombConfig`](../../api/src/infinity/config/GravBombConfig.java) / [`MineConfig`](../../api/src/infinity/config/MineConfig.java) / [`ThorConfig`](../../api/src/infinity/config/ThorConfig.java) / [`BurstFireConfig`](../../api/src/infinity/config/BurstFireConfig.java) bundled into [`WeaponsConfig`](../../api/src/infinity/config/WeaponsConfig.java); plus [`PrizeConfig`](../../api/src/infinity/config/PrizeConfig.java). [`ConfigRegistry`](../../infinity/src/main/java/infinity/settings/ConfigRegistry.java) gained `weapons()` / `prize()` accessors with the `Builder` defaulting to `*Config.DEFAULTS` (= legacy Java values). `WeaponsSystem.weaponsFor(attacker)` and `ConsumableSystem.thorConfigFor(attacker)` look up per-attacker via `ArenaId`; arenas / void attackers fall back to `DEFAULTS`. **Zero behaviour change today** — every arena receives `DEFAULTS` — but the architecture now supports per-arena weapon tuning. **Phase B** (reading the actual untyped fragment values like `[Bullet] BulletDamageLevel 100` into the typed records) remains deferred; the typed slots are ready to receive them.

- **Resource / tower** — `ResourceSystem.{RESOURCE_UPDATE_INTERVAL, GOLD_PER_SECOND, TOWER_COST}`. Gold / tower mechanics are inactive gameplay, so promotion isn't urgent. Same shape as the weapons cluster: a `ResourceConfig` record + per-arena lookup; the consumer is single (`ResourceSystem`).

**Spatial-name prefixes** — `WeaponsSystem.{BOMB,BULLET,MINE}_LEVEL_PREFIX` form `ShapeNames` strings the client maps to spatials. Framework convention; not a Pattern 4 candidate.

**Default arena id** — `ArenaSystem.DEFAULT_ARENA_ID = "default"`. Framework convention.

**Done — phantom culls (constants deleted, no behavioral change):**

- ~~Cooldowns (`THORCOOLDOWN`, `BURSTCOOLDOWN`)~~ — `THORCOOLDOWN` was already migrated (`ConsumableSystem.setCoolDownThor` reads `ThorFireDelay` projected from `ShipConfig.thors.fireDelayCs`); `BURSTCOOLDOWN` had no consumer (`WeaponsSystem.setCoolDown` for `BURST` is `// No delay on this for now`). The unenforced burst-cooldown gap is a feature item below.
- ~~Projectile speeds (`BASE/BOMB/BULLET/GRAVBOMB/THOR/BURSTPROJECTILESPEED`)~~ — none had a consumer; actual velocities are inline hardcodes in `WeaponsSystem.getAttackInfo` and `ConsumableSystem.getActionPosition` with a long-standing `// TODO: Look these settings up in SettingsSystem` comment. Wiring up tunable projectile speeds is a feature add — see below.
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
- ~~**`PrizeSystem.java`** (812 lines) — collapse 10 `handleAcquireXxx` methods + switch into a `PrizeApplier` interface~~ — done. Replaced the 105-line dispatch switch + 10 inline handlers with a `Map<String, PrizeApplier>` registry built in `initialize()`. New [`infinity.systems.ship.applier`](../../infinity/src/main/java/infinity/systems/ship/applier/) package: `PrizeApplier` interface, `PrizeApplierContext` record, `CompositePrizeApplier`, 12 implemented leaf appliers (Bomb/Mine/Gun + Burst/Thor + Thruster/TopSpeed/Rotation/Energy/Recharge + QuickCharge/Dud), and 18 stub appliers that throw `UnsupportedOperationException` until their family pattern lands. Stubs are organized by family (LEVEL / COUNT / CAPABILITY / STATUS / INSTANT — see class javadocs). Dispatcher catches `UnsupportedOperationException` and logs+continues so unimplemented prize types degrade to visible no-ops instead of crashing the contact loop. PrizeSystem dropped from 812 → 580 lines. Sets the polymorphism-collapse template for the planned `WeaponsSystem` `WeaponHandler` split. Two follow-ups logged in the Pattern-4 leaks subsection below.
- **`SISpatialFactory.java`** (811 lines) — two-way split: gameplay-entity spatials (ship/flag/door/base/mob/tower/bomb/bullet/bounty) stay in the main factory; effect spatials (explosion variants, over1/2/5, particle emitters, warp/repel/burst) move to `EffectSpatialFactory`. Also flagged: only one usage of `jme3utilities.MyMesh` lives here — see "Heart" under "Library follow-ups" below.
- ~~**`ModelViewState.java`** (888 lines) — promote inner classes~~ — done. The six inner classes (`MarkVisible`, `Model`, `Body`, `BodyContainer`, `ModelContainer`, `LargeModelContainer`) were promoted to package-private siblings in [`infinity.client.states`](../../infinity/src/main/java/infinity/client/states/). Outer dropped from 888 → 509 lines; each promoted class takes a `ModelViewState owner` reference in its constructor and reaches outer state through it. Outer fields used across the new files (`ed`, `centerWorld`, `timeSource`, `flags`, `avatarFrequency`, `markerQueue`, `modelIndex`, `VIS_DELAY`) bumped from `private` to package-private; `updateSingleFlagMaterial` likewise. Pure refactor; behaviour identical.
- ~~**`GameServer.java`** (690 lines) — extract `expandBlockTypeIndexForTiles` + `expandCollidersForTiles`~~ — done. The two methods + their `BlockType` / `Field` / `CubeCollider` / `Arrays` / `MapTypes` imports moved into [`BlockTypeExpander`](../../infinity/src/main/java/infinity/server/BlockTypeExpander.java) (171 lines, server-side utility class). `GameServer` now delegates via `BlockTypeExpander.expandBlockTypeIndex()` and `BlockTypeExpander.expandCollidersForTiles(baseColliders)`, dropping from 690 → 593 lines. Entry-point boilerplate unchanged.

### Pattern-4 leaks fixed in PrizeSystem

- ~~**Bomb/Gun/Mine first-time-acquisition reads `ShipConfig` at apply time.**~~ Fixed. The first-time-acquisition branch in [`BombPrizeApplier`](../../infinity/src/main/java/infinity/systems/ship/applier/BombPrizeApplier.java) / [`GunPrizeApplier`](../../infinity/src/main/java/infinity/systems/ship/applier/GunPrizeApplier.java) / [`MinePrizeApplier`](../../infinity/src/main/java/infinity/systems/ship/applier/MinePrizeApplier.java) is gone — the `BombMaxLevel != null && BombCurrentLevel == null` combination is unreachable in current spawn flow (both project together on respawn), so the applier now logs a warning if it ever surfaces and skips. `getShipConfig()` and the `ConfigRegistrySystem` field were dropped from [`PrizeApplierContext`](../../infinity/src/main/java/infinity/systems/ship/applier/PrizeApplierContext.java); appliers are pure component reads. If a real "ship has Max but no Current" scenario emerges, the proper fix is projecting `*StartLevel` components at spawn, but that's deferred until evidence surfaces.

- ~~**`MaxMines 0` (and analogous untyped fragment keys) not honored.**~~ Architectural support landed; content migration deferred. [`ShipConfig`](../../api/src/infinity/config/ShipConfig.java) weapon/inventory fields (`bombs`, `guns`, `mines`, `bursts`, `thors`, `repels`) are now `@Nullable`; `ShipSpawnSystem.projectBombs`/`projectGuns`/etc. skip projection when the stat is null. With the projection skipped, the `*Max` component is absent, which the prize appliers already treat as "not allowed" (component-absence as the disallow signal). No behaviour change today — every existing `ships.groovy` still falls through to the `GroovyShipLoader.DEFAULT_*` permissive defaults. Honoring the untyped fragment's `MaxMines 0` etc. requires either reading them into the typed records (Phase B) or extending `ships.groovy` syntax to express disable; the architecture is now ready for either.

### Feature gaps surfaced during cleanup

- **Burst cooldown is unenforced.** [`WeaponsSystem.setCoolDown`](../../infinity/src/main/java/infinity/systems/ship/WeaponsSystem.java) for `BURST` is `// No delay on this for now`; there's no `BurstFireDelay` component, and the `CountStats` record for bursts has no `fireDelayCs` field. To wire it: replace `CountStats bursts` with `CountWithDelayStats bursts` in `ShipConfig`, add a `BurstFireDelay` component (mirror `ThorFireDelay`), project it from `ShipSpawnSystem.projectBursts`, and read it in `WeaponsSystem.setCoolDownBurst`. Adds a real feature (burst cadence cap), so it's a feature add rather than a Pattern 4 cleanup — surfaced here because the dead `BURSTCOOLDOWN = 250` constant referenced this gap before it was removed.

- **Open-vs-closed weapon levels — Phase B design call.** [`GunLevel`](../../api/src/infinity/GunLevel.java) and [`BombLevel`](../../api/src/infinity/BombLevel.java) are closed enums with 4 constants (`LEVEL_1`..`LEVEL_4` / `BOMB_1`..`BOMB_4`) — the typed Pattern-4 path caps weapon levels at 4. The untyped Groovy fragment path (`InitialGuns 3` / `MaxBombs 3` in `infinity/zone/conf/<preset>/ship-*.groovy`) is unconstrained — any int — but currently has no Java reader (only `[PrizeWeight]` is consumed from the merged `Ini`). When Phase B replaces the untyped fragment lookups with typed `*Config` records, decide: keep enums (Subspace-canonical 4-level cap, matches sprite atlas + protocol) or convert to `record GunLevel(int level)` / `record BombLevel(int level)` value classes (open levels, requires sprite-fallback strategy for level 5+ and adjusts the wire-protocol serializer). No urgency; defer until a real "level 5+" use case appears. Surfaced during the `Guns → GunLevel` / `Bombs → BombLevel` rename.

- **Projectile speeds are inline hardcodes.** [`WeaponsSystem.getAttackInfo`](../../infinity/src/main/java/infinity/systems/ship/WeaponsSystem.java) and [`ConsumableSystem.getActionPosition`](../../infinity/src/main/java/infinity/systems/ship/ConsumableSystem.java) compute projectile velocities with literal `addLocal(0, 0, 50)` etc. and a long-standing `// TODO: Look these settings up in SettingsSystem` comment. The six dead `*PROJECTILESPEED` constants in `CoreGameConstants` were the never-realized first attempt; they're gone now. Wiring up tunable speeds is a Pattern 4 candidate: add per-arena per-weapon-type projectile speeds (probably alongside damage in a unified projectile-config record), look them up at projectile-creation time using the attacker's `ArenaId`. Today's inline values: gun=50, bomb=25, gravbomb/mine=0 (inherits ship velocity), thor=50 (drifted from the deleted `THORPROJECTILESPEED=30`), burst is created with its own velocity logic. Treat any chosen "default" as a balance decision — the inline values won the drift, so they're production behavior.

### Architecture micro-refactors

- ~~**Loose `api/src/infinity/` root files** (`Ship.java`, `Bombs.java`, `Guns.java`, `BombRegistry.java`)~~ — addressed via concern-split + rename rather than relocation. The four enums mixed wire-protocol identity with client-only sprite/lighting data; the visual fields (`viewOffset`, `lightColor`, `lightRadius`, `visualOffset`) were extracted into four parallel enums under [`infinity.client.view`](../../infinity/src/main/java/infinity/client/view/) — `ShipVisuals`, `BombVisuals`, `GunVisuals`, `SpecialBombVisuals`. The api-side identity enums kept only identity; `Bombs` was renamed to `BombLevel` and `Guns` to `GunLevel` so each constant reads grammatically as "a bomb level" / "a gun level" rather than a plural ("a guns value"). `Ship` stayed `Ship` (already singular; renaming to `ShipType` would clash with the existing component class). `BombRegistry` deleted from api/ — its `THOR.viewOffset` consumer in `SISpatialFactory` now reads `SpecialBombVisuals.THOR.viewOffset`. Dead `lightColor` / `lightRadius` fields dropped (only referenced by their own constructor assignments — a pre-existing orphan). Net: api/ no longer imports `com.jme3.math.ColorRGBA` for client-rendering purposes (the remaining uses in `PointLightComponent` / `GameEntities` are network-serialized math-type fields, allowed by `api-contracts.md`). ([`IEnum`](../../api/src/infinity/IEnum.java) lived here too; it was removed in commit `cdf762f` after inlining `next()` into the two callers.)

- **`MapState` block create/delete interaction.** [`MapState.java:470, 494`](../../infinity/src/main/java/infinity/client/states/MapState.java) wires left/right mouse click through a raycast and calls `session.map(MapSystem.CREATE / MapSystem.DELETE, vec3)` to mutate world blocks. Two smells: (1) `MapSystem.CREATE` / `DELETE` are loose `static final byte` constants on a server-side system that the client reaches into — they slip past `LayerDependencyTest` only because the Java compiler inlines them at compile time and erases the bytecode dependency. (2) `MapState` mixes rendering with arena-click input handling. Cleanup: promote the action codes to a proper RMI command surface (typed enum or RMI method per intent — `createBlock(Vec3)` / `deleteBlock(Vec3)`), and consider extracting the click-to-block input handling into its own input AppState if the rendering responsibilities of `MapState` keep growing.

### Library follow-ups (still in use today)

- ~~**commons-math 2.2 → commons-math3**~~ — done by deletion. The lone consumer (`infinity/util/MathUtil.java`, a Grubbs-outlier helper with `min`/`max`/`avg`/`stdDev` utilities) had zero callers anywhere in the repo — only a `main()` self-test. YAGNI: deleted `MathUtil.java` (-329 lines) along with the EOL `commons-math:2.2` dep. If outlier detection or stats utilities are needed later, re-add `commons-math3:3.6.1` against fresh consumers — don't resurrect the 2.x bridge.
- **`'+'` version pinning audit.** Most non-Simsilica deps in [`build.gradle:8-23`](../../build.gradle) use `'+'` (latest). Pinned exceptions are JME (`3.9.0-stable`), log4j (`2.25.4`), slf4j (`2.0.17`), pager/sim-fx (`1.0.1-SNAPSHOT`), and ini4j (`0.5.4`). The `dependency-scout` agent tracks Simsilica drift; the rest deserve a one-pass review before a Maven Central cache flush moves the build under us.

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
