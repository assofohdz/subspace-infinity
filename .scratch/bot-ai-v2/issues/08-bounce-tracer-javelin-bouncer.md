# `BounceTracer` + Javelin bouncer archetype

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 8 of 9. Promotes `BounceTracer` out of [ADR-0012](../../../docs/adr/0012-bot-spatial-analysis-services.md)'s deferred list (first consumer arrived).

## What to build

Geometric query-on-demand service `BounceTracer` simulates bomb trajectories
with N bounces and reports where they land. New behaviour `bomb-bounce-shot`
finds firing positions where a bouncing bomb lands on the current threat
target — enabling Javelin-style hits around corners.

Demo: Javelin bot fires bombs that ricochet around corners to hit enemies
out of direct line of sight. Visible behaviour difference vs straight-shot
Brawler Javelin.

## Acceptance criteria

- [ ] `api/infinity.ai.spatial.BounceTracer` interface: `trace(origin, heading, maxBounces, range) → BounceTrace` record with hit position + bounce points
- [ ] `infinity-server/.../ai/spatial/BounceTracerImpl` — pure geometric simulation; reflects off `.lvl` wall tiles using the navmesh's passability data; one trace per invocation (no caching)
- [ ] Bundled into `BotAiArenaContext.bouncer()`
- [ ] New behaviour `bomb-bounce-shot`: enumerates candidate (firing-tile, heading, bounces) tuples within ship range; for each, BounceTracer.trace → score by `distance(traceEndpoint, targetPosition) < splashRadius`; emits `BounceShot(firingTile, heading, bounces, EntityId target)` goal
- [ ] New goal record `BounceShot(TileId firingTile, double heading, int bounces, EntityId target)`
- [ ] New BT executor sequence `ExecuteBounceShot`: NavigateToTile(firingTile) → ArriveAt → rotate to heading (uses existing Steering primitives via a new `FaceHeading` action) → fire BOMB → goal complete
- [ ] New `JavelinBouncer` archetype declared in `bot-tuning.groovy`:
  ```groovy
  archetype 'JavelinBouncer', {
      behaviour 'bomb-bounce-shot', weight: 1.0
      behaviour 'engage',           weight: 0.4
      behaviour 'evade',            weight: 0.8
      behaviour 'wander',           weight: 0.1
  }
  ```
- [ ] Slice #01's `bots { }` block in KOTH (or test arena) used to spawn a `JavelinBouncer`
- [ ] Bomb-fire integration goes through canonical `WeaponsFiring` interface
- [ ] Unit tests: BounceTracer geometry (single bounce, multi-bounce, no-hit); behaviour scoring; ExecuteBounceShot completes; goal expiry on target despawn
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#02 — BotAiArenaContext scaffold + tracer](./02-arena-context-scaffold-tracer.md) (no nav dependency; bouncer is geometric only)
- [#01 — `bots { }` block + multi-ship spawn](./01-bots-block-multi-ship-spawn.md) (need Javelin spawn — Javelin is the default v1 ship so technically this also works without #01, but the demo wants explicit Javelin choice)

## Comments
