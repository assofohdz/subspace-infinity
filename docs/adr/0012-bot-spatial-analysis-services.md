# ADR 0012 — Bot spatial analysis: scalar fields as the unified primitive

**Status:** Accepted (revised 2026-05-23) — implemented by bot-AI v2 slice #07 (2026-05-25)
**Implementation:** Slice #07 landed the scalar-field substrate — `FieldBlend`/`Weighted`/`FieldClamp` operators + `ScalarField.EMPTY` (api); `TeamDensityField`, `ThreatField` (weapon-range falloff, LoS-gated), `OpportunityField` (prize-sourced), `CombatDensityField` (fire-origin splat + time-decay); `ChokepointAnalyzer` as a load-time tile-list producer (geometric width-narrow detection re-ranked by `pinch × (1 + combat + k·totalDensity)`, top-N pinned as static nav goals); blended-gradient navigation (`nav + navThreatWeight·threatDescent + navOpportunityWeight·oppAscent`); and the `follow-traffic` consumer behaviour proving it end-to-end. CoverFinder/LineOfSightOracle confirmed dropped as services (one-off Bresenham raycasts at the leaf). Follow-up: extract an `ArenaSpatialFields` manager from `BotBrainSystem`.
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz
**Revision note:** This ADR was originally drafted as a *suite of distinct services* — TrafficHeatmap, ChokepointAnalyzer, ArenaCongestionField, BounceTracer, CoverFinder, LineOfSightOracle — each with its own interface and update cadence. That framing was reviewed against the actual game shape (2D top-down, sparse obstacles, no cover in the FPS sense, open spaces) and most services either didn't fit or were producing semantically-similar data through divergent APIs. This revision collapses the suite into a single primitive — **2D scalar fields over the `.lvl` tile grid** — with concrete fields as specializations and a small set of derived operators (gradient, blend). [ADR-0011](./0011-bot-navigation-navmesh.md)'s flow fields are one such specialization. CoverFinder and LineOfSightOracle are dropped as services (replaced by one-off raycasts at the leaf level); ChokepointAnalyzer becomes a tile-list producer, not a runtime service.

## Context

Bot v2 tactical brains need to answer spatial questions the per-tick steering layer can't:

- **Navigation** — "from this cell, which direction makes progress toward goal G?" (the flow-field shape, [ADR-0011](./0011-bot-navigation-navmesh.md)).
- **Threat awareness** — "how dangerous is this cell?" — Σ over enemies of (weapon-range Gaussian centered at enemy position, dilated by walls).
- **Opportunity awareness** — "how valuable is this cell?" — prize positions, ally-support positions, ammo tiles.
- **Combat density** — "where is recent firing activity?" — smear recent shots in space + time.
- **Player density** — per-team, "where are the allies" / "where are the enemies."

These are **stateful, shared across bots in an arena, and shaped identically**: each one is a 2D scalar value over the same tile grid, with the same query operations (sample at cell, sample at position, gradient at cell, blend with weights). The original ADR-0012 draft modeled them as six distinct services with bespoke interfaces; the revised design unifies them around one primitive.

Pattern reference: the *influence map* family from RTS / FPS AI literature (Tozour, "Influence Mapping" in *Game Programming Gems 2*, 2001; Mark, *Behavioral Mathematics for Game AI*, 2009 ch. 12). Production references: Age of Empires II, Killzone 2, every Halo combat encounter, StarCraft 2 unit pathing. The pattern is older than any of the services the prior draft proposed.

### What this ADR does not settle

- **Tactical decisions made from sampled values.** [ADR-0013](./0013-bot-tactical-goal-layer.md) — planner scorers sample fields and pick goals.
- **Pathfinding mechanism.** [ADR-0011](./0011-bot-navigation-navmesh.md) — flow field, which is one specialization of this ADR's `ScalarField`.
- **Client visualization of fields.** v2.x affordance; debug overlay reading via wire-crossing components.
- **Per-archetype scoping** of which fields a brain consumes. Fields are arena-scoped; consumers opt in by sampling. Adding a field doesn't enable it for any bot.

## Decision

**Spatial analysis is unified around a single primitive: `ScalarField` (a per-tile-grid float surface) and its derived `GradientField` (per-cell direction toward lower scalar value). Concrete fields (`DistanceField`, `ThreatField`, `OpportunityField`, `CombatDensityField`, `AllyDensityField`, `EnemyDensityField`) are specializations of `ScalarField` with their own build/update mechanics. A small set of composite operators (`FieldBlend`, `FieldClamp`) lets behaviour scorers compose fields without writing new field types. The previously-proposed services that don't fit this shape (CoverFinder, LineOfSightOracle) are dropped as services and become one-off raycasts at the leaf level when needed. ChokepointAnalyzer becomes a load-time *tile-list producer* feeding [ADR-0015](./0015-arena-objective-and-roles.md) goal-tile registration, not a runtime service.**

