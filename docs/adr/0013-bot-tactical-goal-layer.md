# ADR 0013 — Bot tactical-goal layer

**Status:** Proposed
**Date:** 2026-05-23
**Deciders:** Asser Fahrenholz

## Context

The bot AI v1 stack ([ADR-0009](./0009-bot-ai-architecture.md)) tops out at a **Behaviour Tree of reactive primitives** — `Selector(Evade, Engage, Pursue, Wander)`. The BT decides *which behaviour fires this tick given current state* (target visible? in weapon range? low energy?). It does not decide *what the bot is trying to accomplish this minute given its weapons + ship + map context*.

That second decision is the gap the chat thread (2026-05-23) exposed:

- **Shark with mines** should pick a goal: "deny chokepoint at tile T" or "ambush enemy path predicted to cross corridor C." Both are *positional* + *durable* — they last seconds, not ticks.
- **Leviathan with L3 bombs** should pick a goal: "find target cluster + secure overcover + splash." The goal binds together multiple primitives (navigate, hold position, time-the-shot).
- **Javelin with bouncing bombs** should pick a goal: "fire bounce-shot at predicted enemy position from this exact firing tile." Geometry-specific.

In all three cases, the goal is **what the bot is doing for the next several seconds**, parametrised by spatial query results ([ADR-0012](./0012-bot-spatial-analysis-services.md)) and reachable via pathfinding ([ADR-0011](./0011-bot-navigation-navmesh.md)). The BT's job becomes "execute the chosen goal"; goal selection is one layer up.

Pattern reference: this is the **GOAP** (Goal-Oriented Action Planning) family from Jeff Orkin's F.E.A.R. work (GDC 2006), in the *lite* form — goal *selection* without full action *planning*. The Halo 2 paper (Isla, GDC 2005) calls the same shape "encounter scripting"; The Sims calls it "advertising goals." Utility-based selection (David Mark, *Behavioral Mathematics for Game AI*, 2009) is the canonical scoring mechanism.

### What this ADR does not settle

- **Per-archetype goal sets** in detail. This ADR establishes the *layer* + *selection mechanism* + *goal-as-data shape*. Which goals a given archetype offers is per-archetype implementation work in the v2 PRD.
- **Multi-bot squad coordination** (team-level goals: "Shark-1 mines corridor, Shark-2 ambushes the diversion"). Single-bot tactical first; squad-tactical is v2.x+.
- **Online learning / weight tuning.** Utility weights are authored (Groovy CCP, [ADR-0010](./0010-bot-composition-dsl.md)); ML-driven adaptation is out of scope for the foreseeable future.
- **Hierarchical goal decomposition** (a top-level goal recursively spawning subgoals). GOAP-lite is flat: planner picks one goal, BT executes it. Deep hierarchies wait until flat insufficient.

## Decision

**A `TacticalPlanner` sits above the BT and picks one `TacticalGoal` for the bot at a slower cadence than the BT tick (every ~500ms-1s). Goals are typed data (records) describing what the bot is trying to achieve, with parameters drawn from spatial-service queries. The chosen goal is written to the `Blackboard`; the BT reads it and dispatches to per-goal-type Sequence branches. Goal selection is utility-based: the planner enumerates candidate goals available to the bot's archetype, scores each via per-goal utility functions, picks the highest. Goals are "sticky" (preempted only by significant utility margin or by goal completion / failure).**

### Layer position

```
TacticalPlanner   (every 500-1000ms)
   ↓ writes Blackboard.currentGoal
Behaviour Tree    (every tick)
   ↓ reads Blackboard.currentGoal; dispatches to per-goal branch
Steering          (every tick)
   ↓ writes intent
PlayerDriver      (per physics step)
```

The planner does NOT replace the BT — the BT still handles per-tick reactive concerns (mid-action evade on incoming missile; pulse-fire when aim crosses; obstacle avoidance). The planner sets the *strategic context* the BT operates within.

### `TacticalGoal` shape

Goals are immutable records implementing a marker interface:

```java
public sealed interface TacticalGoal { … }

public record DenyChokepoint(TileId tile, double deadlineSeconds) implements TacticalGoal {}
public record AmbushPath(List<TileId> predictedPath, double validUntilSeconds) implements TacticalGoal {}
public record SplashCluster(Vec3d centerPos, double radius, EntityId primaryTarget) implements TacticalGoal {}
public record BounceShot(TileId firingTile, double heading, int bounces, EntityId target) implements TacticalGoal {}
public record SetupOvercover(TileId coverTile, Vec3d fireLineDir) implements TacticalGoal {}
public record Engage(EntityId target) implements TacticalGoal {}    // v1 Brawler default
public record Wander() implements TacticalGoal {}                    // v1 Brawler fallback
```

