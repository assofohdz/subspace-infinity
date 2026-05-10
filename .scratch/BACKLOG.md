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

### Factory parameter records

`WeaponFactory.createMine(EntityData, EntityId, PhysicsSpace, long, Vec3d, long, String, double)` is 8 positional args. Builder or parameter records would help. ABI-breaking — coordinate with module authors before doing it.

### Radar `ArenaFootprint` — current-vs-neighbor styling

Slice U1 landed all loaded arenas' footprints with the same outline + fill colors (`arenaTintColor` / `arenaOutlineColor`). Visually correct but doesn't distinguish "the arena I'm in" from "neighboring arenas." Subspace players think of one arena as theirs, the rest as adjacent worlds — the radar should reflect that.

Fix shape: add a "current arena" detection on the client (already have avatar position + per-arena `ArenaFootprint`/`ArenaMap` bounds — point-in-polygon test, or use server-side `ArenaMembership` if/when that lands), branch styling at render time:
- Avatar's arena: `arenaTintColor` + `arenaOutlineColor` (today's full-strength values)
- Other loaded arenas: `arenaTintColorMuted` + `arenaOutlineColorMuted` (e.g. 50% saturation)

Two new theme entries; one extra branch in `ArenaFootprintContainer.addObject` (or per-frame restyle if avatar changes arena). Add a `~set arenaOutlineMuted <hex>` admin path if Slice B5 (typed `~set`) lands.

Out of scope until enough U1 in-game time confirms outlines are useful as-is. Picked up if uniformity feels noisy.

### `MapState` click-to-block input extraction

[`MapState`](../infinity-client/src/main/java/infinity/client/states/MapState.java) still mixes rendering with arena-click input handling. The `MapAction` enum + typed `GameSession.map(MapAction, Vec3d)` RMI landed in the backlog cleanup bundle; what remains is extracting the click-to-block input handling into its own input `AppState` if/when `MapState`'s rendering responsibilities grow further. Low urgency — defer until `MapState` size or mixed-concerns becomes a real maintenance pain.

## Naming / convention notes (kept for reference)

- **Spatial-name prefixes** — `WeaponsFireSystem.{BOMB,BULLET,MINE}_LEVEL_PREFIX` form `ShapeNames` strings the client maps to spatials. Framework convention; not a Pattern 4 candidate.
- **Default arena id** — `ArenaSystem.DEFAULT_ARENA_ID = "default"`. Framework convention.

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this file. For Subspace-settings wiring, defer to the pipeline tracker + slices file.

**Keep this file in sync** — when an item lands, **delete** it from this file in the same commit. Do not strikethrough; crossed-out content is context-window clutter for future sessions.

## Recommended next steps

Ranked by impact ÷ effort given the post-arch-review state. Items in the same band are roughly interchangeable; pick what's freshest in your head.

### Needs design call BEFORE work

1. **`modules/` directory** — six `*Tester` stubs with no runtime instantiator after `AdaptiveLoader` retirement. Decision pending: delete (the [`groovy-module-loader/PRD.md`](groovy-module-loader/PRD.md) preserves the intent), OR keep as scaffolding for the future Groovy module loader. **Surface to user before touching.**
2. **`Factory` parameter records** — ABI-breaking; coordinate with module authors before doing it.

### Hold for now

3. **Radar `ArenaFootprint` muted styling** — wait until enough U1 in-game time confirms the uniform styling actually feels noisy. Don't pre-build the muted variant.

---

**Not in this file** — Subspace-settings wiring lives in [`settings-pipeline.md`](settings-pipeline.md) + [`settings-pipeline-slices.md`](settings-pipeline-slices.md); pick from the queue file when settings work is the focus. RaM-pattern follow-ons live in [`replacement-as-mutation/PRD.md`](replacement-as-mutation/PRD.md). Spawn-projection harness expansion lives in [`spawn-projection-test-harness/PRD.md`](spawn-projection-test-harness/PRD.md).
