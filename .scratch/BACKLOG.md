# Refactor backlog

Bag of non-settings cleanup items — architecture splits, library audits, naming notes. Pull from this file when "what's next?" comes up for cleanup work that doesn't belong in the settings pipeline.

**Subspace-settings wiring lives in the pipeline tracker + work queue, not here:**
- [`settings-pipeline.md`](settings-pipeline.md) — what's wired, gate-by-gate
- [`settings-pipeline-slices.md`](settings-pipeline-slices.md) — what to work on next

Some items still need a design call before any work happens.

## Architecture refactors

### `modules/` directory — design call

Six `*Tester` stubs (`basicTester`, `doorTester`, `lightTester`, `prizeTester`, `wangTester`, `warpTester`) exist with no runtime instantiator after `AdaptiveLoader` retirement (commit `b0e7911`). Decision pending: delete them (the [`groovy-module-loader/PRD.md`](groovy-module-loader/PRD.md) preserves the gameplay intent), or keep them as scaffolding that the future Groovy module loader will attach to.

Surface to user before doing any work.

### Eliminate Subspace pixels from gameplay types — author + carry world units only

The simulation layer naturally speaks in world units (1 unit ≈ 1 tile). Subspace canon authors several knobs in pixels at the canonical 16 px/tile rate, and a few of those still carry the pixel value through Infinity's internal types instead of converting at the operator boundary.

Today's pixel-flavored gameplay types (audit as of slice S5):
- [`RepelConfig.distancePixels`](../api/src/infinity/config/RepelConfig.java) — `int` in pixels.
- [`RepelDistance`](../api/src/infinity/es/ship/actions/RepelDistance.java) component — `int getPixels()`.
- [`BombConfig.explodeRadius`](../api/src/infinity/config/BombConfig.java) Javadoc — already migrated to tiles for the field itself, but Javadoc still references pixels for SVS-port operators.

Already-clean precedent: `BombConfig.explodeRadius` was migrated tile-units-only in slice 9a and operators porting from SVS divide by 16 themselves. Same shape applies to repel:
- Rename `distancePixels` → `distance` (or `distanceWorldUnits`); store `double` in tiles.
- `repel.groovy` author key from `distance: <pixels>` → `distance: <tiles>` (e.g. SVS canon 512 px → 32).
- `RepelAdapter` drops the pixel-flavored input; consumes a `Number tiles`.
- `RepelDistance` component renamed (`getRadiusWorldUnits()` returns `double`).
- `RepelSystem` drops the `/ 16` conversion; constant `PIXELS_PER_TILE` deletes.

Wire-format change on `RepelDistance` — coordinated server+client update, but no client reads `RepelDistance` today (component is server-only) so the cost is just the consumer rename in `RepelSystem` + tests.

