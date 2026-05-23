# ADR 0012 — Bot spatial-analysis services

**Status:** Proposed
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz

## Context

Bot v2 tactical brains need to answer spatial questions that the per-tick steering + perception substrate can't express:

- **Shark miner** — "where is recent traffic densest?" (so I can deny the lane with mines), "is this tile a chokepoint?" (so the mine matters), "is there already a mine here?" (don't double-stack).
- **Javelin bouncer** — "if I fire from here with N bounces, does the projectile reach the target around that corner?"
- **Leviathan setup** — "where is the densest cluster of enemies in my splash radius?", "what cover position has line-of-sight to that cluster but is shielded from their return fire?"
- **Any combatant** — "what's the shortest-path distance to that tile?" (consumed during goal scoring), "is my current path-tile still passable?" (door closed mid-traverse).

These are **stateful, computationally non-trivial, and shared across multiple bots in an arena**. Each bot recomputing them per tick would burn CPU on identical data; embedding them inside BT leaves means each leaf becomes its own mini-system. The right shape is **dedicated per-arena services with their own update cadence**, queried by the bot brains.

Pattern reference: this is the *influence map* family from RTS / FPS AI literature (Tozour in *AI Game Programming Wisdom*; Mark Brockington; Bourg & Seemann). Influence maps were standard in Age of Empires II, Killzone 2, and every Halo combat encounter. Same shape applies here.

ADR-0011 (navigation) handles the "how do I get there" half of spatial reasoning. This ADR handles the "where is 'there' worth going" half.

### What this ADR does not settle

- **Tactical decisions made from the query results.** That's [ADR-0013](./0013-bot-tactical-goal-layer.md) — the tactical-goal layer evaluates queries and picks goals.
- **Pathfinding.** [ADR-0011](./0011-bot-navigation-navmesh.md). Spatial services may consume `PathPlanner` (e.g. "is this tile reachable from this other tile"); the planner does not depend on spatial services.
- **Client visualization of spatial data.** A v2.x affordance — surface a debug overlay showing heatmaps, chokepoints, etc. Useful for AI authoring but not a primary requirement.
- **Per-archetype "what services does my brain need"** scoping. Services are arena-scoped; consumers opt in by querying. Adding a service doesn't enable it for every bot.

## Decision

**Bot spatial-analysis lives in `infinity.ai.spatial.*` (api interfaces) + `infinity-server/.../ai/spatial/` (impls). Each service is per-arena, owned by a zone-global host system that creates/destroys instances on arena load/unload. Services have one of two update modes: static (compute-at-load, query-as-O(1)-lookup) or dynamic (tick-cadence, throttled). Brains query services via typed methods; the planner translates results into BT-blackboard state.**

### Service inventory (v2.0 minimal first cut)

| Service | Mode | Purpose | Primary consumer |
|---|---|---|---|
| `TrafficHeatmap` | Dynamic (tick) | Per-tile decaying scalar of recent ship presence | Shark miner ("find busy lanes"), Leviathan ("find target clusters") |
| `ChokepointAnalyzer` | Static (load) | Graph analysis of the tile grid: narrow passages, junctions, dead-ends | Shark miner ("mine the choke"), Javelin ("ambush past the choke") |
| `EnemyDensityField` | Dynamic (tick) | Live cluster detection from perception | Leviathan ("splash the cluster"), evade decisions ("avoid the deathball") |
| `BounceTracer` | Query-on-demand | Geometric simulation: "if I fire from X heading H with N bounces, where does the projectile land?" | Javelin bouncer; bomb-trajectory predictors |

**Explicitly deferred** (don't build until first consumer needs them):

- `CoverFinder` — "where can I sit with sight blocked from threat direction, sight open to my fire line?" Adds when Leviathan setup behaviour lands.
- `MinePlacementScorer` — utility scalar per candidate tile combining heatmap × choke-score × distance-from-existing-mines. Adds when Shark miner lands.
- `LineOfSightOracle` — "does ship A have LoS to ship B?" — adds when stealth / cover behaviour needs it.

The deferral principle is the same as everywhere in this codebase: don't build a service without a consumer. The four services above are the ones bot v2 needs immediately; the others wait for their behaviours.

### Update modes: static vs dynamic

**Static services** compute once at arena-load and never update. Queries are O(1) lookups against a pre-built data structure.
- `ChokepointAnalyzer` — tile graph analysis from the `.lvl` data.

Static services are zero per-tick cost. Their work is amortized at arena-enter.

**Dynamic services** update each tick (or every N ticks for cost-tuned services). The update cost must be bounded by arena size + bot count.
- `TrafficHeatmap` — per-tick, iterates all active ships in arena, increments the per-tile counter for the tile each ship occupies, decays all counters by a small factor (e.g. ×0.99). Bounded: O(activeShips) increments + O(populatedTiles) decay. Decay-only update can be skipped most ticks; full update every ~10 ticks (≈300ms at 30Hz).
- `EnemyDensityField` — per-tick, rebuilt from perception snapshots. Cluster centroid + radius via simple density-based scan. Bounded: O(perceivedShips²) for clustering; capped at small perception radii.

**Query-on-demand services** are stateless given the world state — no cached precompute, called when needed.
- `BounceTracer` — pure geometric simulation; one invocation = one trace. Bot calls this only when in the "should I fire a bounce shot" branch.

### Per-arena lifecycle: `SpatialAiHostService`

A single zone-global `SpatialAiHostService` (extending `BaseInfinitySystem`) owns the per-arena service instances. On arena-load: instantiate all services for the arena, register with the spatial host. On arena-unload: dispose all services for the arena. Same lifecycle pattern as `NavMeshService` from ADR-0011.

```java
public class SpatialAiHostService extends BaseInfinitySystem {
  private final Map<ArenaId, ArenaSpatial> byArena = new ConcurrentHashMap<>();

  public ArenaSpatial forArena(ArenaId arena) { return byArena.get(arena); }
  // onArenaLoad / onArenaUnload create / dispose ArenaSpatial bundles.
}

public final class ArenaSpatial {
  public TrafficHeatmap traffic() { … }
  public ChokepointAnalyzer chokepoints() { … }
  public EnemyDensityField enemies() { … }
  public BounceTracer bouncer() { … }
}
```

`ArenaSpatial` is the per-arena query bundle; brains hold a reference once at addObject time (via `Blackboard.spatial()`), call methods per tick.

### Query API shape

Services return **data**, not BT semantics. Translation from query result to BT-blackboard state happens in the brain's tactical planner (ADR-0013), not inside the service. Example:

```java
public interface TrafficHeatmap {
  /** Top-N tiles by heat. */
  List<TileScored> hottest(int topN);
  /** Heat at a specific tile (0..1 normalized). */
  double heatAt(TileId tile);
}

public record TileScored(TileId tile, double score) { … }
```

This keeps services testable in isolation (no brain dependency) and reusable across brain archetypes. A future debug HUD can subscribe to the same query API to visualise heatmaps without going through any BT.

### Layering

- **api/infinity.ai.spatial.*** — service interfaces + return-type records (`TileScored`, `Cluster`, `BounceTrace`, etc.). Pure data + interfaces per [ADR-0005](./0005-layered-architecture.md).
- **infinity-server/.../ai/spatial/*** — service impls + `ArenaSpatial` bundle + `SpatialAiHostService`.
- **No client dependency.** Spatial state is authoritative server data. Client visualization (v2.x) reads via ECS wire-crossing components stamped by a per-arena debug system, not by direct service access.

## Consequences

### Positive

- **Brains express tactical questions naturally.** "Find hottest tile within 30 of me" becomes one line; without the service it's tens of lines of per-bot grid walking.
- **Shared cost across bots.** Heatmap + chokepoint analysis cost is amortized — 10 bots in the same arena pay the cost once, not 10×.
- **Data + interfaces only on api side.** Bots are testable against `FakeTrafficHeatmap` etc. in unit tests; spatial impl tests are independent of any brain.
- **Composable with the navmesh.** Tactical layer can ask "shortest path to hottest tile within 50 units" by combining queries from both ADRs. Each ADR's primitive stays narrow.

### Costs

- **Per-arena memory.** Static services scale with map size (chokepoint analysis ≈ tens of KB for a 1024² arena). Dynamic services scale with map size + bot count. Total budget should stay well under 100 MB per arena for normal Subspace maps; document in the v2 PRD.
- **Tick-cadence dynamic services add per-tick CPU.** Bounded but non-zero. Measure once v2 lands; if any service exceeds budget, throttle (every Nth tick) or downsample (per-cell granularity coarser than per-tile).
- **Cache invalidation on door / wall changes.** `ChokepointAnalyzer` is "static" only in the open-world sense — door-state changes can alter chokepoint topology. v2.0 ignores this (chokepoints recomputed only at arena-load); v2.x adds invalidation listeners if door-driven topology matters.

### Neutral

- **Service granularity is a per-service judgment.** "Should `MinePlacementScorer` be its own service, or a method on `TrafficHeatmap`?" — start coarse (compose at the consumer), split when a second consumer wants the same query. The catalog above is the minimal first cut; expect ~6-10 services by v2 stability.
- **Debug visualization is high-value but out of scope.** Reading heatmap → ECS wire-crossing → client HUD is straightforward but not core. Reserve a `BotSpatialDebug` component for v2.x.

## Alternatives considered

### A. Inline computation in BT leaves

**Why considered:** No new layer; each leaf does what it needs.
**Why rejected:** Catastrophic redundant computation. 8 Shark miners in the same arena each rebuilding a traffic heatmap = 8× the work. Also untestable in isolation — leaf tests would need a whole arena.

### B. One monolithic `SpatialAI` service

**Why considered:** One thing to instantiate per arena.
**Why rejected:** Same anti-pattern as splitting `infinity.systems.*` into focused systems. Each spatial service has its own update cadence (static vs dynamic), its own data shape, its own consumers. Bundling them makes the update loop tangled + harder to test.

### C. Client-side computation (visualization-only)

**Why considered:** Client renders heatmaps from observable state for debug.
**Why rejected for the primary path:** Server owns gameplay state per [ADR-0005](./0005-layered-architecture.md); spatial AI is a gameplay-relevant computation, not a presentation concern. Brain runs server-side; spatial services must be server-side. (Client visualization on top is a separate, additive concern.)

### D. Compute services eagerly for every potential consumer

**Why considered:** Have everything ready in case someone needs it.
**Why rejected:** Build only what current behaviours consume. The deferred services in the catalog above wait until their archetype lands; otherwise we pay ongoing CPU for unused state.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Where do services live? | `api/infinity.ai.spatial.*` interfaces + `infinity-server/.../ai/spatial/*` impls + per-arena `ArenaSpatial` bundle + zone-global `SpatialAiHostService` |
| What's the v2.0 service set? | TrafficHeatmap (dynamic), ChokepointAnalyzer (static), EnemyDensityField (dynamic), BounceTracer (query-on-demand) |
| Update modes | Static (arena-load, O(1) query); dynamic (tick-cadence, throttled); query-on-demand (no cache) |
| Per-arena vs zone-global? | Per-arena instances; zone-global host. Same shape as `NavMeshService` from ADR-0011. |
| Query API shape | Data records out, no BT semantics. Translation to BT-blackboard state is the planner's job. |
| Deferred services | CoverFinder, MinePlacementScorer, LineOfSightOracle — add when first consumer behaviour lands. |
| Cache invalidation | v2.0 ignores door-driven topology changes for `ChokepointAnalyzer`; revisit if it bites. |

## Open work

- **Implementation slicing in the v2 PRD.** Each service is one slice (small) or a half-slice if grouped with its first consumer. Recommended order: ChokepointAnalyzer + Shark-miner together (forces both to design coherently); TrafficHeatmap with Shark/Leviathan; EnemyDensityField with Leviathan; BounceTracer with Javelin.
- **Test infrastructure.** `FakeTrafficHeatmap` / `FakeChokepointAnalyzer` etc. in `api/src/test/java/infinity/ai/spatial/fake/` for brain tests. Real impls get their own unit tests at the service level.
- **Performance budget.** Document target update-cost per service in the v2 PRD. Profile after v2.0 lands; throttle the dynamic services if they exceed budget.
- **Debug visualization design.** Reserve `BotSpatialDebug` wire-crossing component shape; full implementation in v2.x.

## References

### Internal

- [ADR-0005](./0005-layered-architecture.md) — api purity. Service interfaces are api; impls are server.
- [ADR-0008](./0008-arena-composition-and-modules.md) — `onArenaLoad`/`onArenaUnload` lifecycle; spatial host hooks here.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI architecture; this ADR adds the spatial-reasoning layer the v1 architecture didn't have.
- [ADR-0011](./0011-bot-navigation-navmesh.md) — Navmesh; this ADR's services often consume the navmesh for reachability queries.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — Tactical-goal layer; primary consumer of the spatial query API.

### External

- **Paul Tozour, "Influence Mapping"** in *Game Programming Gems 2* (2001) — the canonical reference for tile-grid scalar fields driving AI decisions.
- **Damian Isla, "Halo 2 AI"** (GDC 2005) — encounter-level spatial reasoning; "tactical positions" service shape inspires the design here.
- **Naughty Dog, *The Last of Us* AI talks** (multiple GDCs) — cover-finder + tactical-position selection as a service consumed by behaviour layer.
- **David Mark, *Behavioral Mathematics for Game AI*** (2009) — utility scoring chapter pairs naturally with [ADR-0013](./0013-bot-tactical-goal-layer.md).
- **Mat Buckland, *Programming Game AI by Example*** (2005) — chapter on territorial/influence maps in RTS-style AI.
