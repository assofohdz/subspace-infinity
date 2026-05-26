# `ArenaSpatialFields` extraction — pull the spatial-field plumbing out of `BotBrainSystem`

Status: done
Landed: 2b7e23f2 (2026-05-26) — complexity 118 → 80; smoke-accepted in trench
Category: refactor
Type: no-behaviour-change

## Parent

[Bot AI v2 PRD](../PRD.md) — follow-up spun out of slice #07 (PRD table note:
"`ArenaSpatialFields` extraction spun out as follow-up"). Not a substrate slice;
a complexity-reduction refactor that de-risks #06.

## Why

`BotBrainSystem` accreted the per-arena spatial-field production layer
(executor lifecycle, `navByArena`, dynamic-field refresh, the gather helpers,
the `arenaNav` build with chokepoint pinning, and the `prizes` / `projectiles`
`EntitySet`s) across slices #02/#03/#07. The class cyclomatic complexity hit
118. Slice #06 ([ADR-0015](../../../docs/adr/0015-arena-objective-and-roles.md))
adds objective static-goal registration **at the same `arenaNav` build site**
(`fields.pin(...)`), which would pile more onto the god-method. Extracting the
spatial layer first gives #06 a clean seam to plug into.

## What to build

A new `infinity.ai.field.ArenaSpatialFields` helper (plain server class, owned by
`BotBrainSystem` — not a `BaseInfinitySystem`, because the brain needs synchronous
per-arena access each tick). It owns:

- the `bot-nav-builder` single-thread `ExecutorService` lifecycle;
- the `Map<String, ArenaNav>` per-arena holder;
- the `prizes` (`PrizeType` + `SpawnPosition`) and `projectiles`
  (`WeaponType` + `SpawnPosition`) `EntitySet`s;
- `lastDensityNanos` cadence state;
- the dynamic-field refresh (`refreshDynamicFields` + the `evictExpired`
  sweep) and its gather helpers (`gatherShipCells`, `gatherPrizeCells`,
  `gatherNewShotCells`, `prizesInArena`, `countPassable`);
- the lazy `arenaNav(arena, passable)` build (field construction +
  chokepoint pinning);
- the `ArenaNav` record (promoted to a public/top-level type so
  `BotBrainSystem.buildArenaContext` can read it).

`BotBrainSystem` then:

- constructs + `start()`s / `stop()`s `ArenaSpatialFields` in its own
  `start()` / `stop()`;
- calls `spatialFields.refresh(nowNanos)` from `update()` after the brain
  ticks;
- asks `spatialFields.forArena(arena, passable)` for the per-arena `ArenaNav`
  in `buildArenaContext`, then bundles it (with norms + synergy) into
  `ServerBotAiArenaContext`.

Capability-derivation (`refreshDerivation`, `deriveProfile`, `toArchetype`,
`applyTweaks`, `shipConfigs`) and brain-ticking (`tickBot`, `resolveSteer`,
`planOnCadence`, `maybeLogStuck`, `writeDebugSnapshot`, clock-hour, perception)
**stay** in `BotBrainSystem` — they are not spatial-field plumbing.

## Acceptance criteria

- [x] `ArenaSpatialFields` owns executor + `navByArena` + the two EntitySets +
      refresh + gather + `arenaNav` build (now `forArena`) + `ArenaNav` type
- [x] `BotBrainSystem` no longer references `navBuilder`, `navByArena`,
      `prizes`, `projectiles`, `lastDensityNanos`, or any gather/refresh helper
- [x] **No behaviour change** — fields refresh on the same cadence, chokepoints
      pin identically, the same `ServerBotAiArenaContext` reaches the brain
- [x] Compile + spotless clean
- [x] PMD ratchet on touched files; `BotBrainSystem` cyclomatic complexity
      118 → 80 (highest method 8); new files zero violations
- [x] Layer test passes (`server_modules_ai_must_not_depend_on_client`)
- [x] Manual smoke (HITL): bots navigate in trench, unchanged (2026-05-26)

## Blocked by

- None. #03/#07 already landed the code being moved.

## Comments
