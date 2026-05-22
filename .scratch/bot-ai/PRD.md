# Bot AI v1 — combatant brains driving `BotShip` entities

Status: ready-for-human
Category: enhancement
Date: 2026-05-22
Anchor: [ADR-0009 — Bot AI Architecture](../../docs/adr/0009-bot-ai-architecture.md)

## Problem Statement

A player joining an FFA, KOTH, or any `FillUpXTeams`-populated arena expects opposition: ships pursuing, firing, dodging, flocking with allies. Today the arena is silent — bot ships spawn at the centre point and sit motionless. The KOTH smoke test on 2026-05-14 surfaced this: a non-fighting bot is worse than no bot. From the player's perspective the arena feels empty, scoring is hollow, and the gameplay loop the arena was designed around does not run.

Technically, `AIEntities.createMobShip` stamps `BotShip` + `CharacterInput` at spawn and walks away — nothing drives `CharacterInput` after the spawn-time empty stamp. The legacy `infinity.ai.*` framework (Brain / Goal / Strategy / Actor / `MobDriver`) was ported from a Mythruna chicken-demo in 2021 and drives physics `RigidBody` directly via `MobDriver.AbstractControlDriver` — a path `BotShip` entities never touch.

## Solution

Bot ships acquire enemies, steer with thrust and rotation up to their ship's stat caps, fire bullets and bombs with lead-prediction, evade when energy is low, and flock with allies — using **the same input shape and physics driver as human players** (`MovementInput` → `PlayerDriver`). Server-side rate limits (rotation rate, thrust cap, fire delay, energy gating) are enforced by the same code that handles player input; there is no "AI fast path." If a bot looks more responsive than a player it is because its decision loop has no round-trip latency, not because the input layer skipped a check.

Architecture is set by [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md): hand-rolled three-layer stack — steering primitives → Behaviour Tree framework → composed brain archetypes — driven by a single `BotBrainSystem` at the ECS boundary. v1 ships one archetype (`Brawler`) tuned for FFA/KOTH-style combat. Future archetypes plug in via Groovy fragment + named-archetype factory without forking core AI code.

## User Stories

1. As a player joining an FFA arena, I want bots to pursue and fire at me, so that the arena has opposition.
2. As a player joining a KOTH or team arena, I want bots on the opposing team(s) to fight my team, so that the team-vs-bots scenario the arena is designed around actually runs.
3. As a player approaching low energy, I want enemy bots to predictably pressure me, so that energy management is a meaningful decision.
4. As a player chasing a bot, I want the bot to attempt evasion when its own energy is low, so that combat reads as reactive rather than scripted.
5. As a player firing at a moving bot, I want my shots to hit only with correct lead and aim, so that bots feel skill-fair (no insta-dodge teleports, no perfect-prediction aim assist).
6. As a player flying near allied bots, I want them to maintain spacing without colliding into each other or me, so that the team formation feels cohesive.
7. As a player observing bot behaviour over time, I want bots to obey the same physics + weapon cooldown rules as players, so that nothing feels like "AI cheating."
8. As a player switching ships mid-arena, I want bots' difficulty to track their ship type (a bot in a Warbird should turn faster than a bot in a Shark, just like a player would), so that ship balance applies symmetrically.
9. As an arena operator running a KOTH-style mode, I want bot counts that scale with active player count, so that a 1-player arena and a 16-player arena both feel populated.
10. As an arena operator, I want to choose the bot archetype per arena (default `Brawler`; future `Duelist`, `Defender`, `PrizeGrabber`), so that different modes can have different bot personalities.
11. As an arena operator, I want to tune perception, engagement range, evade-energy threshold, and flocking weights from Groovy, so that I can rebalance without recompiling.
12. As a future module author writing a new gametype that needs custom AI behaviour, I want to ship a brain archetype as a Groovy fragment + named factory, so that my mode has appropriate AI without forking `BotBrainSystem`.
13. As a future module author, I want to compose api-tier steering primitives and BT nodes from my module code, so that I can build custom behaviours without depending on server internals.
14. As a developer extending the v1 `Brawler` to a `Duelist` archetype, I want to add a new BT sub-tree (e.g. fake-out + counter) without restructuring existing branches, so that one archetype's evolution does not destabilise others.
15. As a developer adding a new steering primitive, I want to unit-test it in isolation with pure `Vec3d`/`Quatd` inputs, so that I can verify the math (lead-prediction, arrive-smoothing, AvoidObstacles corridor projection) without an ECS fixture.
16. As a developer auditing architecture invariants, I want `CanonicalWriterTest` to assert that `BotBrainSystem` is the only writer of `MovementInput` on `BotShip`-marked entities and that `CharacterInput` is never stamped on a `BotShip`, so that the input-parity guarantee from ADR-0009 holds at compile-time.
17. As a developer reading the code six months later, I want to find Reynolds steering primitives, BT primitives, and brain archetypes all under `infinity.ai.*` in api/, so that the layered architecture matches the ADR.
18. As a developer running architectural review, I want the legacy chicken framework moved to `infinity.ai.legacy.*` with `@Deprecated`, so that new code lands at the canonical `infinity.ai.*` namespace without colliding.

