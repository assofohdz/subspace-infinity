# Dynamic scalar fields + `ChokepointAnalyzer` + `follow-traffic` behaviour

Status: done
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 7 of 12. Implements [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md). **Spatial substrate — gates the weapon behaviour slices (#08–#11).**

## What to build

The dynamic-field substrate. Per revised ADR-0012, spatial analysis is unified
around `ScalarField` specializations, **not a service suite** — the old
`TrafficHeatmap` / `ArenaCongestionField` / `CoverFinder` / `LineOfSightOracle`
framing is gone. This slice lands the dynamic field impls, the `FieldBlend`
composite, blended-gradient navigation ("go to G but avoid threats"), the
`ChokepointAnalyzer` load-time tile-list producer, and one consumer behaviour
(`follow-traffic`) to prove the substrate end-to-end.

Demo: bots drift toward where the action is (CombatDensity / EnemyDensity) and
navigate toward goals while bending away from threats — visibly different from
#02's straight-line tile seek.

## Acceptance criteria

- [x] `api/infinity.ai.field.FieldBlend` + `Weighted` + `FieldClamp` composite operators
- [x] Dynamic `ScalarField` impls in `infinity-server/.../ai/field/`:
  - [x] `ThreatField` — Σ over enemies of (weapon-range falloff, wall-blocked)
  - [x] `EnemyDensityField` / `AllyDensityField` — per-team position sums (renamed from the old `ArenaCongestionField`)
  - [x] `CombatDensityField` — recent firing events smeared in space + time-decayed (replaces the old `TrafficHeatmap`)
  - [x] `OpportunityField` — prize / ammo / ally-support value falloff
- [x] Blended-gradient composition: `gradientFor(goal) − k·threatGradient + j·opportunityGradient` feeds one `SeekDirection`
- [x] Update cadences read from `zone-bot-ai.groovy` (default ~330ms for density fields; per-event for combat/opportunity with a decay tick); fields ready immediately (accumulate from zero)
- [x] `api/infinity.ai.field.TileScored` record + `ChokepointAnalyzer` as a **load-time tile-list producer** (`hottest(int topN)`), not a runtime service; width-narrow detection over the passable grid at arena load; output unions into `BotAiHostService.onArenaLoad` goal registration (#03); top-N (`N`) is a `zone-bot-ai.groovy` knob (default 5)
- [x] `BotAiArenaContext` exposes `threat()`, `enemyDensity()`/`allyDensity()`, `combatDensity()`, `opportunity()`, `chokepoints()`
- [x] `follow-traffic` behaviour: enumerates top CombatDensity/EnemyDensity tiles, scores by heat × distance-decay, emits `NavigateToTile`; ships its `synergy { }` entry in `engine-bot-ai.groovy`
- [x] `CoverFinder` / `LineOfSightOracle` are **not** services — any LoS need is a one-off Bresenham raycast at the leaf
- [x] Unit tests: each field's accumulation + decay; `FieldBlend` math; blended-gradient direction; chokepoint scoring + top-N ordering; `follow-traffic` enumeration
- [~] Performance: per-tick dynamic update bounded by `O(liveShips + populatedTiles)`; benchmark 32 ships in 1024² within a single-digit-% of one tick budget — respawn-storm churn that inflated the `EntityId` counter is fixed (see code-todos "Suspected entity churn / leak"); formal 32-ship benchmark not yet run
- [x] PMD ratchet on touched files
- [x] Layer test passes

## Blocked by

- [#03 — Production flow-field navigation](./03-flow-field-navigation.md) (blended-gradient nav + chokepoint goal registration)
- [#04 — Capability-derivation pipeline](./04-capability-derivation.md) (`follow-traffic` synergy entry)
- [#05 — `TacticalPlanner` + baseline behaviours](./05-tactical-planner-baseline-behaviours.md) (behaviour enumeration host)

## Comments

### Substrate landed (2026-05-25, commit `565a8977`) — Inc 1–4

Spatial-field substrate shipped + smoke-accepted.

- **Inc 1** — `FieldBlend` / `Weighted` / `FieldClamp` composite operators + `ScalarField.EMPTY` (api).
- **Inc 2** — `TeamDensityField` / `ArenaDensity` (per-freq, active-bin sourced); `teamDensity(freq)` + `activeTeamFreqs()`; enemy/ally composed by the brain (relativity in the consumer).
- **Inc 3** — `ThreatField` / `ArenaThreat` (per-freq, weapon-range falloff, **LoS-gated** for cover); `OpportunityField` (per-arena, prize-sourced via EntitySet, bounds-assigned). `threat(freq)` + `opportunity()`.
- **Inc 4** — `CombatDensityField` (fire-origin splat + time-decay, new-shot sourced); `ChokepointAnalyzer` (geometric width-narrow detector). **Chokepoint = narrow space × traffic heatmap** (per user): `chokepoints()` re-ranks geometric candidates by `pinch × (1 + combat + k·totalDensity)`; top-N pinned as static nav goals.
- 43 tests; knobs in `zone-bot-ai.groovy`; `refreshDynamicFields` rebuilds all per density cadence from one active-bin pass + prize/projectile sets.

### Inc 5 landed (2026-05-25) — behavioural consumer + trench wiring

Smoke-accepted in trench. The substrate now drives a real behaviour end-to-end:

- `SteerToGoalTile` coordinate frame fixed (world→arena-relative, mirrors `SteerApproachTarget`) + blended gradient `nav + navThreatWeight·threatDescent + navOpportunityWeight·oppAscent` (`navThreatWeight` / `navOpportunityWeight` knobs in `zone-bot-ai.groovy`).
- `FollowTrafficBehaviour` (+ test): hot tile = `(combat + enemyDensity)·distDecay + chokepointBonus` → `NavigateToTile`; `follow-traffic` `synergy { }` entry in `engine-bot-ai.groovy` (capability axis — `0.3·mobility + xRadar bonus`, no hard gate); `Blackboard.ownFreq`.
- **Trench test bed:** `zone.groovy` autoLoads trench; arena runs `two-fixed-teams` + the map's stationary turf flag as a static goal that concentrates traffic.
- **`fill-up-x-teams` made team-setup-agnostic** to spawn bots in the bounded trench arena (it was FFA-only). Now delegates freq/team assignment to the `TeamSetupModule` (claims bots, not just players) and fills toward `fixedTeamCount × capacity` (bounded) or a flat bot count (FFA). New `capacity` knob; `TeamSetupModule.fixedTeamCount()`. Fixed a respawn-storm churn on stuck bots (see code-todos). Tests: `FillUpXTeamsTest` (10), `TwoFixedTeamsTeamSetupTest` / `FfaPrivateFreqsTeamSetupTest` bot-claim cases.

Slice deliverable complete + smoke-accepted → **done**. Two follow-ups spun out (not blocking this slice):
- Manual in-game verify the `EntityId` counter no longer climbs with 8 bots — tracked in code-todos-backlog "Suspected entity churn / leak" (`[~]`).
- Extract an `ArenaSpatialFields` manager from `BotBrainSystem` (the gather/refresh/arenaNav plumbing pushed its class-total cyclomatic complexity to 118) — wants its own issue.
