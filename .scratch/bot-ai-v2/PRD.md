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

**Substrate revised 2026-05-23; slices re-cut 2026-05-24.** The original v2 ADR set (0011 grid A*, 0012 service suite, 0010 authored archetypes) was reviewed against the actual game shape (2D top-down momentum physics, fast TTK, sparse obstacles, 8 ships × per-zone tuning) and substantively reshaped. The original 9 slices were authored against that pre-revision substrate. They have now been **re-cut into 8 substrate slices (substrate-first)** to match the revised ADRs — flow fields replace A*, scalar fields replace the service suite, and two new substrate slices land ADR-0014 (capability derivation) and ADR-0015 (arena objectives + roles) which the original 9 did not cover. The **behaviours** that the original slices invented as side-effects (camp, mine, splash, bounce) were extracted into a dedicated [behaviour catalog](../bot-behaviour-catalog/PRD.md) ([ADR-0016](../../docs/adr/0016-bot-behaviour-catalog.md)) — one issue per behaviour, cut on the capability axis, not the ship axis.

Revised substrate (six ADRs):

- [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md) — v1 substrate; v2 adds layers above the existing BT. **Amended:** BT is engineer-authored only; zone/arena admins tune numeric knobs.
- [ADR-0010](../../docs/adr/0010-bot-composition-dsl.md) — Per-arena `bots { }` block. **Superseded in part by ADR-0014:** authored weights → capability-derived; `tweak: [...]` overlay replaces `weights:`.
- [ADR-0011](../../docs/adr/0011-bot-navigation-navmesh.md) — **Revised:** flow fields per goal tile (Dijkstra-derived gradient sampled by steering layer). Replaces grid A* + waypoint following; better fit for momentum physics.
- [ADR-0012](../../docs/adr/0012-bot-spatial-analysis-services.md) — **Revised:** `ScalarField` / `GradientField` / `FieldBlend` as the unified primitive. Concrete fields (DistanceField, ThreatField, OpportunityField, CombatDensityField, AllyDensityField, EnemyDensityField) are specializations. CoverFinder / LineOfSightOracle dropped as services; ChokepointAnalyzer reshaped as load-time tile-list producer.
- [ADR-0013](../../docs/adr/0013-bot-tactical-goal-layer.md) — Tactical-goal layer (utility scoring + additive stickiness). **Amended:** planner cadence default ~150ms (was 500ms) for fast-combat zones; slow-game-mode zones override.
- [ADR-0014](../../docs/adr/0014-capability-derived-bot-composition.md) — **New (2026-05-23):** Behaviour weights derive from `CapabilityProfile` × engine-authored `synergy { }` table × per-arena `tweak: [...]` overlay. Resolves the archetype-design step-back below.
- [ADR-0015](../../docs/adr/0015-arena-objective-and-roles.md) — **New (2026-05-23):** Mechanic modules produce `ArenaObjective` (KOTH, CTF, Hockey, Powerball, Turf, Deathmatch) with per-bot `BotRole` assignment. Closes the "what is this arena trying to win at?" gap that pure capability derivation cannot express.

## User stories (informally numbered)

