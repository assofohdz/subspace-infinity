# `ChokepointAnalyzer` + `camp-chokepoint` behaviour

Status: needs-info
Category: enhancement
Type: HITL

> **PENDING archetype-design workstream.** This issue invents a `ChokeCamper`
> archetype as a side-effect of demoing the service + behaviour. Per the
> 2026-05-23 step-back, archetypes want deliberate design (which roster
> ships, what behaviours each composes, per-archetype tuning). When the
> archetype-design workstream lands, re-scope this issue: either confirm
> the `ChokeCamper` invention (move to `needs-triage`), or split into
> "service + behaviour only" (no archetype) + a separate later issue that
> consumes the behaviour from a designed archetype.
> See [PRD §"Archetype-design step-back"](../PRD.md#archetype-design-step-back).

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 4 of 9. Implements [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md) `ChokepointAnalyzer` + first chokepoint-using behaviour.

## What to build

First arena-static spatial service: `ChokepointAnalyzer` ranks tiles by
"how choke-y they are" using the per-tile clearance data already computed
by NavMeshService (ADR-0011 brushfire pre-compute). Algorithm: width-narrow
detection with corridor through-flow filter; rank by
`1/clearance × log(regionSize)`. New behaviour `camp-chokepoint` navigates
to the highest-scoring chokepoint within range and parks there.

Demo: in a corridor-style arena, the bot moves to a chokepoint and stays
there (light bullets, no mines yet — that's Slice #07). Visible behaviour
difference: bot doesn't wander aimlessly; it picks a tactically meaningful
spot.

## Acceptance criteria

- [ ] `api/infinity.ai.spatial.ChokepointAnalyzer` interface (`hottest(int topN)`, `scoreFor(TileId)`); record `TileScored(TileId, double)`
- [ ] `infinity-server/.../ai/spatial/ChokepointAnalyzerImpl` — width-narrow tile detection consuming NavMeshService clearance + flood-fill region sizes
- [ ] Bundled into `BotAiArenaContext.chokepoints()`; build runs alongside async navmesh build (ADR-0011 sequencing)
- [ ] New behaviour `camp-chokepoint`: enumerates top-N chokepoints; intrinsic score weighted by distance + choke-strength; emits `CampTile(TileId)` goal
- [ ] New goal record `CampTile(TileId tile, double deadlineSeconds)`
- [ ] New BT executor sequence `ExecuteCampTile`: NavigateToTile (FollowPath) → ArriveAt → hold position (zero thrust until threat appears)
- [ ] New `ChokeCamper` archetype declared in `bot-tuning.groovy`:
  ```groovy
  archetype 'ChokeCamper', {
      behaviour 'camp-chokepoint', weight: 1.0
      behaviour 'engage',          weight: 0.6
      behaviour 'evade',           weight: 0.8
      behaviour 'wander',          weight: 0.1
  }
  ```
- [ ] Slice #01's `bots { }` block in KOTH (or test arena) used to spawn one `ChokeCamper`
- [ ] Unit tests: ChokepointAnalyzer scoring; CampTile behaviour enumerates correctly; ExecuteCampTile sequence
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#03 — Real navmesh: clearance + smoothing + async](./03-real-navmesh-clearance-smoothing.md)

## Comments