Out of scope: rendering code that genuinely deals with image pixels (`BlockGeometryIndex`'s tileset loader). The cleanup is the **gameplay** types only — anywhere a value flows through the simulation in pixels rather than world units.

Would also be the natural moment to introduce a centralized `PIXELS_PER_TILE` engine-tier constant **for operator-boundary conversion only** (e.g. an SVS-import tool or `~set` admin command's input adapter), if a centralized px↔tile bridge ever proves useful.

Surfaced during slice S5 manual test (RepelSystem's `/ 16` conversion was the trigger to notice the broader pattern).

### `AvatarMovementFunctions` keybinding cleanup

[`AvatarMovementFunctions.java`](../infinity-client/src/main/java/infinity/client/AvatarMovementFunctions.java) `mapActions()` has four `if (!inputMapper.hasMappings(F_<X>)) { inputMapper.map(F_REPEL, KEY_<Y>); }` blocks where `<X>` is `F_DECOY`/`F_ROCKET`/`F_BRICK`/`F_ATTACH` but the body always maps `F_REPEL` (looks like copy-paste rot). Net effect: `F_REPEL` is mapped to F3 + F4 + F5 + F7, while `F_DECOY`/`F_ROCKET`/`F_BRICK`/`F_ATTACH` get **no** key bindings at all. Plus a commented-out shift-key mapping (original repel binding).

Surfaced during slice S5 manual test (LEVIATHAN repel firing fine via F3/F4/F5/F7, but the action keys for the other features are wrong).

Cleanup pass: walk every `inputMapper.map(F_*, KEY_*)` call site, fix the function-vs-key pairing, settle on canonical bindings (Subspace canon: F3 = repel? F4 = warp? — verify against Continuum defaults), drop the commented-out historical mappings. Plus: consider whether `F_DECOY` / `F_ROCKET` / `F_BRICK` / `F_ATTACH` features still exist or are stale (would explain why nobody noticed they had no keys).

Out of scope for any specific gameplay slice; surface as its own keybinding-cleanup chore when picked up.

### `MapSystem` 2-job split

The 629-line file still mixes two responsibilities:

- **`LegacyMapProjector`** — pure-function lvl-decode → world cell writes. Extract as standalone class taking `(LevelFile, Vec3d offset, int tileBase, long createdTime, World, EntityData, PhysicsSpace) → HashSet<Vec3d>`.
- **`WallLightDecorator`** — wall-run light emitter generator. Extract as standalone strategy taking `(short[][] tiles, Vec3d offset, World, HashSet<Vec3d>)`.

After: `MapSystem` keeps the cohesive "what maps are loaded where" story (load/unload/swap, spiral placement, async orchestration). The projector and decorator become independently testable — the projector via fixture `.lvl` files, asserting cells + entities. Block constants (`INVISIBLE_BLOCK_TYPE`, `LIGHT_EMITTER_BLOCK_TYPE`) hoisted to `InfinityConstants`.

### `ArenaSystem` spatial-index extraction

801 lines. Two of the three originally-proposed extractions already landed: chat command handlers live in [`ArenaCommandsSystem`](../infinity-server/src/main/java/infinity/systems/ArenaCommandsSystem.java) and hot-reload polling lives in [`ArenaReloadWatcher`](../infinity-server/src/main/java/infinity/systems/ArenaReloadWatcher.java).

What remains: extract `ArenaSpatialIndex` for spatial queries — `findArenaAt`, `findArenaEntityAt`, `arenaToWorld`, `worldToArena`, `getArenaSpawn`, `getArenaMap`. After this `ArenaSystem` keeps lifecycle only (reconcile/load/unload/slot allocation/bootstrap).

### `SISpatialFactory` 2-way split

685 lines. Gameplay-entity spatials (ship/flag/door/base/mob/tower/bomb/bullet/bounty) stay in the main factory; effect spatials (explosion variants, over1/2/5, particle emitters, warp/repel/burst) move to `EffectSpatialFactory`. Also flagged: only one usage of `jme3utilities.MyMesh` lives here — see "Library follow-ups" below.

### Factory parameter records

`WeaponFactory.createMine(EntityData, EntityId, PhysicsSpace, long, Vec3d, long, String, double)` is 8 positional args. Builder or parameter records would help. ABI-breaking — coordinate with module authors before doing it.

### Radar `ArenaFootprint` — current-vs-neighbor styling

Slice U1 landed all loaded arenas' footprints with the same outline + fill colors (`arenaTintColor` / `arenaOutlineColor`). Visually correct but doesn't distinguish "the arena I'm in" from "neighboring arenas." Subspace players think of one arena as theirs, the rest as adjacent worlds — the radar should reflect that.

Fix shape: add a "current arena" detection on the client (already have avatar position + per-arena `ArenaFootprint`/`ArenaMap` bounds — point-in-polygon test, or use server-side `ArenaMembership` if/when that lands), branch styling at render time:
- Avatar's arena: `arenaTintColor` + `arenaOutlineColor` (today's full-strength values)
- Other loaded arenas: `arenaTintColorMuted` + `arenaOutlineColorMuted` (e.g. 50% saturation)

Two new theme entries; one extra branch in `ArenaFootprintContainer.addObject` (or per-frame restyle if avatar changes arena). Add a `~set arenaOutlineMuted <hex>` admin path if Slice B5 (typed `~set`) lands.

Out of scope until enough U1 in-game time confirms outlines are useful as-is. Picked up if uniformity feels noisy.

### `MapState` block create/delete interaction

[`MapState.java:442, 449`](../infinity-client/src/main/java/infinity/client/states/MapState.java) wires left/right mouse click through a raycast and calls `session.map(MapSystem.CREATE / MapSystem.DELETE, vec3)` to mutate world blocks. Two smells: (1) `MapSystem.CREATE` / `DELETE` are loose `static final byte` constants on a server-side system that the client reaches into — they slip past `LayerDependencyTest` only because the Java compiler inlines them at compile time and erases the bytecode dependency. (2) `MapState` mixes rendering with arena-click input handling. Cleanup: promote the action codes to a proper RMI command surface (typed enum or RMI method per intent — `createBlock(Vec3)` / `deleteBlock(Vec3)`), and consider extracting the click-to-block input handling into its own input AppState if the rendering responsibilities of `MapState` keep growing.

## Library follow-ups

### `'+'` version pinning audit

Most non-Simsilica deps in [`build.gradle`](../build.gradle) `subprojects` block use `'+'` (latest). Pinned exceptions are JME (`3.9.0-stable`), gson (`2.11.0`), log4j (`2.25.4`), and slf4j (`2.0.17`). Pager and sim-fx are on `1.0.1-SNAPSHOT`. The `dependency-scout` agent tracks Simsilica drift; the rest deserve a one-pass review before a Maven Central cache flush moves the build under us.

## Naming / convention notes (kept for reference)

- **Spatial-name prefixes** — `WeaponsFireSystem.{BOMB,BULLET,MINE}_LEVEL_PREFIX` form `ShapeNames` strings the client maps to spatials. Framework convention; not a Pattern 4 candidate.
- **Default arena id** — `ArenaSystem.DEFAULT_ARENA_ID = "default"`. Framework convention.

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this file. For Subspace-settings wiring, defer to the pipeline tracker + slices file.

**Keep this file in sync** — when an item lands, **delete** it from this file in the same commit. Do not strikethrough; crossed-out content is context-window clutter for future sessions.

## Recommended next steps

Ranked by impact ÷ effort given the post-arch-review state. Items in the same band are roughly interchangeable; pick what's freshest in your head.

### Pick first — small, focused, low-risk

1. **`AvatarMovementFunctions` keybinding cleanup** (S/S). Real bug surfaced during S5 manual test — F_REPEL is mapped four times, F_DECOY/F_ROCKET/F_BRICK/F_ATTACH have no bindings. One file, one cleanup pass, immediate player-facing fix. Also a natural moment to verify whether the unbound features are still real or stale.
2. **Library `'+'` version pinning audit** (S/S). Cheap insurance against an upstream Maven cache flush silently moving the build under us. One pass through the root `build.gradle`'s `subprojects` block; pin everything to a specific version.

### After that — medium-effort focused slices

3. **Eliminate Subspace pixels from gameplay types** (S-M/M). `RepelConfig.distancePixels` + `RepelDistance` component — one cohesive slice. Already mapped step-by-step in this file. Sets the precedent for the broader "world units only in simulation types" pattern; shrinks the cognitive overhead for anyone tracing a pixel-flavored value.
4. **`MapState` block create/delete RMI cleanup** (M/M). Removes a real client→server-system layering smell (the `MapSystem.CREATE`/`DELETE` byte constants). Optional: extract click-to-block input handling out of `MapState` if/when its rendering responsibilities grow further.

### After that — bigger refactors with clearer ROI

5. **`MapSystem` 2-job split** (M/M). Extract `LegacyMapProjector` + `WallLightDecorator`. Real testability win: each pure function gets fixture-based tests. Block constants hoist to `InfinityConstants` as a side benefit.
6. **`ArenaSystem` spatial-index extraction** (M/M). The last of the three originally-proposed extractions; the other two already shipped. After this `ArenaSystem` is lifecycle-only.
7. **`SISpatialFactory` 2-way split** (M/M). Effect spatials move out; gameplay spatials stay. No urgent driver — pick this when a follow-on effect-spatial slice naturally touches the file.

### Needs design call BEFORE work

8. **`modules/` directory** — six `*Tester` stubs with no runtime instantiator after `AdaptiveLoader` retirement. Decision pending: delete (the [`groovy-module-loader/PRD.md`](groovy-module-loader/PRD.md) preserves the intent), OR keep as scaffolding for the future Groovy module loader. **Surface to user before touching.**
9. **`Factory` parameter records** — ABI-breaking; coordinate with module authors before doing it.

### Hold for now

10. **Radar `ArenaFootprint` muted styling** — wait until enough U1 in-game time confirms the uniform styling actually feels noisy. Don't pre-build the muted variant.

---

**Not in this file** — Subspace-settings wiring lives in [`settings-pipeline.md`](settings-pipeline.md) + [`settings-pipeline-slices.md`](settings-pipeline-slices.md); pick from the queue file when settings work is the focus. RaM-pattern follow-ons live in [`replacement-as-mutation/PRD.md`](replacement-as-mutation/PRD.md). Spawn-projection harness expansion lives in [`spawn-projection-test-harness/PRD.md`](spawn-projection-test-harness/PRD.md).
