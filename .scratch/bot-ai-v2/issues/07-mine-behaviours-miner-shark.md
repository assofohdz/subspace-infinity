# Mine-placement behaviours + full `MinerShark` archetype

Status: needs-info
Category: enhancement
Type: HITL

> **PENDING archetype-design workstream.** This issue invents the marquee
> `MinerShark` archetype as a side-effect of implementing mine-placement
> behaviours. Per the 2026-05-23 step-back, archetypes want deliberate
> design (which roster ships, what behaviours each composes, per-archetype
> tuning). MinerShark is the chat-thread's flagship scenario; warrants its
> own design pass before being side-effected into existence. When the
> archetype-design workstream lands, re-scope this issue: either confirm
> the MinerShark composition (move to `needs-triage`), or split into
> "mine behaviours only" + a separate "MinerShark composition" issue.
> See [PRD §"Archetype-design step-back"](../PRD.md#archetype-design-step-back).

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 7 of 9. The marquee Shark-miner archetype from the chat-thread design.

## What to build

Two new behaviours that compose the Shark-miner experience:

- `mine-congestion-points` — combines `TrafficHeatmap` × `ChokepointAnalyzer`
  to score tiles by traffic-weighted choke-strength; navigates to top tile
  and drops a mine.
- `mine-in-front-of-enemies` — uses `ArenaCongestionField` cluster centroids +
  per-cluster average-velocity prediction to find the tile a cluster will pass
  through; navigates there + drops a mine ahead of arrival.

New `MinerShark` archetype blends both at high weight with engage/evade backups.

Demo: Shark bot in an arena with players actively moving — mines start appearing
at intersections and ahead of player paths. Visible behaviour difference vs
Brawler Shark (which just shoots).

## Acceptance criteria

- [ ] New behaviour `mine-congestion-points`: enumerates top-N tiles by `TrafficHeatmap.heatAt × ChokepointAnalyzer.scoreFor`; filter by distance-from-existing-mines (proxy via tile-occupied check until `MinePlacementScorer` lands per ADR-0012 deferral); emits `DenyChokepoint(TileId tile, double deadlineSeconds)` goal
- [ ] New behaviour `mine-in-front-of-enemies`: enumerates `ArenaCongestionField` clusters; per cluster, extrapolate centroid + velocity to predicted-tile-N-seconds-ahead; emit `AmbushPath(List<TileId> predictedPath, double validUntilSeconds)` goal
- [ ] New goal records `DenyChokepoint(TileId, double)` + `AmbushPath(List<TileId>, double)`
- [ ] New BT executor sequences:
  - `ExecuteMineDeploy` — NavigateToTile (FollowPath) → ArriveAt → fire MINE → goal complete
  - `ExecuteAmbush` — pick first predicted tile from path → NavigateToTile → ArriveAt → fire MINE → goal complete
- [ ] New `MinerShark` archetype declared in `bot-tuning.groovy`:
  ```groovy
  archetype 'MinerShark', {
      behaviour 'mine-congestion-points',   weight: 1.0
      behaviour 'mine-in-front-of-enemies', weight: 0.8
      behaviour 'engage',                   weight: 0.3
      behaviour 'evade',                    weight: 0.7
      behaviour 'wander',                   weight: 0.1
  }
  ```
- [ ] Slice #01's `bots { }` block in KOTH (or test arena) used to spawn a `MinerShark`
- [ ] Mine-fire integration goes through canonical `WeaponsFiring` interface (no AI bypass per ADR-0009 §3)
- [ ] Unit tests: both behaviours enumerate + score; ExecuteMineDeploy + ExecuteAmbush sequences complete; goal expiry (path no longer valid) re-selects
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#06 — ArenaCongestionField + Leviathan splash](./06-arena-congestion-leviathan-splash.md) (cluster-prediction shape established)
- [#01 — `bots { }` block + multi-ship spawn](./01-bots-block-multi-ship-spawn.md) (need Shark spawn)

## Comments
