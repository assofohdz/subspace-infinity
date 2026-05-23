# ADR 0011 — Bot navigation: grid A* over the `.lvl` tile grid

**Status:** Proposed
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz

## Context

[ADR-0009](./0009-bot-ai-architecture.md) deferred pathfinding for v1: open Subspace arenas are 2D fields where reactive `AvoidObstacles` (corridor projection) + perception via mphys `BinIndex` covers the navigation surface adequately. The deferral was on the trigger condition "first closed-corridor arena, OR upcoming gametype that needs route-aware AI."

Both conditions are now active. The bot-AI v2 design (chat thread, 2026-05-23) describes tactical brains that must reach specific tiles — Shark-miner heading to a chokepoint, Leviathan setting up overcover near a high-traffic intersection, Javelin moving to a bounce-shot firing position. Reactive steering cannot resolve "go to tile (50, 80)" through a corridor maze; it will press against the first wall on the direct line and fail.

The map substrate is convenient: Subspace `.lvl` files are native **tile grids** (`map.getMax().x` × `map.getMax().z` cells, each tile is "wall / passable / door / over1 / etc."). Grid A* maps directly to this; no mesh decomposition needed. Larger-than-tile reasoning (rooms, corridors, chokepoints) is a separate concern handled by [ADR-0012](./0012-bot-spatial-analysis-services.md), not by the navigation layer.

`.scratch/adr-backlog.md` had a stub "Bot navigation / pathfinding" reserving this decision space; this ADR promotes that stub. The backlog entry is deleted in the same change per tracker hygiene.

### What this ADR does not settle

- **Tactical reasoning** (which tile is worth pathing to). Lives in [ADR-0013](./0013-bot-tactical-goal-layer.md) — tactical AI layer.
- **Spatial analysis** (chokepoint detection, congestion field). Lives in [ADR-0012](./0012-bot-spatial-analysis-services.md). Pathfinding answers "how to get there"; the spatial services answer "where is 'there'."
- **Dynamic-obstacle integration in the planner.** Ships are not in the navmesh; reactive `AvoidObstacles` handles local dodging. The planner re-plans on path-blocked events, not on every enemy move.
- **Cross-arena pathing.** Each arena is a separate navigation domain; no inter-arena route planning.
- **Wormholes / warp tiles.** Subspace teleporters translate ships to non-adjacent tiles; modelling them as `TeleportEdge` in the graph is real work (warp source/destination tracking, conditional activation, prize-warp randomness). v2.0 leaves wormholes out of the graph — a goal tile across a wormhole is treated as unreachable, and bots that walk into a wormhole-tile get teleported as a side-effect from the physics layer (existing behaviour). Promote when a v2.x arena's gameplay hinges on wormhole-aware AI.
- **Door-state invalidation of cached paths.** Doors flap; a cached path may become blocked mid-traverse. v2.0 detects this only via the existing "re-validate current waypoint each tick" cheap check (planner re-plans on the next bot tick after the door closes — bot may briefly bump the door). Full door-state event subscription is deferred until visible bot pathing through closing doors becomes a player-visible bug.

## Decision

**Bot pathfinding is clearance-aware grid A* over the `.lvl` tile grid, per-arena, computed asynchronously at arena-load and cached for the arena's lifetime. A new `infinity.ai.nav.*` api package defines `Path`, `PathPlanner`, and the `FollowPath` BT action. A server-side `NavMeshService` builds the grid graph from `.lvl` at arena-load and exposes the planner. Dynamic obstacles (ships, projectiles) are NOT in the navigation graph — local avoidance stays with `AvoidObstacles` in the steering layer. Re-planning is event-triggered, not per-tick. Paths post-process through line-of-sight smoothing before reaching the `FollowPath` action.**

### Representation: grid A* over passable tiles

The graph is the `.lvl` tile grid: each passable tile is a node; edges connect to the 4 (orthogonal) or 8 (orthogonal + diagonal) passable neighbours. Tile passability is read once at arena-load from the loaded `.lvl` (wall tiles = impassable; doors = conditionally passable based on door state). Heuristic = Manhattan (4-conn) or octile (8-conn).

Decision: **8-connected with octile heuristic** for v2.0. Reason: 4-connected paths look visually unnatural (only cardinal directions); 8-connected is the cheap upgrade that makes paths look like a competent player chose them. Octile heuristic is admissible and consistent for 8-connected grids.

