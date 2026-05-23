# Bot AI v2 — tactical behaviours, weapon-aware archetypes, spatial reasoning

Status: ready-for-human
Category: enhancement

## Why

v1 (closed by [Bot AI v1 PRD](../bot-ai/PRD.md) slices #01-#08) delivered reactive
behaviours: bots steer, pursue, orbit, fire, evade. They are *functional*
but *characterless* — every bot does the same thing regardless of ship hull
or weapon kit. A Shark behaves identically to a Javelin behaves identically
to a Leviathan, modulo per-arena tuning.

v2 makes bots **tactically character-ful**: weapon-aware, map-aware,
goal-driven. The marquee scenarios (from chat discussion 2026-05-23):

- **Shark-miner** seeks congestion points and plants mines; predicts enemy
  paths and ambushes corridors with mines.
- **Javelin-bouncer** uses bouncing bombs to hit enemies around corners;
  computes bounce paths before firing.
- **Leviathan-AOE** secures cover, finds dense target clusters, lands L3
  splash bombs.

These require three architectural layers v1 doesn't have:
**pathfinding**, **spatial-analysis services**, and a **tactical-goal layer**
that picks named behaviours via utility scoring.

## Architecture anchors

Three ADRs reserve the design space; v2 implements them:

- [ADR-0011](../../docs/adr/0011-bot-navigation-navmesh.md) — Clearance-aware grid A* over `.lvl` tile grid; Floyd LoS smoothing; async per-arena build.
- [ADR-0012](../../docs/adr/0012-bot-spatial-analysis-services.md) — `BotAiArenaContext` bundling `TrafficHeatmap` + `ChokepointAnalyzer` + `ArenaCongestionField`; weapon-specific services (BounceTracer, CoverFinder, MinePlacementScorer, LineOfSightOracle) deferred until first consumer.
- [ADR-0013](../../docs/adr/0013-bot-tactical-goal-layer.md) — Named `Behaviour` building blocks (`mine-congestion-points`, `bullet-snipe-from-afar`, etc.); archetypes = weight vector over behaviour names; utility-based goal selection with additive stickiness; three-tier tuning (zone / arena / archetype).

Also extending:
- [ADR-0010](../../docs/adr/0010-bot-composition-dsl.md) — Per-arena `bots { ship 'X', archetype: 'Y' }` block (implemented in Slice #01).
- [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md) — v1 substrate; v2 adds layers above the existing BT, doesn't replace it.

## User stories (informally numbered)

1. As an operator, I can declare which ship hulls spawn as bots in an arena (Slice #01).
2. As an operator, I can map ship hull → archetype name in an arena (Slice #01).
3. As a bot, I can navigate to a remote tile through corridors, not just react to immediate walls (Slices #02 + #03).
4. As an operator, I can declare a new archetype by listing named behaviours + weights in Groovy (Slice #02).
5. As a bot author, I can introduce a new tactical behaviour without forking the brain — define a `Behaviour` impl + register in the registry (Slice #02 establishes the contract; every behaviour slice exercises it).
6. As a Shark bot, I camp at chokepoints (Slice #04).
7. As a Brawler bot in a busy arena, I drift toward where the action is (Slice #05).
8. As a Leviathan bot, I find target clusters and splash them (Slice #06).
9. As a Shark bot, I plant mines at congestion points AND ambush predicted enemy paths (Slice #07).
10. As a Javelin bot, I fire bouncing bombs around corners (Slice #08).
11. As a bot-AI designer, I can see what each bot is thinking — current goal, scored behaviours, current path — in the debug HUD (Slice #09).

## Out of scope for v2.0

- **Multi-bot squad coordination** ("Shark-1 mines while Shark-2 distracts"). v2.x+.
- **Online learning / weight tuning.** Weights are operator-authored Groovy. v3+.
- **Wormhole-aware pathfinding.** Bots that hit warp tiles get translated by physics (existing behaviour); the planner doesn't model warp edges. v2.x+.
- **Door-state event subscription.** Cached paths re-validate the current waypoint each tick; full event-driven invalidation deferred. v2.x+.
- **Goal *sequence* synthesis (full GOAP).** v2 selects ONE goal per planner tick; goal-satisfaction is a hand-authored BT Sequence. Promote if the authoring rate of new behaviours warrants the planner-graph cost.

## Archetype-design step-back

**Surfaced 2026-05-23 during the post-/to-issues review.** Slices #04, #06,
#07, #08 each invent a new archetype (`ChokeCamper`, `LeviathanSetup`,
`MinerShark`, `JavelinBouncer`) as a side-effect of demoing a spatial
service or behaviour. That's the wrong direction — archetypes are the
user-facing artifact (the answer to "what kind of bot is this?"); they
warrant deliberate roster design, not emergence-from-infrastructure-slices.

Questions a dedicated archetype-design workstream should settle before
slices #04 / #06 / #07 / #08 can be re-scoped to `needs-triage`:

- **Roster.** Which archetypes ship in v2.0? The three chat-thread
  scenarios (MinerShark, JavelinBouncer, LeviathanSetup) plus the v1
  Brawler default? Or also a Defender / FlagCarrier / Bomber /
  AggressiveTerrierEscort / etc.?
- **Composition.** For each archetype: which named behaviours, at what
  weights, given which ship hull's weapons. Independent of which spatial
  service each behaviour happens to need.
- **Discovery.** How does an operator learn which archetypes exist?
  Registry exposure (auto-loaded list); default arena rosters; HUD
  surfacing; documented catalog?
- **Per-archetype tuning.** Shared `BotBrainConfig` knobs vs
  archetype-specific overrides — does `MinerShark` want a wider perception
  than `Brawler`? (ADR-0013 reserves the three-tier shape; archetype
  design fills in the concrete overrides per archetype.)
- **Ship-hull-to-archetype mapping defaults.** Should the registry ship
  a recommended `Ship.SHARK → MinerShark` default, or are all assignments
  arena-author-controlled?
- **Future expansion path.** When v2.x adds a new archetype, what's the
  workflow? New `Behaviour` + new `ArchetypeConfig` entry + arena.groovy
  reference — sufficient, or does the registry need richer machinery?

**Recommended form for the workstream:** new ADR (0014?) covering the
archetype roster + per-archetype composition + discovery + tuning
defaults. Alternatively, a dedicated section in this PRD with the same
content. Decision lands when the workstream kicks off.

**Until that workstream completes, slices #04 / #06 / #07 / #08 sit at
`needs-info`.** Slices #01 / #02 / #03 / #05 / #09 are pure infrastructure
+ Brawler-extending and can proceed independently.

## Implementation slices

See `.scratch/bot-ai-v2/issues/01-09.md`. Vertical tracer bullets — each slice
demos a complete tactical pattern through nav + spatial + tactical + Groovy
end-to-end.

## Done definition

- All 9 slices land + per-slice acceptance criteria met
- ADRs 0011/0012/0013 status flips to Accepted (or partial-acceptance note if any sub-decision is still TODO)
- `BotDebug` HUD surfaces v2 state (goal + scores + path)
- One archetype per chat-thread scenario shipped + live-verified: MinerShark, JavelinBouncer, LeviathanSetup
