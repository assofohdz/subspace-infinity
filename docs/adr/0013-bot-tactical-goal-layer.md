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

Pattern reference: this is **utility-based goal selection** — agents have a set of named *advertising* goals (each declaring "I'm valuable for these reasons"); the agent's planner scores each candidate and picks the highest. The shape comes from The Sims (Maxis, ~2000, "smart objects advertise"), Killzone 2 (Guerrilla, GDC 2009), and is comprehensively documented in David Mark's *Behavioral Mathematics for Game AI* (2009). The Halo 2 paper (Isla, GDC 2005) layers this with BT execution — the same composition this ADR adopts.

**Not GOAP.** Jeff Orkin's F.E.A.R. paper (GDC 2006) describes Goal-Oriented Action *Planning* — an A*-over-action-graph planner that generates sequences of preconditioned actions to satisfy a goal. That's a different (more powerful, more expensive, harder-to-author) shape. The earlier draft of this ADR mis-cited F.E.A.R. as the reference; corrected here. We may grow toward GOAP if action *sequences* need dynamic composition, but v2 is utility-AI for goal *selection* only, with per-goal hand-authored Execute Sequences inside the BT.

### What this ADR does not settle

- **Per-archetype goal sets** in detail. This ADR establishes the *layer* + *selection mechanism* + *goal-as-data shape*. Which goals a given archetype offers is per-archetype implementation work in the v2 PRD.
- **Multi-bot squad coordination** (team-level goals: "Shark-1 mines corridor, Shark-2 ambushes the diversion"). Single-bot tactical first; squad-tactical is v2.x+.
- **Online learning / weight tuning.** Utility weights are authored (Groovy CCP, [ADR-0010](./0010-bot-composition-dsl.md)); ML-driven adaptation is out of scope for the foreseeable future.
- **Hierarchical goal decomposition** (a top-level goal recursively spawning subgoals). GOAP-lite is flat: planner picks one goal, BT executes it. Deep hierarchies wait until flat insufficient.

## Decision

**A `TacticalPlanner` sits above the BT and picks one `TacticalGoal` for the bot at a slower cadence than the BT tick (every ~500ms). Goals are typed data (records) produced by named `Behaviour` building blocks (e.g. "mine-congestion-points", "bullet-snipe-from-afar"); each behaviour enumerates candidate goals from current world state and scores them by intrinsic utility. The archetype is a *weight vector over behaviour names*; the planner multiplies intrinsic score × archetype weight + applies an additive stickiness margin against the currently-running goal. The chosen goal is written to the `Blackboard`; the BT dispatches to per-goal-type Execute Sequences. Tuning lives in three Groovy tiers: zone-wide (`BotBrainSystem` defaults), arena-wide (`BotBrainConfig` per arena), archetype-specific (named `archetype { … }` blocks declaring behaviour weights).**

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

### Named behaviours + archetype weight vectors

Each tactical concept the design team wants ("mine congestion points", "bullet-snipe from afar") is a named `Behaviour` — a reusable building block that knows how to enumerate candidate goals + score them intrinsically.

```java
public interface Behaviour<G extends TacticalGoal> {
  /** Stable name; the same string operators write in archetype { ... } weight blocks. */
  String name();

  /** Enumerate candidate goals of this type from current world state. */
  List<G> enumerate(Blackboard bb);

  /** Intrinsic utility of {@code candidate} in [0, 1]. Archetype weight applied by planner. */
  double intrinsicScore(G candidate, Blackboard bb);
}
```

Concrete v2 behaviours (planned, not all v2.0):

| Name | Goal type | Spatial inputs |
|---|---|---|
| `mine-congestion-points` | `DenyChokepoint(tile)` | ChokepointAnalyzer + TrafficHeatmap |
| `mine-in-front-of-enemies` | `AmbushPath(predictedPath)` | ArenaCongestionField + predicted-velocity extrapolation |
| `bullet-snipe-from-afar` | `Engage(target, fireRange=long)` | sight-line oracle (deferred) |
| `bomb-snipe-from-afar` | `Engage(target, fireRange=long, weapon=BOMB)` | sight-line + splash range |
| `engage` | `Engage(target)` | nearest threat |
| `evade` | `Flee(threat)` | nearest threat + LowEnergy gate |
| `wander` | `Wander()` | none |

### Archetype = behaviour-weight map

An archetype declares which behaviours it considers and how strongly. **No per-archetype scorer subclasses.** The behaviour is the reusable scorer; the archetype is just the weight vector.