### Clearance-aware traversal: ship-radius padding

Ships have non-zero collision radius (`EngineConfig.shipRadius` ≈ 0.5). A grid path that grazes a wall tile's edge would press the ship's body into the wall — `AvoidObstacles` would fight the path the whole way.

v2.0 mitigates with **wall-adjacency edge rejection**: during graph construction, mark each passable tile with its **clearance** (chebyshev distance to nearest wall tile, computed once via brushfire / BFS-from-walls). Reject an edge `(a, b)` when `min(clearance(a), clearance(b)) < shipRadiusInTiles`. For a 0.5-unit radius on a 1-unit tile grid, that means an edge requires both endpoints to be ≥ 1 tile away from any wall.

This costs one extra pre-compute pass at arena-load (O(N) brushfire) + an int per node. It's the simplest form of "clearance-based A*" from the path-planning literature ([Harabor & Botea, "Clearance-based Pathfinding"](https://harabor.net/data/papers/dthesis.pdf)); upgrade to per-edge clearance if narrow-corridor navigation needs it.

### Path smoothing: line-of-sight chain post-processing

Raw grid A* output is stair-stepped (`(5,5) → (6,6) → (7,6) → (8,7)`). The `FollowPath` action would steer ship-center toward each tile center — visible zigzag at every step.

Post-process the raw waypoint list by **Floyd-style line-of-sight chaining**: starting from waypoint `w[0]`, find the farthest `w[k]` such that the straight line from `w[0]` to `w[k]` doesn't cross any wall tile. Replace `w[0..k]` with `[w[0], w[k]]`; recurse from `w[k]`. Result: a sparse waypoint list (corners only) that the steering layer's `Arrive` follows smoothly.

This is the standard post-processing step in tile-grid A* implementations; cheap (O(N²) worst case but typically O(N log N)); produces visually clean paths.

### Per-arena lifecycle: `NavMeshService`

One `NavMeshService` instance owns the per-arena nav graphs. On arena-load (`ArenaModule.onArenaLoad` per [ADR-0008](./0008-arena-composition-and-modules.md)), the service **schedules async graph construction** on a worker thread; the per-arena entry is marked `BUILDING` until the brushfire + clearance + neighbour-graph pass completes. Bot planner requests against a `BUILDING` arena return `Path.empty()`; the brain falls back to wander until the next tick after the graph is ready. On arena-unload, the cache entry is released.

Async chosen over lazy-per-bot to avoid "first bot spawn pays the graph-build cost" latency spikes; the trade-off is graceful (bot wanders briefly during the build window, typically sub-second on commodity hardware for a 1024² map).

The service is **zone-global, not per-arena module** — it's a navigation utility consumed by the bot AI; arena modules don't own the navmesh, they just trigger its build via the load event. Lives in `infinity-server/src/main/java/infinity/ai/nav/NavMeshService.java`. Per the cross-cutting decision shared with [ADR-0012](./0012-bot-spatial-analysis-services.md) + [ADR-0013](./0013-bot-tactical-goal-layer.md), the service is consumed via `BotAiArenaContext.nav()` rather than directly — a single container holds nav + spatial + tactical per-arena state.

### Re-plan policy: event-triggered

Per-tick re-planning is the cost trap A* implementations fall into. Re-plan only when:

1. **Target moves more than `RE_PLAN_DISTANCE_TILES`** from where the current path was planned to (e.g. 3 tiles).
2. **Path becomes blocked** — door closes on a path waypoint, or a `LargeStatic` materializes on a waypoint. Detected by re-validating the current waypoint each tick (cheap; just a tile-passability lookup).
3. **Path completed** — bot reached the final waypoint; planner asked for a new goal.
4. **Sanity floor**: re-plan every `RE_PLAN_SANITY_TICKS` (e.g. 600 ticks = ~20s at 30Hz) regardless. Catches edge cases where the trigger conditions miss a real change.

Reactive avoidance does NOT trigger re-plan — that's local dodge, not strategic re-route. Bot re-acquires the current path waypoint after the obstacle clears.

### Dynamic obstacles: NOT in the navmesh

Ships, projectiles, and other moving bodies stay out of the navigation graph. The planner's view of the world is the static tile grid + current door states. Local avoidance of moving bodies stays with `AvoidObstacles` via PrioritySteering composition:

