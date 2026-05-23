# ADR 0011 — Bot navigation: flow fields per goal tile

**Status:** Proposed (revised 2026-05-23)
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz
**Revision note:** This ADR was originally drafted as "grid A* over the `.lvl` tile grid" with clearance-aware A* + Floyd LoS smoothing + `FollowPath` BT action. That design was reviewed against the actual game shape (2D top-down momentum physics, fast TTK, sparse obstacles, open arenas) and rejected as a genre mismatch — A* returns waypoints, but the bot can't follow waypoints because it has thrust + rotational inertia + drag + bounce. The locomotion problem is closer to a rocket-landing problem than an FPS pathfinding problem. This revision replaces the A*-based design with **flow fields** as the navigation primitive. A* now appears as Alternative A (rejected).

## Context

[ADR-0009](./0009-bot-ai-architecture.md) deferred pathfinding for v1: open Subspace arenas covered the navigation surface adequately with reactive `AvoidObstacles` + perception via mphys `BinIndex`. The deferral trigger was "first closed-corridor arena, OR upcoming gametype that needs route-aware AI."

Both conditions are now active. The bot-AI v2 design ([.scratch/bot-ai-v2/PRD.md](../../.scratch/bot-ai-v2/PRD.md)) describes tactical brains that must navigate toward specific tiles — Shark-miner heading to a chokepoint, Leviathan setting up near a high-traffic intersection, Javelin moving to a bounce-shot firing position. Reactive `AvoidObstacles` cannot resolve "get to tile (50, 80)" through a wall maze; it will press against the first wall on the direct line and fail.

The key constraint that shapes the decision: **Subspace ships have momentum**. Thrust changes velocity, not position; rotation has inertia; ships bounce off walls; drag is non-trivial. A path returned by A* is a sequence of waypoint tile centers — but the bot can't follow waypoints. By the time it "arrives" at waypoint 3, its velocity carries it past, and the local steering layer fights the waypoint sequence. This is a well-known mismatch in vehicle AI (Reynolds 1999 §"Path Following" addresses it explicitly): for momentum-physics agents, the right primitive is a *gradient* — "from this position, which direction makes progress?" — not a *waypoint sequence*.

The map substrate remains convenient: Subspace `.lvl` files are native tile grids; per-tile operations are cheap. The decision is what *shape of data* the navigation layer produces.

### What this ADR does not settle

- **Tactical reasoning** (which tile is worth heading to). [ADR-0013](./0013-bot-tactical-goal-layer.md). Navigation answers "how do I make progress toward T"; tactical answers "is T worth heading to."
- **Other spatial analysis** ([ADR-0012](./0012-bot-spatial-analysis-services.md)). Navigation is one specialization of the unified `ScalarField`/`GradientField` primitive that ADR-0012 defines; other specializations (threat field, opportunity field) follow the same shape.
- **Dynamic-obstacle integration.** Ships, projectiles, mines are not in the navigation field. Reactive `AvoidObstacles` in the steering layer handles them, same as before.
- **Wormhole / warp tiles.** Treated as just-another-passable-tile in the Dijkstra pass for now. A bot that walks into a warp gets teleported (existing physics behaviour); the field doesn't model the topology change explicitly. Promote when wormhole-aware AI becomes a v2.x archetype's need.
- **Cross-arena navigation.** Each arena is a separate field domain; no inter-arena routing.

## Decision

**Bot navigation is a flow field per goal tile: Dijkstra from the goal tile outward over the `.lvl` passable tile grid produces a distance field; the gradient of the distance field at any cell points toward the next-closer-to-goal neighbour. Bots sample the gradient at their current position to get a desired heading; the steering layer translates that heading into thrust + rotation commands subject to the ship's momentum. Flow fields are owned by `BotAiArenaContext` ([ADR-0012](./0012-bot-spatial-analysis-services.md)), built lazily on first request per goal tile, cached for the arena's lifetime, evicted on a TTL for transient goals (e.g. current-target-position).**

### Why flow fields fit momentum physics

A flow field gives the bot a **direction signal at every cell**, not a waypoint to chase. The bot reads `gradient(distanceField[G]).at(currentCell)` → unit vector pointing toward G's basin of attraction. That direction goes straight into the existing steering composition:

```
PrioritySteering(
  AvoidObstacles,                    // wall + body avoidance, reactive
  SeekDirection(flowField.gradient)  // thrust + rotate toward gradient
)
```