Per-archetype goal sets are a subset of this universe. Brawler (v1) uses `Engage` + `Wander`. Shark-miner (v2) uses `DenyChokepoint` + `AmbushPath` + `Engage` + `Wander`. New goal types are records added here; the BT dispatches on the record's class.

### Per-archetype Goal sets + utility scorers

Each `BrainArchetype` declares (a) the goal types it considers, and (b) a `UtilityScorer` for each. Scorers return a `double` score; the planner picks the max.

```java
public interface UtilityScorer<G extends TacticalGoal> {
  /** Higher = more desirable. Negative = "do not consider." */
  double score(G candidate, Blackboard blackboard);
  /** Enumerate candidate goals of this type from current world state. */
  List<G> enumerate(Blackboard blackboard);
}
```

Per-archetype planner registration:

```java
// inside MinerShark.tacticalSetup(...)
planner.register(DenyChokepoint.class, new DenyChokepointScorer(weights));
planner.register(AmbushPath.class, new AmbushScorer(weights));
planner.register(Engage.class, new EngageScorer(weights));
```

Scorers consume spatial services ([ADR-0012](./0012-bot-spatial-analysis-services.md)) via the `Blackboard`. Example:

```java
public double score(DenyChokepoint candidate, Blackboard bb) {
  double choke = bb.spatial().chokepoints().scoreFor(candidate.tile());      // static
  double traffic = bb.spatial().traffic().heatAt(candidate.tile());          // dynamic
  double distance = bb.spatial().pathLength(bb.self().tile(), candidate.tile());
  return choke * traffic / Math.max(1.0, distance * 0.1);
}
```

The weight constants in each scorer are the per-archetype parameterization. ADR-0010's per-arena weight overrides extend naturally here — weights live in `BotBrainConfig` (or a per-archetype follow-on `BotTacticalConfig`).

### Planner cadence + stickiness

The planner runs **every ~500ms** (not per-tick) — `BotBrainSystem` re-selects on a throttled timer. Per-tick selection is the cost trap AND the source of "the bot keeps switching goals mid-action" behaviour bugs.

**Sticky preemption rule**: a new candidate goal preempts the current goal only if `new.score > current.score × (1 + STICKINESS_MARGIN)` (e.g. margin 0.25 = 25%). Without margin, two goals scoring 0.50 vs 0.51 would oscillate; with margin, the current goal stays unless a clearly better option appears.