```
PrioritySteering(
  AvoidObstacles,        // wall + body avoidance, reactive
  FollowPath             // BT-driven path following
)
```

The trade-off: bots can occasionally chase paths into mob clusters (planner doesn't know the cluster is there) and need a tick of AvoidObstacles to swing around. This is fine for v2 — the alternative (RVO / velocity-obstacle treatment of dynamic bodies in the planner) is significantly more complex and only buys behaviour quality in already-rare scenarios.

### `Path` shape + `FollowPath` BT action

```java
// api/infinity.ai.nav
public record Path(List<TileId> waypoints, double totalCost) { … }

public interface PathPlanner {
  Path findPath(ArenaId arena, TileId from, TileId to);
}

// api/infinity.ai.brain
public final class FollowPath implements Action {
  // Reads bb.currentPath() + bb.currentWaypointIndex(); steers via Arrive(nextWaypoint);
  // advances waypoint on arrival; returns SUCCESS when path complete, RUNNING while traversing,
  // FAILURE if path is blocked + planner returned null on re-plan.
}
```

The `Arrive` steering primitive ships with this ADR's implementation slice — it's a Reynolds primitive (`Pursue` with a deceleration radius around the target) that we should have had since slice #03 but the v1 pursue-the-player use case didn't need.

### Layering

- **api/infinity.ai.nav.*** — `TileId`, `Path`, `PathPlanner` interface, `FollowPath` BT action, `Arrive` steering. Pure data + interfaces per [ADR-0005](./0005-layered-architecture.md).
- **infinity-server/.../ai/nav/NavMeshService** — graph build + A* impl + cache.
- **No client dependency** — pathfinding is server-only authoritative concern. Client visualization (debug HUD showing the current path) is a v2.x affordance, not core.

## Consequences

### Positive

- **Bots reach remote tiles.** The "go to chokepoint at (50, 80)" task ADR-0013 needs becomes expressible.
- **Map-aware behaviour.** Brains can reason about geometry (rooms, corridors) by querying the planner for path-existence + path-length, even when not executing a path.
- **Steering layer unchanged.** Existing `AvoidObstacles`, `Pursue`, `OrbitTarget`, etc., compose with `FollowPath` via the existing `PrioritySteering`. No refactor of the steering substrate.
- **Subspace map fidelity.** Grid A* matches the map data model exactly — no decomposition fidelity loss.

### Costs

- **Per-arena memory:** grid graph is `width × height × neighbour_count` references plus a clearance int per node. Order-of-magnitude estimate for a 1024² arena with 30% passable tiles + 8-connected adjacency lands in the tens of MB; the actual number must be benchmarked against a real `.lvl` before committing v2.0 production budgets. **This is illustrative, not a budget.** If real-world measurement exceeds available headroom, HPA* or per-region graph caching are the documented optimization escapes.
- **Arena-load CPU spike (mitigated by async).** Initial graph build is `O(N)` brushfire + `O(N × neighbours)` graph construction. Async on a worker thread keeps the sim tick responsive; the bot AI gracefully degrades to wander until the build completes. Sub-second on commodity hardware for typical Subspace maps.
- **A* worst-case query cost:** an unreachable target forces the algorithm to expand the full reachable set before returning `null`. Mitigate with `closedSet.size() > MAX_NODES` early-out (treat as "no path") and let the brain fall back to wander.

### Neutral

- **No anytime/incremental planner needed for v2.** Re-plans are rare (event-triggered); per-query A* on a cached grid is fast enough. Promote to D* Lite if measurements show otherwise.
- **Diagonal-corner-clipping caveat.** 8-connected grids by default allow diagonal moves through diagonally-adjacent walls. The implementation must reject diagonal moves when both orthogonal neighbours are walls (standard fix; one boolean check per move). Test this explicitly.

## Alternatives considered

### A. Polygonal navmesh (mesh-from-tile-grid)

**Why considered:** Industry standard; widely used in Unity/Unreal. More expressive for non-grid geometry.
**Why rejected:** Subspace maps are tile grids natively. Mesh decomposition adds build complexity + lossy fidelity (mesh edges don't match tile boundaries exactly) for zero expressive gain. Promote when the first non-tile map appears, which for Subspace is "never" by the map format.

### B. Reactive steering only (no pathfinding)

**Why considered:** Already shipped in v1; no new layer.
**Why rejected:** The whole reason this ADR exists. v2 tactical brains have remote goals; reactive can't reach them through walls. The chat thread literally enumerated three bot use cases that all fail under reactive-only.

### C. Hierarchical A* (HPA*) from day one

**Why considered:** Faster than grid A* on large maps.
**Why rejected:** Premature optimization. Grid A* on a 1024×1024 cached graph completes in microseconds for typical paths. HPA* adds significant build + maintenance complexity (cluster graphs, abstract paths, intra-cluster refinement). Promote when measurements show grid A* is the bottleneck.

### D. Dynamic obstacles (ships) in the planner

**Why considered:** "Smarter" — planner avoids known threats.
**Why rejected:** Re-plan storm. Every ship move would invalidate every path. RVO / VO algorithms are the right shape for that problem but they're a different architecture entirely (continuous spatial reasoning, not graph search). Stay with "static planner + reactive local avoidance" — the standard FPS/RTS pattern.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Representation | 8-connected clearance-aware grid A* over `.lvl` passable tiles; octile heuristic |
| Clearance | Wall-adjacency edge rejection via brushfire-computed per-tile clearance (Harabor-style) |
| Path smoothing | Floyd-style line-of-sight chain post-process: raw waypoints → sparse corner-only list |
| Lifecycle | Per-arena cache in zone-global `NavMeshService`; **async** build at arena-load, evict at unload; `BUILDING` arenas return `Path.empty()` (bot wanders briefly) |
| Re-plan policy | Event-triggered (target moved, waypoint blocked, path complete) + sanity floor every ~20s |
| Dynamic obstacles | NOT in navmesh; `AvoidObstacles` handles them via `PrioritySteering(AvoidObstacles, FollowPath)` |
| Path output | `Path(List<TileId>, totalCost)`; `FollowPath` BT action drives `Arrive(nextWaypoint)` |
| Layer | `api/infinity.ai.nav.*` interfaces + `infinity-server/.../ai/nav/NavMeshService` impl; consumed via per-arena `BotAiArenaContext.nav()` |
| Diagonal corner-clip | Reject diagonal moves when both orthogonal neighbours are walls |
| Wormholes / warps | Deferred — out of graph in v2.0 |
| Door-state events | Deferred — re-validate current waypoint each tick only, no event subscription |

## Open work

- **Implementation pairing with the first v2 slice** that needs pathfinding. ADR-0013 (tactical goals) consumes the planner via "navigate to chokepoint"; that's the natural seam to land this ADR's impl.
- **Path visualisation in `BotDebugHudState`** — render current waypoint list for live debugging. Cheap (client reads a wire-crossing `BotPath` component); high-value for AI authoring.
- **Door integration.** v2 must invalidate paths when `Door.state` changes. `DoorSystem` already exists; add a `PathInvalidationListener` that the planner subscribes to.
- **Per-tile cost overrides** — let arenas mark "high-traffic" or "preferred lane" tiles as cheaper / more-expensive. Out of scope for ADR-0011 itself; the `Path.totalCost` field reserves the room.
- **Promote `Arrive` steering primitive into the steering library** — should have shipped in slice #03; lands as part of this ADR's impl slice.

## References

### Internal

- [ADR-0008](./0008-arena-composition-and-modules.md) — Arena composition; `onArenaLoad` is the trigger for graph build.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI architecture; this ADR is the deferred-navigation follow-up named in §"What this ADR does not settle".
- [ADR-0012](./0012-bot-spatial-analysis-services.md) — Spatial analysis services; consumes the navmesh for path-length queries.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — Tactical-goal layer; the primary consumer of `FollowPath`.
- [`world-coordinates.md`](../../.claude/rules/world-coordinates.md) — `TileId` API; the canonical map-coordinate shape this ADR builds on.

### External

- **Amit Patel, "Introduction to A*"** (https://www.redblobgames.com/pathfinding/a-star/introduction.html) — the canonical pedagogical reference. Octile heuristic + corner-clip details.
- **A. Botea, M. Müller, J. Schaeffer, "Near-Optimal Hierarchical Path-Finding"** (2004) — HPA*. Reference for the deferred optimization path.
- **Steve Rabin, *AI Game Programming Wisdom* vol. 1–4** — chapters on A* tuning + grid pathfinding.
- **Mat Buckland, *Programming Game AI by Example*** (2005) — chapter on path-following + steering composition.
