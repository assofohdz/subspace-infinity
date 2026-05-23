# ADR backlog — potential future ADRs

Tracks architectural decisions that exist *implicitly* in the codebase but aren't yet formalized as ADRs. **Not a queue of "things to do."** Items here become draftable when (a) churn / change pressure surfaces the implicit decision, or (b) someone questions the existing shape in a review and needs the rationale.

The current series ([`docs/adr/`](../docs/adr/) 0001–0007) covers decisions that have been actively contested or migrated through. The backlog below is "wider-lens completeness" — areas where the *code does something* but the *why* is implicit. None are urgent.

Identified from a wider-lens architectural pass on 2026-05-13 (Nygard-style: "would someone reading this in six months wonder why?").

## Candidates

### Persistence strategy

- **Implicit today.** A `world.db` directory exists at the project root. Some state survives restarts, some doesn't. The boundary is not documented.
- **What an ADR would settle:**
  - What state is *durable* (account credentials, arena history, achievements?) vs *ephemeral* (live entities, in-flight projectiles, transient buffs).
  - Storage engine choice (currently SQLite via what driver? Why?).
  - Schema-migration story when fields evolve.
  - How persistence interacts with hot-reload — e.g. `ConfigRegistry` snapshots are in-memory; should there be a persisted last-known-good snapshot?
- **Trigger to draft.** First time someone proposes adding new persistent state and "should this be durable?" doesn't have an obvious answer. Also: if modules ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) need their own persistence, the trust-model + storage decisions compound.

### Authentication / accounts / identity flow

- **Implicit today.** `AccountHostedService` + `AccountEvent` ([ADR-0003 Channel B](../docs/adr/0003-communication-channels.md) publish sites). Session-to-player binding lives in code but no architectural decision document.
- **What an ADR would settle:**
  - Identity model: per-installation, per-account, guest, mixed?
  - Trust level differences between authenticated and unauthenticated sessions.
  - Module + identity intersection ([ADR-0004](../docs/adr/0004-settings-pipeline.md)): a module's server systems run with what identity? Whose actions does a module-emitted intent component carry?
  - Credentials storage (intersects the Persistence candidate above).
  - Login / logout lifecycle and the surface area for cheating.
- **Trigger to draft.** When modules ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) need identity-aware behavior (e.g. a `ScoringRule` tracking per-player stats), the implicit identity flow becomes load-bearing. Also: if anti-cheat work surfaces a concrete attack vector that the current identity model doesn't address.

### Zone-tier composition story

- **Implicit today.** [ADR-0008](../docs/adr/0008-arena-composition-and-modules.md) defined per-arena composition (`ArenaModule`, lifecycle, 4-phase tick, coordinator pattern). The *zone* tier — multi-arena structure: auth, accounts, admin, master-server, multi-arena lobbies, persistence boundaries, identity flow, the `~swapMap` / `~loadArena` command surface — has no analogous composition story. Today these are individual `HostedService` / `BaseInfinitySystem` classes wired into `GameServer` directly, with no pluggability framework.
- **What an ADR would settle:**
  - Are zone-level concerns compositional (analogous to ArenaModules) or monolithic infrastructure?
  - If compositional: what categories? Auth providers (file / external billing / VIE-bot), account stores (in-memory / SQLite / external), admin command surfaces (built-in / extension), lobby behaviours, league/tournament shells.
  - The identity-flow + persistence + module-trust intersections that the existing *Authentication* and *Persistence* backlog entries already gesture at — a zone-tier composition ADR would likely supersede or fold those.
  - Where the boundary sits with [ADR-0008](../docs/adr/0008-arena-composition-and-modules.md): some concerns (squad, league, cross-arena stats) are zone-tier even though they touch gameplay; this ADR would clarify.
- **Trigger to draft.** When cross-arena features land (league play, tournament shells, global stats, persistent squads); or when auth/admin needs significant rework; or when the user explicitly asks for the grill-with-docs design exercise that mirrors ADR-0008 at the zone tier. *"Arenas are the game. Zone is the structure"* — flagged 2026-05-15.

### Engine-tier composition story

- **Implicit today.** Below the arena tier sits the sim substrate — physics (Moss today), tick loop, coordinate system, telemetry sinks, the `engine.groovy` config tier (see [`.claude/rules/`](../.claude/rules/) for `feedback-engine-tier-for-physics-constants` semantics). These are *monolithic infrastructure*: every arena and zone gets the same physics, the same tick frequency, the same coordinate system. No pluggability framework today.
- **What an ADR would settle:**
  - Is anything at the engine tier genuinely compositional, or is it all load-bearing universal substrate? (Likely the latter, but worth verifying via grilling rather than assuming.)
  - If anything is compositional: physics flavour swapping (Moss-Box vs alt), telemetry sink composition (console / file / external metrics), tick-loop variants (fixed-step vs variable, lockstep vs free), coordinate-system extensions.
  - Where `engine.groovy` config-tier knobs ([`feedback-engine-tier-for-physics-constants`](../.claude/rules/) in agent memory) stop and "engine modules" begin — i.e. what's tuning vs what's swap.