`SeekDirection` is a new Reynolds-style primitive (Pursue-shaped, with a direction instead of a target position) that emits `MovementInput.move = Vec3d(turnRate, 0, thrustRate)` toward the desired heading. It handles momentum naturally: when the bot's velocity already aligns with the gradient, thrustRate stays high; when the gradient flips (passing a corner), thrustRate dips while rotation catches up. No waypoint advancement; no overshoot; no Floyd-smoothing pass.

### Flow field shape

```java
// api/infinity.ai.field
public interface DistanceField extends ScalarField {
  TileId goal();
  /** Dijkstra-derived distance from goal to (x,y); Double.POSITIVE_INFINITY if unreachable. */
  @Override double valueAt(int x, int y);
}

public interface GradientField {
  /** Unit vector at (x,y) pointing toward lower scalar value. Zero vector at goal cell or unreachable cells. */
  Vec2d directionAt(int x, int y);
}

// Convenience: derive a GradientField from any ScalarField (e.g. DistanceField).
public final class FieldGradient implements GradientField {
  public FieldGradient(ScalarField source) { ... }
}
```

`ScalarField` is the base primitive from [ADR-0012](./0012-bot-spatial-analysis-services.md); `DistanceField` is one specialization (navigation), `ThreatField` / `OpportunityField` are others (perception). The same `FieldGradient` derives a gradient from any of them, so behaviour scorers can ask "thrust toward goal G but bias against the threat field" by blending two gradients with weights.

### Construction: per-goal Dijkstra, lazy + cached

The naive cost is "one Dijkstra per goal." For a 1024×1024 tile map at typical 30% passable, a single-source Dijkstra over the passable subset completes in 50-200ms on commodity hardware. That's offline cost paid once at goal-registration time, not per-tick.

```java
// BotAiArenaContext (per ADR-0012)
public interface NavigationFields {
  /** Returns the flow field for {@code goal}, building it asynchronously on first request. */
  DistanceField fieldFor(TileId goal);
  /** Convenience: the gradient of fieldFor(goal). */
  GradientField gradientFor(TileId goal);
  /** Drop a cached field (transient goals; e.g. predicted-target tile changes). */
  void evict(TileId goal);
}
```

Fields are categorized for cache management:

