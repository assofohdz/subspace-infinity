# Dynamic scalar fields + `ChokepointAnalyzer` + `follow-traffic` behaviour

Status: needs-triage
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

- [ ] `api/infinity.ai.field.FieldBlend` + `Weighted` + `FieldClamp` composite operators
- [ ] Dynamic `ScalarField` impls in `infinity-server/.../ai/field/`:
  - [ ] `ThreatField` — Σ over enemies of (weapon-range falloff, wall-blocked)
  - [ ] `EnemyDensityField` / `AllyDensityField` — per-team position sums (renamed from the old `ArenaCongestionField`)
  - [ ] `CombatDensityField` — recent firing events smeared in space + time-decayed (replaces the old `TrafficHeatmap`)
  - [ ] `OpportunityField` — prize / ammo / ally-support value falloff
- [ ] Blended-gradient composition: `gradientFor(goal) − k·threatGradient + j·opportunityGradient` feeds one `SeekDirection`
- [ ] Update cadences read from `zone-bot-ai.groovy` (default ~330ms for density fields; per-event for combat/opportunity with a decay tick); fields ready immediately (accumulate from zero)
- [ ] `api/infinity.ai.field.TileScored` record + `ChokepointAnalyzer` as a **load-time tile-list producer** (`hottest(int topN)`), not a runtime service; width-narrow detection over the passable grid at arena load; output unions into `BotAiHostService.onArenaLoad` goal registration (#03); top-N (`N`) is a `zone-bot-ai.groovy` knob (default 5)
- [ ] `BotAiArenaContext` exposes `threat()`, `enemyDensity()`/`allyDensity()`, `combatDensity()`, `opportunity()`, `chokepoints()`
- [ ] `follow-traffic` behaviour: enumerates top CombatDensity/EnemyDensity tiles, scores by heat × distance-decay, emits `NavigateToTile`; ships its `synergy { }` entry in `engine-bot-ai.groovy`
- [ ] `CoverFinder` / `LineOfSightOracle` are **not** services — any LoS need is a one-off Bresenham raycast at the leaf
- [ ] Unit tests: each field's accumulation + decay; `FieldBlend` math; blended-gradient direction; chokepoint scoring + top-N ordering; `follow-traffic` enumeration
- [ ] Performance: per-tick dynamic update bounded by `O(liveShips + populatedTiles)`; benchmark 32 ships in 1024² within a single-digit-% of one tick budget
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#03 — Production flow-field navigation](./03-flow-field-navigation.md) (blended-gradient nav + chokepoint goal registration)
- [#04 — Capability-derivation pipeline](./04-capability-derivation.md) (`follow-traffic` synergy entry)
- [#05 — `TacticalPlanner` + baseline behaviours](./05-tactical-planner-baseline-behaviours.md) (behaviour enumeration host)

## Comments
