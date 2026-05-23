# `ArenaCongestionField` + Leviathan splash behaviour

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 6 of 9. Implements [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md) `ArenaCongestionField` + first Leviathan archetype.

## What to build

Second dynamic spatial service: `ArenaCongestionField` runs cluster detection
across all live ships in the arena per tick (omniscient, arena-wide knowledge
per the grill decision — "every player knows the congestion points"). New
behaviour `splash-cluster` finds the largest cluster within bomb range,
positions to fire, and fires L3 bombs. Creates the first weapon-aware
archetype: `LeviathanSetup`.

Demo: Leviathan bot finds a cluster of enemies and lands splash bombs on
them. Uses chokepoint scoring as cover-proxy until the dedicated
`CoverFinder` service lands (deferred to v2.x per ADR-0012).

## Acceptance criteria

- [ ] `api/infinity.ai.spatial.ArenaCongestionField` interface (`largestClusters(int topN)`); record `Cluster(Vec3d centroid, double radius, int shipCount)`
- [ ] `infinity-server/.../ai/spatial/ArenaCongestionFieldImpl` — per-tick density-based cluster detection (DBSCAN-lite or similar; capped at small `eps`); arena-wide scope (all live PlayerShip + BotShip counted)
- [ ] Bundled into `BotAiArenaContext.congestion()`; immediate readiness
- [ ] New behaviour `splash-cluster`: enumerates clusters within ship's bomb range; intrinsic score weighted by cluster size × proximity-to-firing-position; emits `SplashCluster(Vec3d centerPos, double radius, EntityId primaryTarget)` goal
- [ ] New goal record `SplashCluster(Vec3d centerPos, double radius, EntityId primaryTarget)`
- [ ] New BT executor sequence `ExecuteSplashCluster`: find cover tile near firing-position-of-cluster (use ChokepointAnalyzer as proxy → high-clearance tile within range) → NavigateToTile → ArriveAt → fire BOMB when target enters splash radius
- [ ] New `LeviathanSetup` archetype declared in `bot-tuning.groovy`:
  ```groovy
  archetype 'LeviathanSetup', {
      behaviour 'splash-cluster', weight: 1.0
      behaviour 'camp-chokepoint', weight: 0.5
      behaviour 'evade',          weight: 0.9
      behaviour 'engage',         weight: 0.3
  }
  ```
- [ ] Slice #01's `bots { }` block in KOTH (or test arena) used to spawn one Leviathan with `LeviathanSetup`
- [ ] Unit tests: ArenaCongestionField cluster detection; SplashCluster behaviour scoring; ExecuteSplashCluster sequence completes / handles cluster dispersal mid-action
- [ ] Performance: per-tick cluster pass bounded by O(liveShips²); benchmark stays cheap for typical arena populations
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#04 — ChokepointAnalyzer + camp-chokepoint](./04-chokepoint-analyzer-camp-behaviour.md) (cover-tile-proxy)
- [#05 — TrafficHeatmap + follow-traffic](./05-traffic-heatmap-follow-behaviour.md) (sibling pattern; cluster prediction uses similar tile-density shape)
- [#01 — `bots { }` block + multi-ship spawn](./01-bots-block-multi-ship-spawn.md) (need Leviathan spawn)

## Comments