- **Static fields** — goal is a fixed map tile (flag spawn, KOTH center, named chokepoint). Built at arena load + on `.lvl` reload; persist for the arena's lifetime.
- **Transient fields** — goal is a dynamic tile (current target's predicted position, hottest-traffic tile). Built lazily on first request; evicted on TTL (default ~5s) or when the planner explicitly evicts.

The arena's typical static-goal set is ~5-10 tiles; transient goals turn over but only the currently-referenced ones stay resident. Memory budget for a 1024² map at 2 bytes per cell ≈ 2MB per field × 15 active fields ≈ 30MB per arena. Acceptable; downcountable by tile-supersampling (e.g. 4×4 superblocks → 16× reduction) if real measurements demand.

### Goal registration: `BotAiHostService` orchestrates

A single orchestrator wires the static-goal set at arena load:

```
BotAiHostService.onArenaLoad(arena):
  1. Build per-arena BotAiArenaContext
  2. Compute ChokepointAnalyzer tile list (ADR-0012)
  3. Collect arena's ArenaObjective.staticGoalTiles() (ADR-0015)
  4. For each tile in (chokepoint top-N ∪ objective tiles):
       context.navigation().fieldFor(tile)  // triggers async Dijkstra
  5. Mark context ready (planner can start scoring)
```

One code site to debug "why doesn't this field exist." A new mechanic module shipping a new gametype only needs to provide `ArenaObjective.staticGoalTiles()`; the orchestrator handles registration. Transient goals (current-target-predicted-tile, dynamic per planner) bypass this and register lazily via `nav.fieldFor(tile)` from the behaviour scorer; cache uses LRU + TTL to keep memory bounded.

### Composition: blended gradients for "go there but avoid this"

The architectural payoff is that "navigate to G while avoiding threats" composes naturally:

```java
Vec2d desired = gradientFor(goal).directionAt(cell)
             - 0.6 * gradientFor(threatField).directionAt(cell)
             + 0.3 * gradientFor(opportunityField).directionAt(cell);
```

One blend, one direction, one `SeekDirection` consumer. No NavigateTo + EvadeProjectile + SeekCover composition pyramid; the field arithmetic handles it. The behaviour-tree leaf shrinks to "compute blended gradient, pass to SeekDirection."

This is the influence-map composition pattern (Tozour, "Influence Mapping" in *Game Programming Gems 2*, 2001; Mark, *Behavioral Mathematics for Game AI*, 2009 ch. 12). Production references: StarCraft 2 unit pathing, Supreme Commander, Total War series.

### Construction lifecycle: `BotAiArenaContext.navigation()`

Per the cross-cutting decision shared with [ADR-0012](./0012-bot-spatial-analysis-services.md) + [ADR-0013](./0013-bot-tactical-goal-layer.md): one zone-global `BotAiHostService` owns one `BotAiArenaContext` per loaded arena, and the context exposes `navigation()` returning the `NavigationFields` accessor. On arena load, the host triggers Dijkstra for the arena's *known static goals* (registered by mechanic modules — flag tiles, KOTH centers — via the [ADR-0015](./0015-arena-objective-and-roles.md) ArenaObjective contract). Transient fields build on first request.

Build state per goal uses **`CompletableFuture<DistanceField>`** — Dijkstra runs on a worker thread and completes the future when done. Readers call `.getNow(EMPTY_FIELD)` for non-blocking access; pre-completion returns a zero-vector default → bot's steering falls through to `AvoidObstacles` only (wanders briefly) → field is ready on next read. Failed builds (Dijkstra throws) leave the future in a failed state; the cache evicts + retries on next request. One pattern covers BUILDING + READY + FAILED states — no separate flag, no polling, no flag-publication ordering bugs. **No crash; bot gets info next tick.**

### Door / topology changes

Doors changing state invalidate the affected fields. v2.0 detection: each field tracks the door tiles its Dijkstra pass crossed; a `DoorStateChanged` event invalidates fields whose tracked door set contains the changed door. Invalidated fields rebuild on next request.

Same trigger applies to `.lvl` reloads (development) — all fields invalidate.

Diagonal corner-clipping in the Dijkstra: reject diagonal moves through diagonally-adjacent walls (standard fix, one boolean check per move). Same constraint applies whether you're running A* or Dijkstra.

### Layering

- **api/infinity.math.Vec2d** — new 2D double-precision vector type; ~50 LOC; conversion helpers `Vec2d.fromXz(Vec3d)` / `.toXz()`. Used by `GradientField` and field-blending math across this ADR + [ADR-0012](./0012-bot-spatial-analysis-services.md).
- **api/infinity.ai.field.\*** — `ScalarField`, `GradientField`, `DistanceField`, `FieldGradient` (shared with [ADR-0012](./0012-bot-spatial-analysis-services.md)).
- **api/infinity.ai.field.NavigationFields** — the accessor surface.
- **infinity-server/.../ai/field/nav/** — Dijkstra impl, cache management, door-event subscription.
- **No client dependency.** Server-only; debug visualization in v2.x via wire-crossing components.

### Live-reload semantics

| Event | Action |
|---|---|
| `ZoneBotAiReloaded` | Re-read tile-supersampling factor. If changed, evict + rebuild all fields (rare). |
| `EngineBotAiReloaded` | No action (no engine-tier config consumed). |
| `ArenaGroovyReloaded` | No action (no arena-tier config consumed by navigation). |
| `DoorStateChanged` | Per-field door-tile tracking invalidates affected fields; rebuild on next request. |
| `.lvl` reload | Evict all fields for that arena. |

Per [ADR-0014](./0014-capability-derived-bot-composition.md) §"Engine vs zone Groovy tiers"; subscription pattern per ADR-0003 EventBus.

## Consequences

### Positive

- **Fits momentum physics.** Gradient sampling + steering composition matches how Reynolds-style vehicles actually move. No waypoint-following overshoot; no path-smoothing pass needed.
- **Shared computation across bots.** N bots heading toward the same goal pay the Dijkstra cost once. A* per-bot scales N×; flow field is O(1) per bot per tick after build.
- **Composes with other fields.** Threat avoidance, opportunity seeking, ally-following are all gradient blends — same primitive, same consumer. The behaviour-tree leaves shrink considerably.
- **Per-tick cost is trivial.** One array lookup per bot per goal-of-interest. Bot at 30Hz × 8 bots × 2 goals = 480 lookups/sec; negligible.
- **No clearance-aware A*, no Floyd smoothing, no path-invalidation re-plan logic.** The whole machinery the original ADR specified disappears.

### Costs

- **Memory.** ~2MB per field at 1024² × 2 bytes; ~30MB per arena at 15 active fields. Higher than A* (which stores nothing per goal). Downcountable by tile-supersampling.
- **Dijkstra build cost.** 50-200ms per field on a 1024² map. Acceptable for arena load (run on worker; bots wander briefly during build) and for new-goal registration (rare). Becomes painful if many transient goals turn over fast — mitigation is the LRU/TTL eviction policy.
- **Doesn't expose path *length* directly.** A* returns a totalCost number; flow fields require sampling distance at the bot's cell to get "how far am I from goal." Trivial in practice (`distanceField.valueAt(cell)`), but worth noting.
- **One-tick stall on first request for a new transient goal.** First sample returns zero vector while Dijkstra runs on worker; bot wanders for one planner cycle (~150-500ms) before the field is ready. Acceptable for the use case (planner cadence is anyway slower than tick rate).

### Performance budget (estimates; profile-validated per slice)

- **Memory per field:** ~2 MB at 1024² tiles × 2 bytes (or 0.5 MB at 4× supersampling).
- **Memory per arena:** ~30 MB for ~15 active fields (static goals + transient cache, LRU-bounded).
- **Dijkstra build:** 50-200 ms on commodity hardware for 1024² with ~30% passable; on worker, doesn't block sim tick.
- **Per-tick lookup:** O(1) array index per bot per gradient query. Negligible.
- **Reload cost:** door-state event invalidates ≤5 fields typical; rebuild on worker.

Numbers are seeds; first impl slice measures actuals and updates this section.

### Neutral

- **Doesn't model dynamic obstacles.** Same as A* would have been. Ships + projectiles stay in the local-avoidance layer.
- **Wormholes treated as passable.** Bot walking into one teleports; field doesn't predict the teleport. Acceptable for v2.0; the "actively use wormhole as shortcut" behaviour is a separate planner-aware concern.
- **Multiple goals = multiple fields.** Not a hierarchy. If the planner cares about 5 goals at once, 5 fields exist. Behaviour scorers pick which gradient to blend per the current `TacticalGoal`.

## Alternatives considered

### A. Grid A* over the `.lvl` tile grid (the prior version of this ADR)

**Why considered:** Industry-standard; well-documented; widely understood; the tile-grid map idiom maps to it directly. This was the original decision.
**Why rejected:** Returns waypoint sequences, not gradients. Subspace ships have momentum (thrust + rotation + drag + bounce); they cannot reliably follow waypoints. Reynolds 1999 §"Path Following" notes this mismatch explicitly. The workarounds (Floyd LoS smoothing, deceleration-zone advancement, lookahead waypoint selection) recover quality but never fully solve the impedance mismatch. Flow fields *are* the gradient primitive the steering layer wants, with no translation layer between.

The cost the previous version paid for A* (per-goal per-replan A* runs, clearance-aware adjacency precompute, Floyd smoothing pass, re-plan trigger logic, `FollowPath` BT action with waypoint indexing) all disappears under flow fields.

### B. Reactive steering only (no spatial navigation at all)

**Why considered:** v1 ships this; no new layer.
**Why rejected:** Same as the original ADR — v2 tactical brains need remote-goal navigation through maps with walls. Reactive can't do it.

### C. Hierarchical A* (HPA*) from day one

**Why considered:** Faster than grid A* on large maps; abstract paths refined per-cluster.
**Why rejected:** Inherits the same waypoint-vs-momentum mismatch as flat A*. The abstraction speeds up the *search*, but the output shape (waypoints) is still wrong for the steering layer. The flow field design sidesteps this by changing the output shape, not by speeding up the search.

### D. Continuous-space navigation (RVO / velocity obstacles)

**Why considered:** Industry-standard for crowd simulation; handles dynamic obstacles directly.
**Why rejected:** Massive complexity overhead for a game where dynamic-obstacle avoidance is already handled by Reynolds `AvoidObstacles`. RVO's wins are in tight-crowd scenarios (dozens of agents in close proximity); Subspace bots are sparse on the map.

### E. Pre-computed navmesh polygons (mesh-from-tile-grid)

**Why considered:** Industry standard in Unity/Unreal; more expressive for non-grid geometry.
**Why rejected:** Subspace maps are tile grids natively. Mesh decomposition adds build complexity + lossy fidelity for zero expressive gain. Also inherits the waypoint output problem.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Primitive | **Flow field per goal tile** (Dijkstra-derived distance field + gradient). Not A*, not waypoints. |
| Bot consumes via | `SeekDirection(gradient)` steering primitive composed with `AvoidObstacles` in `PrioritySteering`. |
| Composition with other fields | Gradient blend: `desired = α·navGradient - β·threatGradient + γ·opportunityGradient`. Same primitive across all field types. |
| Lifecycle | `BotAiArenaContext.navigation()` ([ADR-0012](./0012-bot-spatial-analysis-services.md) bundle). Static fields built at arena load; transient fields lazy + LRU/TTL. |
| Door / topology changes | Per-field door-tile tracking; door event invalidates affected fields; rebuild on next request. |
| Layer | `api/infinity.ai.field.*` interfaces (shared with [ADR-0012](./0012-bot-spatial-analysis-services.md)); server impl under `ai/field/nav/`. |
| Wormholes | Treated as just-another-passable; teleport happens at the physics layer. Wormhole-shortcut-awareness deferred. |
| Memory budget | ~30MB per arena at 15 active fields (1024² map, 2 bytes/cell). Downcountable via tile supersampling if needed. |
| Per-tick cost | One array lookup per bot per relevant field. Negligible. |

## Open work

- **Implementation slice paired with the first v2 navigation-consuming behaviour.** Likely the Shark-miner's "navigate to chokepoint" leaf — forces flow-field-per-chokepoint-tile end-to-end.
- **`SeekDirection` steering primitive.** New addition to the Reynolds suite in [ADR-0009](./0009-bot-ai-architecture.md). ~30 LOC; pairs with the field consumer.
- **`BotDebug` HUD field visualization.** Render gradient at every Nth cell as a tiny arrow; render distance field as a heat overlay. Cheap once a debug overlay system exists.
- **Tile-supersampling for memory reduction.** Default off; toggle in `zone-bot-ai.groovy` (per-zone performance tuning, per [ADR-0014](./0014-capability-derived-bot-composition.md) §"Engine vs zone Groovy tiers") if real-world memory measurement demands it.
- **Door-event subscription.** `DoorSystem` event hook; per-field door-tile tracking. Tracked in [`.scratch/bot-ai-v2/`](../../.scratch/bot-ai-v2/) once the first arena with doors lands.
- **Sparse representation for mostly-empty Subspace maps.** Run-length or quadtree storage of the distance field; saves substantial memory if profiling shows fields are mostly POSITIVE_INFINITY.

## References

### Internal

- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI substrate. Steering layer consumes flow-field gradients via new `SeekDirection` primitive.
- [ADR-0012](./0012-bot-spatial-analysis-services.md) — Scalar fields as the unified primitive. Navigation is one specialization.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — Tactical-goal layer. Primary consumer; planner registers goal tiles, behaviour scorers blend gradients.
- [ADR-0015](./0015-arena-objective-and-roles.md) — Arena objectives. Mechanic modules register static goal tiles (flag tiles, KOTH center, etc.) via the objective contract.
- [`world-coordinates.md`](../../.claude/rules/world-coordinates.md) — `TileId` API; the canonical map-coordinate shape.

### External

- **Craig Reynolds, *Steering Behaviors for Autonomous Characters*** (GDC 1999). §"Path Following" notes the waypoint-vs-momentum mismatch this ADR's revision is built around. red3d.com/cwr/steer/
- **Paul Tozour, "Influence Mapping"** in *Game Programming Gems 2* (2001). The canonical reference for 2D scalar fields driving AI decisions; flow fields are a navigation specialization.
- **David Mark, *Behavioral Mathematics for Game AI*** (2009). Ch. 12 on influence maps + utility scoring with field-sampled inputs.
- **StarCraft 2** (Blizzard) and **Supreme Commander** (GPG) — production examples of flow fields for unit pathing in real-time strategy games with hundreds of agents.
- **Amit Patel, "Introduction to A*"** (https://www.redblobgames.com/pathfinding/a-star/introduction.html) — the canonical pedagogical A* reference, useful background on why the field-based alternative differs.