### The primitive

```java
// api/infinity.ai.field
public interface ScalarField {
  int width();
  int height();
  /** Value at (x,y); semantics field-specific. POSITIVE_INFINITY = unreachable / undefined. */
  double valueAt(int x, int y);
  /** Convenience: sample at world position (interpolated or nearest-cell — impl choice). */
  default double valueAt(Vec3d worldPos) { ... }
}

public interface GradientField {
  /** Unit vector at (x,y) pointing toward lower scalar value. Zero vector at minima or undefined cells. */
  Vec2d directionAt(int x, int y);
  default Vec2d directionAt(Vec3d worldPos) { ... }
}

public final class FieldGradient implements GradientField {
  public FieldGradient(ScalarField source) { ... }
}
```

That's the substrate. Everything below is a specialization.

### Concrete fields

| Field | Builder | Update mode | Primary consumer |
|---|---|---|---|
| `DistanceField(goalTile)` | Dijkstra from goal over passable tiles | Static per goal; rebuild on door / topology change | Navigation ([ADR-0011](./0011-bot-navigation-navmesh.md)) |
| `ThreatField` | Σ over enemies of (weapon-range falloff centered on enemy, wall-blocked) | Dynamic, per-tick or every-N-ticks | Evade behaviours; "navigate to G avoiding threats" composition |
| `OpportunityField` | Σ over prizes / ammo / ally-support of (value falloff) | Dynamic, per-prize-event or every-N-ticks | Prize-grab behaviours; engage scoring boost |
| `CombatDensityField` | Recent firing events smeared in space + time-decayed | Dynamic, per-fire-event + decay tick | "Where's the action" — scout / engage |
| `AllyDensityField` / `EnemyDensityField` | Per-team team-position sum | Dynamic, every-N-ticks | Flock-toward / avoid-deathball decisions |

Specializations land **on-demand**: build the field type when the first behaviour scores against it, not before. The ADR reserves the design space; the impls follow consumer demand.

### Composite operators

For "navigate to G but avoid threats":

```java
ScalarField composite = FieldBlend.of(
    Weighted.of(distanceField, +1.0),
    Weighted.of(threatField,   -0.6),
    Weighted.of(opportunityField, +0.3)
);
Vec2d desired = new FieldGradient(composite).directionAt(botCell);
```

Or, simpler when the consumer just wants a blended *gradient* (not a blended scalar):

```java
Vec2d desired = navGradient.directionAt(cell)
             .mul(1.0)
             .sub(threatGradient.directionAt(cell).mul(0.6))
             .add(opportunityGradient.directionAt(cell).mul(0.3));
```

Both work. `FieldBlend` is cheap when the consumer wants to sample the composite at multiple cells; per-cell gradient blending is cheaper when the consumer samples one cell.

### What got dropped from the prior draft

- **`CoverFinder` as a service.** There is no cover in 2D top-down; the closest analogue is "is there a wall between me and shooter," which is one raycast at the BT leaf needing it. No service.
- **`LineOfSightOracle` as a service.** Same reasoning — one raycast per query, computed on demand at the leaf. The "service" was a wrapper around `Bresenham line-of-sight`; we don't need a service for a 10-line function.
- **`TrafficHeatmap` as a separate concept.** Replaced by `CombatDensityField` (recent fires) or `EnemyDensityField` (recent positions), depending on what the consumer actually wants. The "heatmap" abstraction collapsed because both candidates were really scalar fields with different update mechanics.
- **`MinePlacementScorer` (deferred service).** Replaced by composing `CombatDensityField + DistanceField(existingMines) + ChokepointTileList` at the behaviour-scorer level. No new service; one scorer's composition logic.

### What got reshaped