- **Trigger to draft.** When proposing an alt-physics path; when the tick loop needs to be pluggable for a specific use case (e.g. deterministic replay); when the engine grows enough complexity that the monolith assumption breaks; or when the user explicitly asks for the grill-with-docs design exercise at the engine tier.

### Bot navigation / pathfinding

- **Implicit today.** [ADR-0009](../docs/adr/0009-bot-ai-architecture.md) explicitly defers pathfinding for v1: open Subspace arenas are 2D fields where steering-layer `AvoidObstacles` (reactive corridor projection) + perception via mphys `BinIndex` (dynamic targeting) cover the navigation surface adequately. There is no `infinity.ai.nav.*` package today.
- **What an ADR would settle:**
  - **Representation.** Polygonal navmesh (mesh-from-tile-grid at arena load) vs grid A* directly against the `.lvl` tile array. Subspace maps are tile grids natively; grid A* is structurally simpler but produces blockier waypoints and grows fast at unit-cell resolution.
  - **Layered insertion.** Where pathfinding slots into the bot AI stack: a new api-tier `infinity.ai.nav.*` package (NavMesh / Path / PathPlanner / PathFollower); a new BT `FollowPath` Action that drives `Arrive(nextWaypoint)` via the existing steering layer; `AvoidObstacles` continues to run in parallel via `BlendedSteering` for dynamic local obstacles (ships). No replacement of steering — pathfinding feeds it.
  - **Per-arena vs zone-global.** NavMesh data is per-arena (mesh structure derived from the `.lvl`); planner instances cache per-arena. Decide whether the cache lives in the arena module lifecycle ([ADR-0008](../docs/adr/0008-arena-composition-and-modules.md)) or in a zone-global `NavMeshService` keyed by `ArenaId`.
  - **Re-plan policy.** When to re-plan vs follow the cached path: target moves > threshold, path becomes blocked (door closes), bot reaches a waypoint, every N ticks as a sanity floor. Re-planning every tick is the cost trap; cache + event-triggered replan is standard.
  - **Dynamic-obstacle integration.** Ships are not in the navmesh. Decide whether bots treat enemy ships as moving obstacles in the planner (expensive — full replan when any threat moves) or rely entirely on `AvoidObstacles` for local reactive avoidance (cheap; what ADR-0009 does today).
- **Trigger to draft.** First closed-corridor / multi-room arena design lands where `AvoidObstacles` is structurally insufficient — a flag-room with one entrance, a maze, a base-defense layout with chokepoints. Triggers when a player-visible "the bot got stuck pushing against a wall trying to reach me" bug surfaces, OR when an upcoming gametype (CTF, base-rush, dungeon-style) explicitly needs route-aware AI. ADR-0009 commits to re-opening the decision as a follow-up ADR at that point.

### Wire-compatibility / component-shape migration policy

- **Implicit today.** Component shapes evolve in `api/src/main/java/infinity/es/...` as gameplay needs change. Some are wire-crossing (synced to client via Zay-ES `FieldSerializer`); changes to those fields must coordinate server + client + (future) modules. Coordination is currently negotiated per change, with no rule for "when can a component shape change without staged rollout?"
- **What an ADR would settle:**
  - Which component-shape changes are wire-breaking vs wire-compatible (add field with default? rename? reorder?).
  - Staged-rollout policy for breaking changes (deploy server first? client first? require version bump?).
  - How module-defined components ([ADR-0004](../docs/adr/0004-settings-pipeline.md)) interact with the manifest-hash handshake when their shape changes.
  - Whether `Serializer.registerClass` registration order matters for cross-version compatibility.
- **Trigger to draft.** First time a wire-component shape needs to change after a client-vs-server version skew is realistic (e.g. dedicated-server deployments where the client is on a release tag and the server isn't). Or when modules ship and their component shapes need versioning.


## How items leave this backlog

- **Become a drafted ADR** when the trigger condition above is met.
- **Get folded into another ADR** if the implicit decision turns out to be covered by an adjacent area (e.g. *Persistence* might fold into [ADR-0004](../docs/adr/0004-settings-pipeline.md) if the durable-state model ends up `zone/`-driven; *Asset pipeline* probably folds the same way).
- **Get deleted** if the implicit decision is really a non-decision ("we don't do this" rather than "we do X").

## What this list is NOT

- **Not a TODO list.** Drafting an ADR without a real consumer is the same YAGNI trap that motivated the `modules/` subproject deletion. The series risks becoming theatre if entries land here just to look complete.
- **Not a survey of every implicit decision.** Many implicit decisions are fine implicit — language choice, code style, library minor versions. The list above is "implicit decisions someone might reasonably question," not "all decisions that exist."
- **Not architecturally urgent.** Infinity's existing ADR coverage is unusually thorough for a hobby-scale game project (7 ADRs is high-end). The backlog is wider-lens completeness, not a deficit.

## See also

- [`docs/adr/`](../docs/adr/) — landed ADRs (0001–0007).
- [Joel Parker Henderson's ADR examples repo](https://github.com/joelparkerhenderson/architecture-decision-record) — catalog of example ADR topics from other projects for comparison.