**Forced re-select** on: goal completion (BT branch returned SUCCESS terminally), goal failure (BT branch returned FAILURE — path blocked, target despawned), goal expiry (goal's `deadlineSeconds` passed), or arena event (round transition, player joined).

### BT integration

The BT root extends to dispatch on the current goal type via the existing `Selector` + `Sequence` primitives — no new BT semantics. Example for the Shark miner archetype:

```java
Selector(
  // Per-tick reactive overrides (highest priority — preempt goal mid-execution).
  Sequence(LowEnergy, HasTarget, SteerEvade),

  // Goal-driven branches: each is gated on the current goal type.
  Sequence(IsGoal(DenyChokepoint.class),    ExecuteMineDeploy),
  Sequence(IsGoal(AmbushPath.class),        ExecuteAmbush),
  Sequence(IsGoal(Engage.class), HasTarget, InWeaponRange, SteerOrbitTarget, ...),

  // Wander fallback when no goal applies (shouldn't normally happen; planner always picks one).
  SteerWander
)
```

`IsGoal(...)` is a new Condition leaf — returns SUCCESS when `bb.currentGoal()` matches the type. `ExecuteMineDeploy` / `ExecuteAmbush` are per-archetype Action sequences (themselves Sequences of pathfind / position / fire primitives).

### Layering

- **api/infinity.ai.tactical.*** — `TacticalGoal` sealed interface + standard record subtypes, `UtilityScorer` interface, `TacticalPlanner` interface, `IsGoal` Condition. Per [ADR-0005](./0005-layered-architecture.md) data + interfaces only.
- **infinity-server/.../ai/tactical/*** — `TacticalPlannerImpl`, per-archetype scorer impls, per-archetype `Execute*` Action impls.
- **`Blackboard`** (api, extended) — `currentGoal()` accessor + `setCurrentGoal()` setter; `spatial()` accessor returning the per-arena `ArenaSpatial` bundle.
- **`BotBrainSystem`** (server) — owns the planner's throttle timer; invokes `planner.select()` on cadence; writes result to blackboard.

## Consequences

### Positive

- **Tactical brains expressible.** The chat-thread scenarios (Shark miner, Javelin bouncer, Leviathan AOE) become composable: each archetype = (goal types) × (scorers) × (Execute* sequences).
- **Goals are testable in isolation.** A scorer takes a candidate + blackboard; pure function. Unit tests are trivial.
- **BT stays simple.** No new BT primitives beyond `IsGoal`. The complexity moves to data (goal types) and pure functions (scorers), both easier to reason about than nested BT branches.
- **Stickiness eliminates oscillation.** The most common "AI bug" category from utility systems is jittery oscillation; explicit margin + completion-based re-select address it head-on.
- **GOAP-lite is debuggable.** Each tick the planner can log "considered N goals, scored {Engage=0.5, AmbushPath=0.7}, picked AmbushPath." Surface this in `BotDebugHudState` and the bot's reasoning becomes visible.

### Costs

- **More layers means more concepts.** A new contributor must understand steering + BT + goals + scorers + spatial services. Mitigation: the layers compose linearly (each only knows the one below); no contributor needs all five at once.
- **Per-tick planner-throttle bookkeeping.** Minor — one timestamp per bot. Cost is negligible vs the BT tick itself.
- **Goal-record class proliferation.** Each new tactical goal = new record type. Acceptable: records are tiny, sealed interface keeps the list discoverable, dispatch is type-based.
- **Coupling to spatial services + navmesh.** A tactical archetype's value depends on both being implemented. v2.0 sequencing must land all three ADRs' impls (or stubs sufficient for one archetype) before any tactical archetype demos.

### Neutral

- **Stickiness margin + planner cadence are tunable.** Default 25% / 500ms; surface in `BotTacticalConfig` (per-archetype) once a clear-cut "this needs to be looser/tighter" case appears. Don't over-knob until measurements demand.
- **Goal lifetime is per-goal-type.** Some goals are durative (Ambush expires when path passes), some are momentary (Engage as long as target visible), some are completion-driven (DenyChokepoint until mines run out). The `Goal` record carries its own validity context.
- **Cross-archetype goal sharing.** A goal type like `Engage(target)` makes sense for every combatant archetype. Goal records live in `api/infinity.ai.tactical.*` and are shared across archetypes; per-archetype customization is in the Scorer + Execute* sequences, not in new record types.

## Alternatives considered

### A. Pure utility AI (no BT)

**Why considered:** GAMASUTRA: "utility AI replaces behaviour trees entirely." Every action evaluated by utility every tick; pick highest; execute. Simpler conceptually.
**Why rejected:** Loses the per-tick reactive sequencing the BT excels at (LowEnergy gate on Evade; mid-orbit fire-when-aimed). Utility AI is great at *what to do strategically*; BTs are great at *how to execute reactively*. Combining both ([Mark]) is the proven shape; rejecting either is a regression.

### B. Pure GOAP (action planner generates plans)

**Why considered:** F.E.A.R.'s original GOAP plans sequences of preconditioned actions to satisfy goals. Most general; most powerful.
**Why rejected:** **Author cost.** Each action needs precondition + effect declarations; the planner does A* over the action graph. Sufficient for our scope (≤10 goal types per archetype, ≤5 actions per goal) is *GOAP-lite*: goal selection without action planning. Promote to full GOAP if action sequences need dynamic composition; today they're per-goal-type hand-authored Sequence branches in the BT.

### C. Goal-per-BT-leaf (no separate planner)

**Why considered:** Every BT leaf carries "should I be doing this?" check; no central planner.
**Why rejected:** Loses the *centralized strategic decision-making* this ADR exists to enable. Distributed goal selection has no coordinator; the bot can't say "I've decided to be a miner this minute" — only "this leaf wants to fire, that leaf wants to wander." Strategic identity disappears.

### D. Per-tick planner re-selection (no stickiness)

**Why considered:** Always optimal; the planner always picks the currently-best goal.
**Why rejected:** Oscillation. Two goals scoring 0.49 / 0.50 flip-flop every tick; the bot starts every action, never finishes. Stickiness is the universally-applied fix in the utility-AI literature ([Mark], [Buckland]).

### E. Hierarchical goals (top-level → subgoals)

**Why considered:** Goals like "win the round" decompose into "control flag room" → "deny corridor" → "place mine." Most general.
**Why rejected for v2.0:** Premature. Flat goals + hand-authored per-goal Execute Sequences cover the chat-thread scenarios. Promote when a goal's per-tick complexity exceeds what a BT Sequence can express cleanly. Hierarchical Task Networks (HTN) is the canonical formalism if/when we get there.

## Resolved decisions (TL;DR)

| Decision | Resolution |
|---|---|
| Layer position | `TacticalPlanner` above BT, below steering. Planner picks Goal; BT dispatches on it. |
| Goal shape | Immutable record types implementing `sealed interface TacticalGoal`. Parameters drawn from spatial-service queries. |
| Selection mechanism | Per-archetype `UtilityScorer<G>` per goal type. Highest-scoring candidate wins. |
| Cadence | Planner runs every ~500ms; not per-tick. BT continues to tick every frame. |
| Stickiness | New goal preempts current only if `score > current × (1 + STICKINESS_MARGIN)`. Default margin 25%. |
| Goal sharing | Goal record types live in `api/infinity.ai.tactical.*`; per-archetype customization in Scorer + Execute Sequences only. |
| BT integration | New `IsGoal(GoalClass)` Condition leaf; per-archetype `Execute*` Action Sequences. No other new BT semantics. |
| Per-archetype config | Weights live in `BotBrainConfig` (or per-archetype `BotTacticalConfig`); per [ADR-0010](./0010-bot-composition-dsl.md). |

## Open work

- **v2 PRD authoring.** This ADR + ADR-0011 + ADR-0012 together define the substrate; the v2 PRD breaks implementation into slices. Recommended slice shape: one archetype + its goals + its required spatial services per slice (Slice 1: Shark miner end-to-end → forces Chokepoint + Traffic + DenyChokepoint goal + ExecuteMineDeploy). Vertical slices over horizontal layer-by-layer for the same reasons ADR-0008 prefers vertical arena modules.
- **`BotDebugHudState` extension.** Surface `currentGoal` + `lastScores` (top-N goal scores from the last planner run) so AI authoring can see what the bot is "thinking." Cheap; high-value; ships with the first v2 archetype.
- **Per-archetype config record.** When the first archetype's scorer weights need tuning, decide whether to extend `BotBrainConfig` (one record, all knobs) or split into per-archetype `BotShark Config` / `BotLeviathanConfig` / etc. (one record per archetype). The ADR-0010 trajectory suggests per-archetype; decision lands with the first concrete archetype impl.
- **Stickiness + cadence tuning.** Defaults are seed values. Expect 1-2 rounds of tuning during v2 stability.
- **Test infrastructure.** `FakeArenaSpatial` (per ADR-0012) + a `TacticalPlannerHarness` that runs the planner without a full server. Goal scorers should be unit-testable in isolation; the harness covers planner-level concerns (cadence, stickiness, preempt rules).

## References

### Internal

- [ADR-0001](./0001-ecs-component-model.md) — RaM canonical-writer rule. `BotBrainSystem` writes the goal to `Blackboard` (server-side scratch, not an ECS component) — no canonical-writer concern for Goal selection.
- [ADR-0002](./0002-config-component-projection.md) — CCP. Goal scorer weights flow through the same CCP pipeline as ship stats.
- [ADR-0005](./0005-layered-architecture.md) — api purity. Goal records + interfaces are api-side; impls are server.
- [ADR-0009](./0009-bot-ai-architecture.md) — Bot AI architecture. This ADR adds the missing tactical-decision layer.
- [ADR-0010](./0010-bot-composition-dsl.md) — Per-arena DSL. Tactical weights extend the same Groovy-CCP pipeline.
- [ADR-0011](./0011-bot-navigation-navmesh.md) — Navmesh. Required for any Goal type that involves "go to remote tile."
- [ADR-0012](./0012-bot-spatial-analysis-services.md) — Spatial services. Required as input data for goal scorers.

### External

- **Jeff Orkin, "Three States and a Plan: The AI of F.E.A.R."** (GDC 2006). The canonical GOAP reference. This ADR uses GOAP-lite (goal selection only, no action sequence planning).
- **Damian Isla, "Handling Complexity in the Halo 2 AI"** (GDC 2005). Encounter-level tactical reasoning; "behaviour DAG with character context" maps to our (BT + Goal) split.
- **David Mark, *Behavioral Mathematics for Game AI*** (2009). Comprehensive treatment of utility-based AI selection — scoring functions, response curves, oscillation, stickiness margins. The textbook for this layer.
- **Mat Buckland, *Programming Game AI by Example*** (2005). Goal-driven agent architecture chapter; the original C++ reference shape this ADR's interfaces mirror.
- **Penny Drennan & Mark Boyer, "Implementing Goal-Oriented AI Action Planning"** (various GDC). Practical scoping notes that informed the GOAP-lite-not-full-GOAP decision.