- **`ChokepointAnalyzer` is now a load-time tile-list producer**, not a runtime service. The width-narrow detection algorithm runs once at arena load over the passable tile grid (same brushfire pass that builds clearance data for navigation, [ADR-0011](./0011-bot-navigation-navmesh.md)); output is a `List<TileScored>` of chokepoint tiles. The tile list feeds [ADR-0015](./0015-arena-objective-and-roles.md) goal-tile registration — chokepoint tiles become candidate static `DistanceField` goals.
- **`ArenaCongestionField` is renamed `EnemyDensityField`** (with `AllyDensityField` sibling). Same shape; clearer name.
- **`BounceTracer` is the one previously-proposed service that earns its keep.** It is *not* a scalar field — it's a geometric raycast simulator for bouncing projectiles. Lands as its own narrow service when Javelin-bouncer behaviour lands; not bundled into the field substrate. Reserved here for cross-reference; the impl ADR (or impl PR) carries the design.

### Per-arena lifecycle: `BotAiArenaContext` (consolidated)

Per the cross-cutting decision shared with [ADR-0011](./0011-bot-navigation-navmesh.md) + [ADR-0013](./0013-bot-tactical-goal-layer.md): one zone-global `BotAiHostService` owns one `BotAiArenaContext` per loaded arena. The context bundles the field accessors:

```java
public final class BotAiArenaContext {
  public NavigationFields navigation() { ... }       // ADR-0011
  public ScalarField threat() { ... }                // this ADR
  public ScalarField opportunity() { ... }
  public ScalarField combatDensity() { ... }
  public ScalarField allyDensity() { ... }
  public ScalarField enemyDensity() { ... }
  public List<TileScored> chokepoints() { ... }      // load-time tile list, not a field
  public BounceTracer bounceTracer() { ... }         // when Javelin lands
  // ArenaCapabilityNorms per ADR-0014 also lives here.
}
```

Brains hold a reference once at addObject time (via `Blackboard.arenaContext()`); behaviour scorers sample fields per planner tick.

**Load-order safety:** dynamic fields are ready immediately (they accumulate from zero); static fields and the navigation flow fields build asynchronously. Queries during the build window return the field's default (`POSITIVE_INFINITY` for distance, `0` for accumulated). No crash; bot gets info next tick.

### Update cadence

- **Static fields** (`DistanceField` per registered goal) — build at arena load (async) + on topology change.
- **Dynamic fields** — per-tick or every-N-ticks, throttled. Default cadence: every 10 ticks (~330ms at 30Hz) for `EnemyDensityField`/`AllyDensityField`; per-event for `CombatDensityField` (decay tick every 10); per-event for `OpportunityField` (prize-pickup / prize-spawn events).

All cadences are tunable in `zone-bot-ai.groovy` (per-zone performance tuning, per [ADR-0014](./0014-capability-derived-bot-composition.md) §"Engine vs zone Groovy tiers") per [ADR-0006](./0006-tuning-knobs-vs-magic-numbers.md). Default values are seeds; profile after v2.0 lands.

### Layering

- **api/infinity.math.Vec2d** — shared 2D double-precision vector type ([ADR-0011](./0011-bot-navigation-navmesh.md) §Layering owns the type definition).
- **api/infinity.ai.field.\*** — `ScalarField`, `GradientField`, `FieldGradient`, `FieldBlend`, `Weighted`, `DistanceField` interface, `TileScored` record. Pure data + interfaces.
- **infinity-server/.../ai/field/\*** — impls (per field type), build mechanics, update timers.
- **No client dependency.** Server-only authoritative computation. Client visualization (v2.x) reads via ECS wire-crossing components.

### Goal-tile contribution to navigation

`ChokepointAnalyzer`'s top-N tile list feeds into `BotAiHostService.onArenaLoad` (per [ADR-0011](./0011-bot-navigation-navmesh.md) §"Goal registration") alongside `ArenaObjective.staticGoalTiles()` ([ADR-0015](./0015-arena-objective-and-roles.md)). The orchestrator unions both sets and calls `nav.fieldFor()` for each. ChokepointAnalyzer itself does not call the navigation layer directly — it produces data; the orchestrator consumes.

`N` (chokepoint top count) is a zone-tier knob in `zone-bot-ai.groovy` (default 5). Larger N = more flow fields = more memory + more upfront Dijkstra work. Profile-validated per slice.

### Live-reload semantics

| Event | Action |
|---|---|
| `ZoneBotAiReloaded` | Re-read dynamic-field cadences + chokepoint-N. If chokepoint-N changed, re-derive ChokepointAnalyzer tile list + re-register goal tiles via orchestrator. |
| `EngineBotAiReloaded` | No action (no engine-tier config consumed by spatial fields directly). |
| `ArenaGroovyReloaded` | No action. |
| `.lvl` reload | Chokepoint list re-derived; dynamic fields reset (they accumulate from zero). |

## Consequences

### Positive