```groovy
// In bot-tuning.groovy (slice #08 framework already in place):
archetype 'MinerShark', {
    behaviour 'mine-congestion-points',  weight: 1.0
    behaviour 'mine-in-front-of-enemies', weight: 0.8
    behaviour 'engage',                  weight: 0.3
    behaviour 'evade',                   weight: 0.7
    behaviour 'wander',                  weight: 0.1
}

archetype 'Brawler', {
    behaviour 'engage',  weight: 1.0
    behaviour 'evade',   weight: 0.8
    behaviour 'wander',  weight: 0.2
}
```

Mapped to the typed config (extending ADR-0010's per-archetype config trajectory):

```java
public record ArchetypeConfig(String name, Map<String, Double> behaviourWeights) {}
```

The planner enumerates each behaviour the archetype names, runs `enumerate()` to get candidates, scores `intrinsic × weight` per candidate, picks the global max. **Behaviours not named in the archetype contribute zero candidates** — operators control what the archetype "knows how to do" by listing behaviours.

### Three-tier tuning (per grill response)

Per the grill's explicit "some zone-wide, some arena-wide, plus archetype configs" decision, tuning splits cleanly:

1. **Zone-wide** — `BotBrainSystem` constants that apply to every bot in the zone regardless of arena (planner cadence, stickiness margin, max enumerated candidates per behaviour per tick). Lives in a `zone-bot-ai.groovy` fragment loaded by an `EngineConfigSystem`-style holder.
2. **Arena-wide** — `BotBrainConfig` per arena (existing from slice #08): `perceptionRadius`, `aimConeDegrees`, etc. These apply to every bot in *this* arena.
3. **Archetype-specific** — `ArchetypeConfig` keyed by name: behaviour weights + per-archetype overrides of arena-wide knobs (e.g. MinerShark might want a wider perception radius than Brawler).

Precedence: zone → arena → archetype (later wins). Mirrors `ShipConfig`'s preset → arena → ship-type precedence and ADR-0010's reservation.

### Planner cadence + additive stickiness

The planner runs **every ~500ms** (not per-tick) — `BotBrainSystem` re-selects on a throttled timer. Per-tick selection is the cost trap AND the source of "the bot keeps switching goals mid-action" behaviour bugs.

**Sticky preemption rule (additive, per grill correction)**: a new candidate goal preempts the current goal only if `new.weightedScore > current.weightedScore + STICKINESS_MARGIN` (e.g. additive margin 0.10). The earlier draft used a multiplicative margin (`current × 1.25`), which behaves unevenly across score scales — at low absolute scores the margin shrinks to nothing, at high scores it grows large. Additive is predictable: regardless of the current score, a competing goal needs to beat it by a fixed amount.

Default `STICKINESS_MARGIN = 0.10` (zone-wide tunable). With weighted scores in `[0, 1]`, 0.10 means "10% of the score range firmer than the alternative" — empirically the sweet spot in utility-AI tuning per *Behavioral Mathematics for Game AI*.

**Forced re-select** on: goal completion (BT branch returned SUCCESS terminally), goal failure (BT branch returned FAILURE — per the "goal-validity mid-tick" decision: target despawned → ExecuteEngage returns FAILURE → planner re-selects on next planner tick; the BT does NOT itself re-run the planner inline), goal expiry (goal's `deadlineSeconds` passed), or arena event (round transition, player joined).

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

`IsGoal(...)` is a new Condition leaf — returns SUCCESS when `bb.currentGoal()` matches **exactly that class** (industry-standard exact-class dispatch, per grill resolution). Goal *variants* (e.g. `Engage(target)` vs hypothetical `EngageWithCover(target, coverTile)`) get separate record types and separate BT branches.

`ExecuteMineDeploy` / `ExecuteAmbush` are per-behaviour Action sequences. **Per the grill's "behaviours in Groovy" framing**, each behaviour ships with both an enumerator (Java) AND an executor sequence; the executor is the BT subtree that satisfies one of the behaviour's goals. The executor is currently hand-coded Java (a small Sequence assembled from existing BT primitives: `NavigateTo` + `ArriveAt` + `FireWeapon` etc.); a future Groovy-composition layer for executors is reserved for v2.x+ when the executor authoring rate justifies the DSL cost.

### Layering

- **api/infinity.ai.tactical.*** — `TacticalGoal` sealed interface + standard record subtypes, `Behaviour` interface, `TacticalPlanner` interface, `IsGoal` Condition, `ArchetypeConfig` record. Per [ADR-0005](./0005-layered-architecture.md) data + interfaces only.
- **infinity-server/.../ai/tactical/*** — `TacticalPlannerImpl`, per-behaviour impls (`MineCongestionBehaviour`, `EngageBehaviour`, etc.), per-behaviour `Execute*` Action Sequences.
- **`Blackboard`** (api, extended) — `currentGoal()` accessor + `setCurrentGoal()` setter; `arenaContext()` accessor returning the per-arena `BotAiArenaContext` bundle (cross-cutting decision; see ADR-0011 / ADR-0012).
- **`BotBrainSystem`** (server) — owns the planner's throttle timer; invokes `planner.select()` on cadence; writes result to blackboard. Also injects `BotAiArenaContext` reference into Blackboard at `BrainContainer.addObject` time.

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

### B. Pure GOAP (Goal-Oriented Action Planning a la F.E.A.R.)

**Why considered:** F.E.A.R.'s original GOAP plans sequences of preconditioned actions to satisfy goals via A* over an action graph. Most general; most powerful.
**Why rejected:** **Author cost.** Each action needs precondition + effect declarations; the planner does A* over the action graph at runtime. Our scope (≤10 behaviours per archetype, each with a small hand-authored Execute Sequence) doesn't need action *sequence* synthesis — the sequences are short and hand-authored more clearly than they would be planner-generated. Promote to GOAP if action sequences need dynamic composition; today utility-AI goal selection + per-behaviour Execute Sequences cover the design surface.

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
| Selection mechanism | Named `Behaviour` building blocks (`mine-congestion-points`, `bullet-snipe-from-afar`, etc.) enumerate + intrinsic-score candidates; archetype weight map multiplies score; planner picks max. |
| Archetype shape | `ArchetypeConfig(name, Map<String, Double> behaviourWeights)` — pure data; one record per named archetype. No per-archetype Java subclasses. |
| Cadence | Planner runs every ~500ms; not per-tick. BT continues to tick every frame. |
| Stickiness | **Additive** margin: new goal preempts current only if `weightedScore > current + STICKINESS_MARGIN`. Default `0.10`; zone-wide tunable. (Earlier multiplicative margin draft replaced.) |
| Goal sharing | Goal record types live in `api/infinity.ai.tactical.*`; per-behaviour customization in `Behaviour` impl + Execute Sequence. |
| BT integration | New `IsGoal(GoalClass)` Condition leaf, exact-class dispatch; per-behaviour `Execute*` Action Sequences (hand-coded Java for v2.0). No other new BT semantics. |
| Goal-validity edge | BT branch returns FAILURE when its goal becomes invalid mid-tick (target despawned, path blocked); planner re-selects on next planner tick. BT doesn't run planner inline. |
| Three-tier tuning | Zone-wide (`BotBrainSystem` defaults) → arena-wide (`BotBrainConfig`) → archetype-specific (`ArchetypeConfig`); precedence later-wins. Mirrors `ShipConfig` + ADR-0010. |
| Multi-bot squad | Deferred to v2.x+ per grill. v2.0 single-bot tactical only. |
| Citation correction | NOT GOAP. Pattern is utility-AI goal selection (The Sims, Killzone 2, *Behavioral Mathematics for Game AI*). F.E.A.R. cited only as Alternative B. |

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

- **David Mark, *Behavioral Mathematics for Game AI*** (2009). **Primary reference for this ADR.** Comprehensive treatment of utility-based AI selection — scoring functions, response curves, oscillation, additive stickiness margins. The textbook for this layer.
- **Damian Isla, "Handling Complexity in the Halo 2 AI"** (GDC 2005). Encounter-level tactical reasoning; "behaviour DAG with character context" maps to our (BT + Goal) split.
- **The Sims** (Maxis, 2000+). The "smart objects advertise utility scores; agent picks max" pattern this ADR adopts at the goal layer. Documented in *AI Game Programming Wisdom* vol. 2 (Forbus & Wright).
- **Killzone 2 / Killzone 3** (Guerrilla Games, GDC 2009-2011). Production-shipped utility-AI with named behaviours + per-archetype weights — close to the shape this ADR codifies.
- **Mat Buckland, *Programming Game AI by Example*** (2005). Goal-driven agent architecture chapter; the original C++ reference shape this ADR's interfaces mirror.
- **Jeff Orkin, "Three States and a Plan: The AI of F.E.A.R."** (GDC 2006). The canonical GOAP reference — cited here as Alternative B (action *planning*), **not** the model this ADR adopts (which is goal *selection*).