## Implementation Decisions

Architecture is anchored by [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md); this PRD does not re-derive it.

**api/-tier modules (new):**

- **Steering primitives** (`infinity.ai.steer.*`) — `Steering` interface + 12 implementations: `Seek`, `Pursue`, `Evade`, `Arrive`, `Wander`, `AvoidObstacles`, `OrbitTarget`, `Separation`, `Cohesion`, `Alignment`, `PrioritySteering`, `BlendedSteering`. Pure `Vec3d`/`Quatd` math. No ECS deps. Reynolds-1999 + Millington-2019 are the reference shapes.
- **Behaviour Tree framework** (`infinity.ai.bt.*`) — `Behavior` base + `Status` enum (Success/Failure/Running) + composites (`Selector`, `Sequence`, `Parallel`) + decorators (`Inverter`, `Cooldown`, `Timeout`, `Repeater`, `UntilFailure`) + `Condition` + `Action` leaf interfaces.
- **Brain composition** (`infinity.ai.brain.*`) — `BrainArchetype` factory interface (named-registry pattern), `Blackboard` per-bot scratchpad value, `CombatantBrain` v1 archetype shipping the `Brawler` BT shape.
- **Perception contract** (`infinity.ai.Perception` + `PerceptionSnapshot`) — interface returning nearby threats / allies / projectiles within a radius, filtered by team / alive / arena.
- **`BotBrainConfig`** (`infinity.config.BotBrainConfig`) — `*Config` record per [ADR-0002](../../docs/adr/0002-config-component-projection.md). Tunables: perception radius (defaults to ship's `RadarRange` stat per input-parity — see Further Notes), engage range, evade-energy threshold, lead-prediction time, wander cadence, flocking weights, fire-range per weapon.
- **`BotBrain`** (`infinity.es.BotBrain`) — server-only ECS component holding the brain instance + blackboard. Not wire-crossing; clients see results via normal SimEthereal sync of `MovementInput` + projectile spawns.

**Server-tier modules (new):**

- **`BotBrainSystem`** (`infinity.ai.BotBrainSystem`) — `BaseInfinitySystem`. Owns `EntitySet<BotShip>`. Per tick: gather perception, tick each bot's BT, write `MovementInput`, emit `FireRequest` Change-entity intents. **Canonical writer** of `MovementInput` on `BotShip` entities (disjoint-entity-set with the existing player input writer).
- **`PerceptionService`** (`infinity.ai.PerceptionService`) — implements api-tier `Perception`. Wraps mphys `BinIndex` broadphase + per-tick filtering. Caches per-bot snapshots within a tick to avoid redundant queries.
- **`GroovyBotBrainLoader`** (`infinity.settings.GroovyBotBrainLoader`) — settings-pipeline adapter ([ADR-0004](../../docs/adr/0004-settings-pipeline.md)). Reads `bot-tuning.groovy` fragment, produces `BotBrainConfig` records into `ConfigRegistry`.

**Data + config (new):**

- **`bot-tuning.groovy`** — Groovy fragment under `zone/conf/<preset>/`. v1 defines the `Brawler` archetype block; future archetypes (`Duelist`, `Defender`, …) are siblings.

**Modify (existing):**

- `AIEntities.createMobShip` — switch from `CharacterInput` stamp to `MovementInput` stamp; stamp `BotBrain` reading archetype name (default `"Brawler"`) from `BotBrainConfig` template.
- `FillUpXTeams` — pass an archetype name to `AIEntities.createMobShip`.
- Existing `infinity.ai.*` chicken framework — move whole package → `infinity.ai.legacy.*`, mark `@Deprecated` on the public surface. Keep compiling; deletion is a separate follow-up PRD once v1 stabilises.

**Schema / API contracts:**

- `MovementInput` writer on `BotShip` entities is `BotBrainSystem`; writer on non-`BotShip` ships is the existing player input path. Disjoint-entity-set canonical writers per ADR-0009.
- `FireRequest` Change-entity intents drained by `WeaponsProjectileSpawnSystem` — bots fire by the same mechanism human ships do.
- `BotBrainConfig` template projects to `BotBrain` component at spawn (`BotBrainSystem` reads the archetype name, looks up the template, projects).

## Testing Decisions

Good tests for this PRD assert **external behaviour** — observable input → observable output through the public interface — rather than implementation details. The pure-math + pure-logic split in the layered architecture makes this natural: most modules are testable as functions, not as ECS-coupled fixtures.

**Modules with unit tests in scope for v1:**

- **All 12 steering primitives** — input `Vec3d`/`Quatd` kinematic state + target, output `(yaw, thrust)` tuple. Each primitive gets focused tests for its math (e.g. `Pursue` lead-prediction correctness at varying target velocities; `Arrive` deceleration profile; `AvoidObstacles` corridor projection edge cases; `BlendedSteering` weighted sum across multiple sub-behaviours). Cheap, load-bearing, no fixtures needed.
- **BT framework composites + decorators** — `Selector`/`Sequence`/`Parallel` with mock Condition/Action leaves; decorators (`Cooldown`, `Timeout`, `Repeater`, `UntilFailure`) with mocked SimTime; assert Success/Failure/Running propagation under representative inputs.
- **`CombatantBrain` integration** — wire the v1 BT against mock `Steering` + mock `Perception`; assert state transitions: engages when target in weapon range, evades when energy below threshold, falls back to wander when no target perceived, re-acquires when target enters perception again.
- **`GroovyBotBrainLoader`** — assert Groovy fragment → `BotBrainConfig` record produces the expected typed values. Matches the existing `GroovyShipLoader` / `GroovyEngineLoader` test shape.

**Architectural test extension:**

- Extend `CanonicalWriterTest` (or sibling test) to assert (a) `MovementInput` on `BotShip`-marked entities has exactly one writer (`BotBrainSystem`); (b) `CharacterInput` is never stamped on a `BotShip`-marked entity.

**Prior art for tests:**

- `GroovyShipLoader` tests — fragment-to-`ShipConfig` shape; mirror for `BotBrainConfig`.
- `CanonicalWriterTest` (architectural-review P1-a) — existing 32-component writer registry; extend with the `MovementInput`-on-`BotShip` row.
- Pure-math tests in `simsilica-deps/SimMath` and Moss `mphys` — `Vec3d`/`Quatd` test shape to mirror.

## Out of Scope

- **Pathfinding (A* / navmesh).** Subspace arenas are open 2D fields; steering-layer `AvoidObstacles` is sufficient for static walls. Revisit when a closed-corridor arena mode appears.
- **GOAP / strategic AI / team-coordination AI.** Moment-to-moment combat is BT; strategic role-assignment / objective-routing is a future layer that calls GOAP and feeds a BT. Deferred until concrete demand.
- **Additional brain archetypes beyond `Brawler`.** v1 ships `Brawler` only (triage decision 2026-05-22). The BT shape supports siblings without code changes; Duelist / Defender / PrizeGrabber are clean follow-ups.
- **BT live debugger / visualiser.** Useful for tuning; defer until tuning fatigue motivates it.
- **Deletion of `CharacterInput` / `UprightDriver` / `MobContainer` path.** Follow-up to chicken-framework retirement (which is itself a follow-up PRD after v1 stabilises). v1 leaves the legacy path compiling.
- **End-to-end spawn-projection integration tests.** Pattern-4 / spawn / projection refactors currently have no automated harness; bot AI inherits that gap. Building the harness is its own PRD scope — flagged as a parallel work item, not bundled here. v1 verifies bot behaviour via manual arena launch.
- **Client-side bot rendering changes.** Bots already render as ships (they are ships). No client work in scope.

## Further Notes

**Perception radius = ship's `RadarRange` stat (input-parity extends to perception).**
A bot's default perception radius is the `RadarRange` of the ship type it's flying — same as what a human pilot would see on radar. A bot in a Warbird perceives at Warbird-radar distance; a bot in a Stealth Lancaster does not see further than its (likely smaller) radar. This forecloses the "AI sees through fog of war" cheating pattern. `BotBrainConfig.perceptionRadius` is an override only — defaults to `null`, meaning "read from ship's `RadarRange` stat at spawn."

**Reading reference — game-AI canon:**

- Craig Reynolds. *Steering Behaviors for Autonomous Characters* (GDC 1999, red3d.com/cwr/steer/) — the source for all 12 steering primitives.
- Ian Millington. *AI for Games* (3rd ed., 2019). Chapter 3 (movement / steering) + Chapter 5 (decision-making / BT framework). Closest available textbook.
- Damian Isla. *Handling Complexity in the Halo 2 AI* (GDC 2005) — the BT shape this PRD's brain layer takes.
- *Game AI Pro* volumes 1–3 (free PDFs at gameaipro.com) — production BT chapters from Halo, FEAR, Killzone, Tomb Raider, Civ.

**Subspace canonical knobs.**
Bot-specific tunables are Infinity-native; they do not have Subspace canonical equivalents (Subspace's bots were either MERVBots / Continuum bots / TWCore Java bots — different architectures). Ship stat consumption (`ThrustStats.max`, `RotationStats.max`, `RadarRange`, fire-delay knobs) is canonical and flows through the existing per-ship Groovy pipeline; bots inherit those for free under input-parity. Per [`prize-applier.md`](../../.claude/rules/prize-applier.md) and [`settings-pipeline.md`](../../.claude/rules/settings-pipeline.md): no REFERENCE.md lookup is needed for the new brain-tier knobs (they have no Subspace canon), but any time we reach into ship stats for bot behaviour we obey the canonical mapping.

**Test-gap caveat.**
The "build a programmatic spawn-projection test harness" memory rule applies here: Pattern-4-shape projections (CCP template → component) have zero automated coverage in this repo today. Bot AI inherits the gap — without the harness, only manual arena launch verifies the integration. v1 ships with that limitation; a parallel PRD scopes the harness independently.

**Player-count scaling on `FillUpXTeams`** (per [`player-scaling.md`](../../.claude/rules/player-scaling.md)).
v1 adds `base + countPerPlayer × N` scaling on `FillUpXTeamsConfig` (triage decision 2026-05-22). Same shape as `SpawnerSpec.countPerPlayer` (canonical example called out in the rule). Active-player count filtered by `ArenaId`. Defaults: `base = effectiveTeams` (current behaviour), `countPerPlayer = 0` (no additional scaling unless explicitly tuned per arena).

**ADR cross-link.**
This PRD is the implementation scope for [ADR-0009](../../docs/adr/0009-bot-ai-architecture.md). Architectural decisions (three-layer stack, BT not FSM, input parity, disjoint-entity-set canonical writer, CCP for tunables, no path-finding/GOAP v1) are settled in the ADR — push back there if you want to re-litigate, not here.

## Comments

### 2026-05-22 — Triage outcome

> *This was generated by AI during triage.*

**Decision:** `ready-for-human` + `enhancement`.

**Why not `ready-for-agent`:**

1. **Multi-day scope** (~8-12 days per ADR-0009 cost estimate). Exceeds the unit-of-work an AFK agent ships in one run.
2. **Needs vertical slicing.** The work decomposes naturally into ~6-8 slices (steering primitives, BT framework, perception, brain composition, system wiring, legacy migration, archetype tuning, scaling). Run `/to-issues` against this PRD next; individual slices will spawn as `ready-for-agent` where the work is mechanical and `ready-for-human` where it requires judgment.
3. **Math sensitivity.** Lead-prediction (`Pursue`), arrive-with-deceleration (`Arrive`), corridor projection (`AvoidObstacles`) are easy to get subtly wrong. Per the user's hand-roll preference, these benefit from human implementation against the Reynolds-1999 + Millington-2019 reference math.
4. **Architectural recency.** ADR-0009 was strengthened twice during this session (input parity, api-tier placement). The architectural shape is stable but new enough that judgment calls during implementation may surface that warrant ADR revision rather than mechanical execution.

**Open decisions resolved during triage:**

- **v1 archetype scope:** ship `Brawler` only. The BT shape supports siblings; Duelist / Defender / PrizeGrabber are clean follow-ups.
- **Player-count scaling:** include `base + countPerPlayer × N` on `FillUpXTeamsConfig` in v1. Matches the canonical pattern in `SpawnerSpec.countPerPlayer` (called out in [`player-scaling.md`](../../.claude/rules/player-scaling.md)).

**Slicing hint** (for the `/to-issues` step that follows):

Suggested vertical slices, each independently mergeable:

1. **Chicken framework retirement** — move `infinity.ai.*` → `infinity.ai.legacy.*` with `@Deprecated`; clears the namespace before new code lands.
2. **Steering primitives (api/)** — the 12 Reynolds-derived classes + 2 composites. Pure-math; unit-tested in isolation. Mechanical.
3. **BT framework (api/)** — `Behavior` / `Status` / composites / decorators / leaf interfaces. Pure-logic; unit-tested in isolation. Mechanical.
4. **Perception contract + service** — api-tier `Perception` + `PerceptionSnapshot`; server-tier `PerceptionService` over mphys `BinIndex`. Judgment on the filtering shape (team/alive/arena).
5. **Brain composition + `Brawler` BT shape** — `BrainArchetype` registry, `Blackboard`, `CombatantBrain`. The judgment-laden slice. Manual launch verification.
6. **`BotBrainConfig` + `BotBrain` + `GroovyBotBrainLoader` + `bot-tuning.groovy`** — CCP wiring. Mostly mechanical (matches `GroovyShipLoader` pattern); judgment on archetype block grammar.
7. **`BotBrainSystem` + `AIEntities.createMobShip` + `FillUpXTeams` wiring** — the ECS boundary. Includes `countPerPlayer` scaling on `FillUpXTeamsConfig`. Architectural-test extension to `CanonicalWriterTest`.
8. **Manual smoke + tuning iteration** — launch the arena, fight the bots, iterate the Groovy archetype until it feels right. Cannot be delegated; needs a human at the keyboard.

**Why this can't be one agent assignment:**

Even if scope were small enough, slices 5 and 8 require gameplay judgment (does the bot *feel* like a player? does combat *read* as fair?). Those calls can't be made without the human-in-loop arena testing the rule already calls out (memory: "Test-before-commit gate for game code"). The remaining slices are mechanical and could be agent-delegated individually if you want.

**Test gap caveat (carried from PRD):** spawn-projection harness isn't in scope here; without it, slice 7 + 5 verification is manual-launch-only. Flagged in the PRD's Out-of-Scope; tracked separately in [`.scratch/spawn-projection-test-harness/`](../spawn-projection-test-harness/).