- **One primitive, many specializations.** New "thing to know about the map" = new `ScalarField` impl + register in the context. ~30-50 LOC per field, not a new service interface + lifecycle + tests.
- **Composition is gradient algebra.** "Go to G avoiding threats" is two-line math; no service-orchestration code.
- **Brains express tactical questions naturally.** Sample at position; read value; blend gradients. No grid-walking.
- **Shared cost across bots.** 8 bots in the same arena read the same fields; build cost amortized.
- **Testable in isolation.** A field is `(x, y) → double`. Stub one with a fixed function for tests. No mock orchestration.
- **Subset of the prior service suite collapses to zero code.** `CoverFinder`, `LineOfSightOracle`, `MinePlacementScorer` go away as services — replaced by raycasts and composition logic at the leaf level.

### Costs

- **Memory** per field at 1024² × ~2 bytes = ~2MB. For ~10 concurrent active fields (~5 static distance + 4-5 dynamic) ≈ 20-30MB per arena. Acceptable; downcountable via tile-supersampling.
- **Field-update CPU on dynamic fields.** Bounded; throttle by tick count if any field exceeds budget. Profile after v2.0 lands.
- **The flat `ScalarField` interface assumes 2D grid alignment.** If a behaviour ever wants "value at *world-position* with sub-tile fidelity," it interpolates via the default `valueAt(Vec3d)` — not worse than the prior service-per-need shape.

### Performance budget (estimates; profile-validated per slice)

- **Memory per dynamic field:** ~2 MB at 1024² × 2 bytes; ~6 dynamic fields concurrent = ~12 MB per arena.
- **Memory per arena (this ADR + ADR-0011 nav):** ~42 MB total.
- **Update cadence:** density fields every ~10 ticks (~330 ms at 30 Hz); combat/opportunity event-driven + decay every 10.
- **Per-update cost:** O(liveShips) for density fields; O(events) for combat/opportunity. Linear in arena population; expected bounded (Subspace arenas rarely exceed 64 ships).
- **Per-sample lookup:** O(1) array index. Negligible per consumer.

Numbers are seeds; first impl slice that lands a dynamic field measures actuals and updates this section.

### Neutral

- **`BounceTracer` stays its own thing.** It's not a field; bundling it would be category-error. Lives in the context as a separate accessor.
- **No service-per-bot scratch.** All fields are arena-scoped + omniscient (read from authoritative server state, not per-bot perception). Same trade-off the prior draft accepted for `ArenaCongestionField`: bots "know" cluster locations even where their personal perception wouldn't. v2.0 accepts this; per-bot perception masking is a v2.x query parameter if it matters.
- **No GOAP-shaped service dispatch.** Behaviour scorers know which fields they want; no central service registry routes queries. Flatter than the prior draft.

## Alternatives considered

### A. The prior service-suite design (6 distinct services with bespoke interfaces)

**Why considered:** This was the original v0 of this ADR. Modeled each spatial concern as its own service.
**Why rejected:** Most of the services were producing semantically-similar data through divergent APIs. `TrafficHeatmap.heatAt(tile)` and `ArenaCongestionField.densityAt(tile)` are both "sample a 2D scalar at this cell." The unification under `ScalarField` collapses the API surface 6× and makes composition (blend, gradient) work uniformly. `CoverFinder` + `LineOfSightOracle` were the genre-mismatched services — they belong to FPS-with-cover games; in 2D top-down they're per-call raycasts at the leaf level.

### B. Pure raw-data services (each field is a `double[][]` returned directly)

**Why considered:** No interface ceremony; consumers index into arrays.
**Why rejected:** Loses the type discrimination (`DistanceField` vs `ThreatField` — same shape, different semantics). Also forces consumers to know storage layout (row-major? column-major?). The interface abstracts storage choice (`ScalarField` impls may use run-length, quadtree, or dense arrays depending on what fits the data); consumers don't care.

### C. Push services into Groovy

**Why considered:** Operator-extensible "new spatial concern" without Java edits.
**Why rejected:** Field impls do real computation (Dijkstra, decay loops, event-driven updates) — not closure-tunable. The synergy table ([ADR-0014](./0014-capability-derived-bot-composition.md)) is the Groovy-tier knob; field impls are engine code.

### D. Inline computation in BT leaves

**Why considered:** No new layer; each leaf does what it needs.
**Why rejected:** Catastrophic redundant computation. 8 bots each rebuilding a threat field per tick = 8× the work. Also untestable in isolation.

### E. One monolithic `SpatialAI` service