1. As an operator, I can declare which ship hulls spawn as bots in an arena (Slice #01).
2. As an operator, I can nudge a hull's behaviour with a per-arena `tweak: [...]` overlay without re-authoring the whole weight vector (Slice #01 + #04).
3. As a bot, I can navigate to a remote tile through corridors via a flow field, not just react to immediate walls (Slices #02 + #03).
4. As a bot, my behaviour weights are *derived* from my ship's capabilities (a mines+cloak Shark plays differently from a Warbird) with no authored roster (Slice #04).
5. As an engine author, I can introduce a new tactical behaviour without forking the brain — define a `Behaviour` impl + its `synergy { }` line (Slice #05 establishes the contract; every behaviour slice exercises it).
6. As an operator, my arena's gametype biases bot behaviour and assigns per-bot roles — KOTH bots hold the king tile, CTF bots defend/attack the flag (Slice #06).
7. As a bot in a busy arena, I drift toward where the action is (Slice #07).
8. As a bot-AI designer, I can see what each bot is thinking — effective-weight breakdown, current goal, scored behaviours, nav mode — in the debug HUD (Slice #08).

(Behaviour-specific stories — "a mines hull plants mines at congestion points," "a splash hull anchors and bombs clusters," "a bouncing-bomb hull fires around corners" — belong to the [behaviour catalog](../bot-behaviour-catalog/PRD.md), not here. This PRD delivers the substrate those behaviours need.)

## Out of scope for v2.0

- **Multi-bot squad coordination** ("Shark-1 mines while Shark-2 distracts"). v2.x+.
- **Online learning / weight tuning.** Weights are operator-authored Groovy. v3+.
- **Wormhole-aware pathfinding.** Bots that hit warp tiles get translated by physics (existing behaviour); the planner doesn't model warp edges. v2.x+.
- **Door-state event subscription.** Cached paths re-validate the current waypoint each tick; full event-driven invalidation deferred. v2.x+.
- **Goal *sequence* synthesis (full GOAP).** v2 selects ONE goal per planner tick; goal-satisfaction is a hand-authored BT Sequence. Promote if the authoring rate of new behaviours warrants the planner-graph cost.

## Archetype-design step-back

**Resolved 2026-05-23 by [ADR-0014](../../docs/adr/0014-capability-derived-bot-composition.md).** The deliberate-roster workstream is replaced by capability-derived composition: each bot's behaviour weight vector is derived from its ship's `CapabilityProfile` (a normalized read of `ShipConfig`) crossed with a zone-tier Groovy `synergy { }` table. Slices #04/06/07/08 unblock — they no longer invent named archetypes; each demos one spatial service or weapon-specific behaviour, with the "what bot is this?" answer falling out of derivation. Named identities (`MinerShark` / `JavelinBouncer` / `LeviathanSetup`) are deferred to v2.1 pending first-playtest feedback on whether derived bots feel distinct enough on their own.

### Original framing (preserved for context)

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

**Outcome (2026-05-24):** the workstream landed as [ADR-0014](../../docs/adr/0014-capability-derived-bot-composition.md) (capability-derived composition) rather than a hand-authored roster. The four previously-frozen behaviour slices are unfrozen and re-cut as #08 (camp-chokepoint), #09 (mine behaviours), #10 (Leviathan splash), #11 (bounce shot) — each adds a `Behaviour` impl + its `synergy { }` line, with "what kind of bot is this?" falling out of derivation. No slice sits at `needs-info` any longer.

## Implementation slices

This PRD is the bot-AI v2 **substrate** — the planner, capability derivation, navigation, spatial fields, objectives, and observability. The **behaviours** that ride on it are a separate, longer-lived workstream: see the [bot behaviour catalog PRD](../bot-behaviour-catalog/PRD.md) ([ADR-0016](../../docs/adr/0016-bot-behaviour-catalog.md)). The Phase-1 catalog behaviours unblock once slices #05 + #07 land.

See `.scratch/bot-ai-v2/issues/01-08.md`. Vertical tracer bullets, dependency-ordered (substrate-first):

| # | Slice | ADR | Depends on | Status |
|---|---|---|---|---|
| 01 | `bots { }` block + multi-ship spawn (tweak overlay) | 0010/0014 | — | ✅ done (e9861a40) |
| 02 | `BotAiArenaContext` scaffold + flow-field tracer | 0011/0012 | — | ✅ done (db90765e) |
| 03 | Production flow-field nav (async, cache, doors) | 0011 | 02 | ⏸ deferred — needs MapSystem passability seam |
| 04 | **Capability-derivation pipeline** | 0014 | (02) | ✅ done (49a0dcd0 + #05 wiring) |
| 05 | `TacticalPlanner` + baseline behaviours | 0013 | 04, 02 | ✅ done (24db369f) |
| 06 | **Arena objectives + roles** | 0015 | 03, 04, 05 | ⬜ not started |
| 07 | Dynamic scalar fields + chokepoints + follow-traffic | 0012 | 03, 04, 05 | ⬜ in progress |
| 08 | `BotDebug` HUD v2 extension (AFK) | 0013/0014/0015 | 05 | ✅ done (7c028117) |

The behaviour roster (engage, area-denial, mine behaviours, splash, bounce-shot, …) lives in the [behaviour catalog](../bot-behaviour-catalog/), one issue per behaviour — **not** as v2 slices. This keeps the substrate PRD bounded and stops behaviours being invented as a side-effect of substrate work.

## Done definition

- All 8 substrate slices land + per-slice acceptance criteria met
- ADRs 0011/0012/0013/0014/0015 status flips to Accepted (or partial-acceptance note if any sub-decision is still TODO)
- `BotDebug` HUD surfaces v2 state (effective-weight breakdown + goal + scores + nav mode)
- The substrate proves out end-to-end on the **Phase-1 catalog behaviours** (engage / harass / disengage / area-denial / hold-position / flank): capability-derived bots are demonstrably distinct by hull + objective with no per-arena authoring, and KOTH/CTF objectives bias roles. **Named archetype presets are explicitly out of scope** — deferred to v2.1 per ADR-0014.
