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
| `TrafficHeatmap` | Dynamic (tick) | Per-tile decaying scalar of recent ship presence | Shark miner ("find busy lanes"), behaviours scoring "place mine on this lane" |
| `ChokepointAnalyzer` | Static (load) | Width-narrow tile detection (per below): tiles with low clearance + high through-flow | Shark miner ("mine the choke"), Javelin ("ambush past the choke") |
| `ArenaCongestionField` | Dynamic (tick) | **Arena-wide** density field of all observed live ships (player + bot). Replaces per-bot perception aggregation. | Leviathan ("splash the cluster"), evade decisions ("avoid the deathball"), miner ("mine where they cluster") |

**Explicitly deferred** (don't build until first consumer needs them):

- `BounceTracer` — geometric simulation for bouncing-projectile aim. Adds when Javelin bouncer behaviour lands.
- `CoverFinder` — "where can I sit with sight blocked from threat direction, sight open to my fire line?" Adds when Leviathan setup behaviour lands.
- `MinePlacementScorer` — utility scalar per candidate tile combining heatmap × choke-score × distance-from-existing-mines. Adds when Shark miner lands.
- `LineOfSightOracle` — "does ship A have LoS to ship B?" — adds when stealth / cover behaviour needs it.

The deferral principle is uniform: don't build a service without a consumer. The v2.0 set above is the minimal substrate; weapon-specific services land alongside the first archetype that needs each.

### `ArenaCongestionField` design note (per grill response)

Original draft had `EnemyDensityField` rebuilt from *per-bot perception*, which made it not really a service (it would be per-bot scratch). Replaced with **arena-wide knowledge**: the field is computed once per tick from all live ships in the arena (omniscient), not from any individual bot's perception. Read as "every player can see where the congestion is" — a deliberate simplification that treats bots as having human-level map awareness.

This trade-off:
- **Pro:** the field is genuinely shared (one compute, many readers); simple semantics; matches how a human player thinks about traffic on the map.
- **Pro:** dodges the privacy-leak / scope ambiguity of per-bot rebuilds.
- **Con:** bots "know" cluster locations even when their personal perception radius wouldn't normally see them. v2.0 accepts this; v2.x can add a per-bot perception-mask filter as a query parameter if it matters.

### Update modes: static vs dynamic

**Static services** compute once at arena-load (async per [ADR-0011](./0011-bot-navigation-navmesh.md)) and never update for the arena's lifetime in v2.0.

- `ChokepointAnalyzer` — algorithm: **width-narrow tile detection on the passable graph**, paired with the per-tile clearance values already computed by `NavMeshService` (see ADR-0011 brushfire pre-compute). A tile is a chokepoint candidate if `clearance(tile) ≤ CHOKE_WIDTH_TILES` (e.g. ≤ 2 tile-widths of room around it). Filter further by "through-flow" — only tiles whose removal would disconnect non-trivial regions (cheap proxy: tiles where both side directions are walls, i.e. corridor segments). Rank by `1.0 / clearance × log(connectedRegionSize)`. Single-pass over the navmesh; produces a `List<TileScored>` ranked by choke strength.

  Algorithm choice (per grill response): width-narrow detection chosen over articulation-point / min-cut for v2.0 because (a) it directly matches Subspace's tile-corridor map idiom, (b) reuses `NavMeshService`'s clearance pre-compute → minimal new cost, (c) produces a continuous "chokepoint strength" score per tile (not a binary articulation/non-articulation), which is exactly what utility scorers want. Promote to articulation-graph-based scoring if more sophisticated "chokepoint" semantics need to materialize.

**Note on door + warp topology changes (deferred per ADR-0011 §"What this ADR does not settle"):** the static chokepoint set in v2.0 is computed from the static `.lvl` passability; door state changes do not invalidate it. Wormholes are not nodes in the analysis. Same deferral envelope as the navmesh itself.

**Dynamic services** update each tick (or every N ticks for cost-tuned services). The update cost must be bounded by arena size + ship count.
- `TrafficHeatmap` — per-tick, iterates all live ships in arena, increments the per-tile counter for the tile each ship occupies, decays all counters by a small factor (e.g. ×0.99). Bounded: O(liveShips) increments + O(populatedTiles) decay. Decay-only update can be skipped most ticks; full update every ~10 ticks (≈300ms at 30Hz).
- `ArenaCongestionField` — per-tick, single pass over live ships in the arena (PlayerShip + BotShip both counted). Cluster detection via simple density-based scan (e.g. DBSCAN-lite over ship tiles with a fixed eps). Bounded: O(liveShips²) worst case; in practice small (Subspace arenas rarely exceed 64 simultaneous ships).

### Per-arena lifecycle: `BotAiArenaContext` (consolidated)

Per the cross-cutting decision shared with [ADR-0011](./0011-bot-navigation-navmesh.md) + [ADR-0013](./0013-bot-tactical-goal-layer.md), the three v2 ADRs **consolidate their per-arena host state into a single `BotAiArenaContext`**. One zone-global `BotAiHostService` (extending `BaseInfinitySystem`) owns one `BotAiArenaContext` per loaded arena:

```java
public class BotAiHostService extends BaseInfinitySystem {
  private final Map<ArenaId, BotAiArenaContext> byArena = new ConcurrentHashMap<>();

  public BotAiArenaContext forArena(ArenaId arena) { return byArena.get(arena); }
  // onArenaLoad / onArenaUnload create / dispose BotAiArenaContext bundles.
}

public final class BotAiArenaContext {
  public NavMeshService.ArenaNav nav() { … }       // ADR-0011
  public ChokepointAnalyzer chokepoints() { … }    // this ADR
  public TrafficHeatmap traffic() { … }
  public ArenaCongestionField congestion() { … }
  // Deferred services land here: bouncer(), cover(), minePlacement(), lineOfSight().
}
```

`BotAiArenaContext` is the single per-arena bundle of bot AI state — navigation, spatial analysis, tactical planner host (per ADR-0013). Brains hold a reference once at addObject time (via `Blackboard.arenaContext()`), call methods per tick.

**Load order — graceful degradation:** the bundle's components have varying readiness:
- Navmesh build is async (per ADR-0011) → `nav()` returns `Path.empty()` while `BUILDING`.
- Static spatial services (chokepoints) build as part of the same async pass, depending on the navmesh's clearance data → return empty result sets while building.
- Dynamic services (traffic, congestion) are ready immediately (they just accumulate from zero).

When a bot ticks before the full bundle is ready, its scorers receive empty data → the goal scoring naturally falls back to whichever behaviour scores positive on incomplete data (typically Wander). **No crash; the bot gets info next tick.** This matches the user's "should not crash, simply gets info next tick" decision in the grill.

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
| Where do services live? | `api/infinity.ai.spatial.*` interfaces + `infinity-server/.../ai/spatial/*` impls; **consolidated per-arena `BotAiArenaContext`** bundle (cross-cutting with ADR-0011 / ADR-0013) owned by zone-global `BotAiHostService` |
| What's the v2.0 service set? | TrafficHeatmap (dynamic), ChokepointAnalyzer (static, width-narrow + clearance-based), ArenaCongestionField (dynamic, arena-wide knowledge) |
| Update modes | Static (arena-load async, O(1) query); dynamic (tick-cadence, throttled) |
| `ChokepointAnalyzer` algorithm | Width-narrow tile detection using `NavMeshService` clearance data; rank by `1/clearance × log(regionSize)` |
| `ArenaCongestionField` scope | Arena-wide knowledge (all live ships counted); not per-bot perception. "Every player knows congestion points." |
| Per-arena vs zone-global? | Per-arena bundle; zone-global host. Single `BotAiHostService` shared with ADR-0011 / ADR-0013. |
| Query API shape | Data records out, no BT semantics. Translation to BT-blackboard state is the planner's job. |
| Knowledge injection into bot | `BotBrainSystem.BrainContainer.addObject` injects `BotAiArenaContext` reference into `Blackboard` |
| Load-order safety | Empty results during async build window; bot wanders; no crash |
| Deferred services | BounceTracer, CoverFinder, MinePlacementScorer, LineOfSightOracle — add when first consumer behaviour lands. |
| Door / wormhole topology changes | Deferred per ADR-0011's matching deferral; v2.0 chokepoints frozen at arena-load |

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