**Why considered:** One thing to instantiate per arena.
**Why rejected:** Each field has its own update cadence + build mechanic; bundling them tangles the update loop. The `BotAiArenaContext` is *already* the bundle — it just exposes typed accessors per field rather than burying everything in one fat service.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Primitive | **`ScalarField` + `GradientField`**. One shape, many specializations. |
| v2.0 concrete fields | `DistanceField` (navigation — [ADR-0011](./0011-bot-navigation-navmesh.md)), `ThreatField`, `OpportunityField`, `CombatDensityField`, `AllyDensityField`, `EnemyDensityField`. Land on-demand per consumer. |
| Composition | `FieldBlend(Weighted...)` for blended scalars; per-cell gradient blending for one-cell consumers. |
| `ChokepointAnalyzer` | Load-time *tile-list producer*, not runtime service. Feeds [ADR-0015](./0015-arena-objective-and-roles.md) static goal-tile registration. |
| `BounceTracer` | Its own non-field service; lands when Javelin behaviour lands. Reserved cross-reference. |
| `CoverFinder` / `LineOfSightOracle` | **Dropped as services.** Replaced by one-off raycasts at the BT-leaf level. |
| Per-arena vs zone-global | Per-arena fields; zone-global host. Single `BotAiHostService` shared with [ADR-0011](./0011-bot-navigation-navmesh.md) + [ADR-0013](./0013-bot-tactical-goal-layer.md). |
| Lifecycle | `BotAiArenaContext` bundle. Build on arena load (static) / lazily on first sample (dynamic, transient). Default-value reads while building; no crash. |
| Update cadence | Tunable in `engine-bot-ai.groovy`. Default: every 10 ticks for density fields; per-event for combat / opportunity. |
| Knowledge scope | Omniscient (server-authoritative state, not per-bot perception). v2.x can mask per-bot if needed. |

## Open work

- **Implementation slicing.** First field to land is `DistanceField` (paired with navigation slice — [ADR-0011](./0011-bot-navigation-navmesh.md)). Next is whichever field the first non-navigation behaviour needs (likely `ThreatField` for evade scoring, or `CombatDensityField` for engage scoring).
- **Field-update budget.** Document target update-cost per field; profile after v2.0; throttle if any field exceeds budget.
- **Test infrastructure.** `FakeScalarField` (constructed from a `(x,y) → double` lambda) for behaviour-scorer tests. Real-impl tests at the field level.
- **Debug HUD overlay.** Render any selected field as a tile-color heatmap; render its gradient as arrows. Cheap once a debug-overlay system exists.
- **Sparse storage** for mostly-empty Subspace maps. Run-length or quadtree backing for `ScalarField` when profiling shows dense arrays are wasteful.
- **`BounceTracer` design.** Standalone ADR or impl PR when Javelin behaviour lands.

## References

### Internal

- [ADR-0005](./0005-layered-architecture.md) — api purity. Field interfaces are api; impls are server.
- [ADR-0008](./0008-arena-composition-and-modules.md) — `onArenaLoad`/`onArenaUnload` lifecycle; field host hooks here.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI architecture; this ADR adds the spatial substrate the v1 architecture didn't have.
- [ADR-0011](./0011-bot-navigation-navmesh.md) — Navigation flow fields are one `ScalarField` specialization. Shared `BotAiArenaContext`.
- [ADR-0013](./0013-bot-tactical-goal-layer.md) — Tactical-goal layer; primary consumer of field sampling.
- [ADR-0014](./0014-capability-derived-bot-composition.md) — Capability derivation; `ArenaCapabilityNorms` also lives on `BotAiArenaContext`.
- [ADR-0015](./0015-arena-objective-and-roles.md) — Arena objectives; `ChokepointAnalyzer` tile-list feeds objective goal-tile registration.

### External

- **Paul Tozour, "Influence Mapping"** in *Game Programming Gems 2* (2001). The canonical reference for 2D scalar fields driving AI decisions.
- **Damian Isla, "Halo 2 AI"** (GDC 2005). Encounter-level spatial reasoning; "tactical positions" service shape inspires the field-sampling consumer pattern.
- **David Mark, *Behavioral Mathematics for Game AI*** (2009). Ch. 12 — influence maps + utility scoring with field-sampled inputs.
- **Mat Buckland, *Programming Game AI by Example*** (2005). Chapter on territorial / influence maps in RTS-style AI.
- **StarCraft 2** (Blizzard) and **Supreme Commander** (GPG) — production examples of scalar fields for unit pathing + threat / opportunity reasoning.
