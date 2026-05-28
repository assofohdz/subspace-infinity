# ADR 0009 — Bot AI Architecture: layered hand-roll with Behaviour Trees

**Status:** Accepted (2026-05-28 — v1 + v2 substrate slices done; legacy chicken-framework retired in bot-ai-v3 #04)
**Date:** 2026-05-22
**Deciders:** Asser Fahrenholz
**Amended by:** [ADR-0014](./0014-capability-derived-bot-composition.md) (2026-05-23) — `BotBrainConfig` "archetype name" + authored weights replaced by capability-derived weights; [ADR-0015](./0015-arena-objective-and-roles.md) (2026-05-23) — arena objective + per-bot `BotRole` add two more multiplicative weight sources; **authoring-tier clarification:** Behaviour Trees + `BrainArchetype` factories are **engineer-authored only**. Zone and arena admins do not author BTs; they tune numeric `BotBrainConfig` knobs (perceptionRadius, aimConeDegrees) and the per-arena `bots { tweak: [...] }` overlay (per [ADR-0014](./0014-capability-derived-bot-composition.md)). The "module-extensibility" framing below applies to module *authors* (engine + community Groovy modules per [ADR-0008](./0008-arena-composition-and-modules.md)), not to per-server zone admins.

## Context

Subspace Infinity spawns AI-controlled ships via the `FillUpXTeams` arena module: `AIEntities.createMobShip` produces a `BotShip`-marked entity, currently stamped with `CharacterInput` (Paul Speed's Javadoc: *"abstract movement inputs from a NPC"*). The ships exist on the server, are nerfed to 100 HP, and currently **have no behaviour** — `CharacterInput` is stamped once at spawn and never updated, so bots sit idle. F5 KOTH smoke surfaced this on 2026-05-14: arenas that assume opposition assume bots will fight, and a non-fighting bot is worse than no bot.

The codebase also currently carries **two parallel input shapes with identical field structure**: `MovementInput` (Javadoc: *"abstract movement inputs from a player"*) and `CharacterInput` (Javadoc: *"abstract movement inputs from a NPC"*). `MovementInputSystem` routes them to different drivers — `MovementInput` → `PlayerDriver` (Infinity-custom ship dynamics: thrust caps, rotation-rate limits, energy gating), `CharacterInput` → `UprightDriver` (`com.simsilica.mphys.UprightDriver`, an upstream Moss walking-character driver). Bot ships routed through `UprightDriver` cannot move as ships — the driver does not implement ship dynamics. The dual-input split is dead architecture waiting to converge.

A homegrown framework at `infinity.ai.*` exists today — `Brain`/`Goal`/`Strategy`/`Actor`/`MobDriver`/`Wander`/`Eat`/`Say` — but it is the Mythruna chicken-demo lineage (`moss/apps/net-char/.../sim/ai/`) ported in 2021. It is built around physics `RigidBody` driving via `MobDriver.AbstractControlDriver`, not `CharacterInput`. The two paths are not compatible: `BotShip` entities never get a `MobDriver` attached, so the chicken brains do not run on them even when configured to. The result: the existing framework drives `Mob`-type entities the project no longer spawns.

The codebase pattern is clear once stated: AI for `BotShip` must drive `CharacterInput` (same intent-channel as human players), so the rest of the server-side movement / weapons / energy systems see no difference between a human-driven and bot-driven ship. The architectural decision is what the AI itself looks like: how it's layered, what decision model it uses, how it gets tuned, and how arena modules extend it.

[`.scratch/adr-backlog.md`](../../.scratch/adr-backlog.md) listed "AI architecture" as a candidate ADR with the trigger *"first non-trivial AI extension by a module, or when mob types grow past a handful."* Both conditions are now active.

### What this ADR does not settle

- **Strategic / team-coordination AI** (GOAP, objective routing, role assignment). Deferred — see Open work.
- **Path-finding** (A*, navmesh). Deferred — see Open work.
- **The chicken framework's retirement timeline** — the move-to-`infinity.ai.legacy` step is in Open work; the full deletion happens once v1 stabilises and is a tactical follow-up, not a separate architectural decision.

## Decision

**Bot AI lives in `infinity.ai.*` as a three-layer hand-rolled stack: pure-math steering primitives → composable Behaviour Tree brains → ECS-boundary system. Brain decision-making is a Behaviour Tree, not an FSM. Tunables flow through Config-Component Projection ([ADR-0002](./0002-config-component-projection.md)). Bots write the same `MovementInput` component players write, processed by the same `PlayerDriver`, subject to the same server-side rate limits — there is no "behind-the-scenes AI steering." `BotBrainSystem` is the canonical writer ([ADR-0001](./0001-ecs-component-model.md)) of `MovementInput` on `BotShip`-marked entities; weapon firing emits `FireRequest` Change-entity intents.**

### Input parity: bots send the same `MovementInput` as players

Bots and players share **one input shape, one driver, one set of rate limits**. `BotBrainSystem` writes `MovementInput` — not `CharacterInput` and not a parallel "bot input" type. Bot ships route through `PlayerDriver`, the same control driver that processes human input. All server-side rate limits — thrust cap (`ThrustStats.max`), rotation rate (`RotationStats.max`), weapon fire delay (`BombFireDelay` / `BulletFireDelay`), energy gating, splash-immunity windows — are enforced by the same downstream code path. The brain has no "AI fast path": a bot cannot turn faster than its `RotationStats.max`, cannot thrust harder than its `ThrustStats.max`, cannot fire weapons more often than its delay knobs. If a bot looks more responsive than a player, it is because its decision loop runs server-side without round-trip latency, not because the input layer skipped a check.

Architectural payoff:

- **One physics + weapons code path to test.** Movement bugs that hit players also hit bots, and vice versa — surfaced earlier.
- **One class of "AI cheating" pattern foreclosed at the API boundary.** A common anti-pattern in many games — AI ignores friction, has perfect aim, instant rotation — is structurally impossible here. The bot writes the same shape into the same component slot processed by the same `PlayerDriver`; there is no "bot bypass" branch to add.
- **Replay parity.** A "replay player as bot" debug mode becomes trivially possible — feed a recorded player's `MovementInput` stream into the bot input slot and the ship moves identically.

The `CharacterInput` + `UprightDriver` + `MobContainer` path in `MovementInputSystem` is retained only while the legacy chicken framework (the only consumer of `CharacterInput` today, beyond the bot-spawn stamp this ADR replaces) lives in `infinity.ai.legacy.*`. When the legacy package is deleted (Open work), the entire NPC input shape disappears with it; `MovementInputSystem` collapses to one container (`PlayerContainer`).

### Three layers, separated by ECS coupling

| Layer | Package | Responsibility | ECS deps |
|---|---|---|---|
| **Steering** | `infinity.ai.steer.*` | Reynolds-derived primitives (`Seek`/`Pursue`/`Evade`/`Arrive`/`Wander`/`AvoidObstacles`/`OrbitTarget`) + boids (`Separation`/`Cohesion`/`Alignment`) + composites (`PrioritySteering`/`BlendedSteering`). Pure `Vec3d`/`Quatd` math; returns `(yaw, thrust)` tuples. | **None** |
| **Behaviour Tree** | `infinity.ai.bt.*` | Tiny BT framework: `Behavior` base + `Selector`/`Sequence`/`Parallel` composites + `Inverter`/`Cooldown`/`Timeout`/`Repeater`/`UntilFailure` decorators + `Condition`/`Action` leaf interfaces. Each tick returns `Success`/`Failure`/`Running`. | **None** |
| **Brain** | `infinity.ai.brain.*` | Composed BTs per archetype (`CombatantBrain`, `DefensiveBrain`, …) with per-bot `Blackboard` (last seen target, last fire time, mode hint, perception cache). One instance per bot ship; held in `BotBrain` ECS component as an opaque holder. | **None** in the brain types themselves; `Blackboard` is plain Java |
| **System** | `infinity.ai.BotBrainSystem` | `BaseInfinitySystem`. Owns `EntitySet<BotShip>`. Per tick: build perception (mphys `BinIndex` broadphase), tick each bot's BT, write `MovementInput`, emit `FireRequest` intents. | **All** — the only ECS boundary |

The layer boundary is non-decorative: steering is unit-testable with no fixtures (pure function on `Vec3d`), the BT is unit-testable with mock `Blackboard`, and only the system layer needs a full ECS test harness.

### Behaviour Tree, not Finite State Machine

The brain is a BT (Halo-2 / Game-AI-Pro standard), not the flat FSM initially scoped. Rationale: the Subspace combat shape the project wants — pursue, strafe, evade, fake-out, reposition, flock, prize-detour, team-coordinate — composes naturally as nested `Selector`/`Sequence` trees. Adding a new behaviour ("fake-retreat-then-counter", "prize-grab-detour-when-low") is one new sub-tree, not an FSM-graph rewrite. FSMs work for small, fixed behaviour sets (~5 states); the target behaviour set is open-ended.

Flocking is **orthogonal** to the BT — it lives in the steering layer as `Separation`+`Cohesion`+`Alignment` blended via `BlendedSteering`. Any BT leaf that emits movement automatically inherits flock-aware steering when ally bots are nearby.

### Canonical-writer compliance (RaM)

`BotBrainSystem` is the **canonical writer** ([ADR-0001](./0001-ecs-component-model.md)) of `MovementInput` on entities carrying the `BotShip` marker. The existing player input path (RMI-driven from client `MovementSession`-shape calls) is the canonical writer of `MovementInput` on non-`BotShip` ships. The two writers operate on **disjoint entity sets** — a ship is either bot-controlled or player-controlled, never both — so the canonical-writer rule holds per entity even though the component type has two writer sites. This is the same disjoint-set canonical-writer shape already used for `Frequency` (`TeamSetup` writes team entities; `FrequencySystem` writes ship entities) and recorded in [`replacement-as-mutation.md`](../../.claude/rules/replacement-as-mutation.md). Weapon firing emits `FireRequest` Change-entity intents (existing pattern, drained by `WeaponsProjectileSpawnSystem`) — bots fire by the same mechanism human ships do, with no direct component writes.

The per-bot brain instance is stamped at spawn as a `BotBrain` component (opaque holder around the brain object + blackboard). This is a single-writer write at the spawn boundary, ticked by `BotBrainSystem` thereafter. The component is **not wire-crossing** — the brain runs server-side only; clients see the resulting `MovementInput` and projectile spawns through normal SimEthereal sync.

### Tuning via Config-Component Projection

Per [ADR-0002](./0002-config-component-projection.md): a `BotBrainConfig` template carries archetype-level tunables — perception radius, engage range, evade-energy threshold, lead-prediction time, wander cadence, weapon-fire range, flocking weights. The template ships as a Groovy fragment under `zone/conf/<preset>/` (provisional name `bot-tuning.groovy`; per-preset and per-arena overrides via the existing settings-pipeline shape). `FillUpXTeams` (or any future bot-spawning `ArenaModule`) selects a `BotBrainConfig` archetype by name when stamping the bot; `BotBrainSystem`'s spawn-projection step reads the template and projects it onto the `BotBrain` component once. Hot-path reads the component only.

True magic numbers stay in Java (BT-node identity, math identities, Reynolds-paper constants). Tuning that an operator would want to adjust without a recompile is in Groovy, consistent with [ADR-0006](./0006-tuning-knobs-vs-magic-numbers.md).

### Layering: steering / BT / brain in api/; system + perception impl + loader in server

[ADR-0005](./0005-layered-architecture.md)'s api/server split is by ECS coupling. The bot AI stack splits cleanly across the boundary:

- **api/** — steering primitives (`infinity.ai.steer.*`), BT framework (`infinity.ai.bt.*`), brain composition (`infinity.ai.brain.*` — archetype factories, `Blackboard`, named-archetype registry), perception interface (`infinity.ai.Perception` + `PerceptionSnapshot` value type), `BotBrainConfig` template, `BotBrain` component. All pure-math / pure-logic / data — no ECS implementation deps.
- **server-tier** — `BotBrainSystem` (the ECS adapter + intent writer), `PerceptionService` (mphys `BinIndex` integration; implements `Perception`), `GroovyBotBrainLoader` (settings-pipeline adapter per [ADR-0004](./0004-settings-pipeline.md)).

Steering + BT + brain composition land in api/ deliberately: future external Groovy modules ([ADR-0008](./0008-arena-composition-and-modules.md)) compose these primitives to author custom brain archetypes without depending on server internals. The api surface grows by ~12 steering classes + ~8 BT primitive classes + a handful of brain types — small, stable (Reynolds steering is a 1999 paper; BT is a well-known shape), worth the boundary cost.

`LayerDependencyTest` rules already cover both packages. The new code respects the same layering: api/-tier code never imports `infinity.systems.*` / `infinity.server.*` / `infinity.client.*`; server-tier code may import from api/.

### Module-extensibility (per ADR-0008)

Bot AI is a horizontal mechanic, not an `ArenaModule`. The single `BotBrainSystem` ticks every arena's bots. Arena modules influence bot behaviour at two grain levels:

1. **Archetype selection** — `FillUpXTeams` (and siblings) pick a `BotBrainConfig` archetype name when spawning. Future arena modules ship their own archetype Groovy fragments (e.g. `zone/conf/dueling-04-2026/bot-tuning.groovy` defines a `Duelist` archetype with different tuning than the default `Brawler`). [ADR-0008](./0008-arena-composition-and-modules.md)'s "horizontal modules own per-arena tuning" rule applies.
2. **Brain composition** — a new archetype can also ship a different BT shape (different root tree wired in Java) by registering a `BrainArchetype` named factory. The `BotBrainConfig` template carries the archetype name; the factory returns a fresh BT instance. No core AI code forks; modules add archetypes, they don't rewrite the system.

Brain archetypes that are **gameplay-shape changes** (a defensive base-defending bot, an objective-grabbing bot for an upcoming flag-capture mode) live next to the arena module that uses them. Brains that are pure tuning-deltas of an existing archetype live as alternate Groovy fragments only.

### No path-finding for v1

Subspace arenas are open 2D fields. Steering-layer `AvoidObstacles` covers static-wall navigation; perception via mphys `BinIndex` covers dynamic targeting. Closed-corridor maps that benefit from A* / navmesh are not in the current roadmap. If a closed-corridor mode appears, the question reopens as a follow-up ADR.

## Consequences

### Positive

- **AI is a first-class ECS citizen.** `BotShip` entities live alongside human-driven ships; the same movement, weapons, energy, and scoring systems run unmodified. No separate "AI control plane."
- **Single canonical writer on `MovementInput`.** Bot inputs and human inputs use the same channel; both are clean single-writer ([ADR-0001](./0001-ecs-component-model.md)) per arena+entity scope (disjoint-entity-set: `BotShip` vs non-`BotShip`).
- **Steering math is unit-testable.** Pure `Vec3d`→`Vec3d` functions. Lead-prediction, arrive-with-deceleration, and the geometric corridor projection in `AvoidObstacles` get focused tests without ECS fixtures.
- **BT shape composes.** Adding a new behaviour (fake-out, prize-detour, team-coordinate) is a sub-tree, not a graph rewrite. Same code shape supports future archetype expansion.
- **All combat tuning is operator-editable.** `bot-tuning.groovy` lives in the same pipeline as ship/weapon stats; reload via the existing settings hot-reload story when it lands ([ADR-0004](./0004-settings-pipeline.md)).
- **Vendoring debt: none.** No external library, no `Vector2`↔`Vec3d` adapter, no upstream-coordination cadence.

### Costs

- **Large complexity, standard risk.** Hand-rolled steering primitives + BT framework + brain composition + ECS-boundary system + tests. Complexity is in the scope (each layer is small individually; the assembly is the bulk); risk is standard because BT + Reynolds steering are well-documented references with no novel substrate. Hand-rolling chosen over vendoring so the code lives in our patterns from line 1; vendoring would have been faster but inherits a cleanup + ongoing-coordination story.
- **Hand-rolled steering math carries risk.** Lead-prediction and arrive-smoothing have known traps (overshoot, ringing oscillation, frame-rate-dependent behaviour). Mitigated by unit tests on each primitive and by treating Reynolds-1999 + Millington-2019 as the reference shape — same math the field has run for two decades.
- **No off-the-shelf fixes.** When a behaviour misbehaves, we own debugging it. There is no upstream issue tracker to file against.
- **BT framework is one more in-house primitive.** ~250 LOC of `Behavior`/`Selector`/`Sequence`/`Parallel`/decorators that future contributors need to learn. Mitigated by BT being a well-documented standard — anyone with game-AI background recognises it.

### Neutral / deferred

- **GOAP / strategic AI.** Deferred until concrete objective-routing or team-coordination AI work surfaces. The BT shape composes well with GOAP-supplied goals if/when added.
- **Pathfinding.** Deferred until a closed-corridor arena mode appears.
- **Behaviour-tree visualisation / live debug.** Useful for tuning but not v1. The BT shape supports a tree-render later (each node has a tick state).
- **Upstream contribution to `moss/steer/`.** Our cleaned + extended steering primitives may be useful as a published Moss module. File an issue against moss if the v1 implementation stabilises; not a commitment.

## Alternatives considered

- **`com.badlogicgames:gdx-ai` (libGDX AI).** Rejected — pulls libGDX-core (~1 MB transitive) for `Vector2` collections we wouldn't use; requires a `Vector2`↔`Vec3d` adapter layer across every steering boundary; maintenance velocity glacial (last release 2019); generic 3D-character shape diverges from Subspace top-down combat. The library's BT and FSM modules are well-built but the cost-of-import doesn't pay for itself when we'd adapt every primitive anyway.

- **Vendor `moss/apps/anarchy/.../sim/steer/`.** Rejected — the steering primitives are real and well-shaped (`Seek`/`Pursue`/`Arrive`/`AvoidObstacles`/`PrioritySteering` with lead-prediction in `Pursue` already), but the package is 2017-vintage with production blockers: `System.out.println` debug calls left in (`Seek.java:38,42,46,50`, `Flee.java:69`), a hard-coded chicken-demo single-predator filter (`Flee.java:50-53` `if (id.getId() != 1) continue;`), zero tests, magic numbers as fields. Cleanup touches every file. Vendoring debt ("ours? theirs? upstream when?") would live forever, and the existing primitives still miss what we need (`Wander` Reynolds-shape, `Evade(target)` parametric, `OrbitTarget`, the boids triad). Net: starting from moss-steer saves ~50% of math but inherits a cleanup-pass and a vendoring story.

- **Extend the existing `infinity.ai.*` chicken framework.** Rejected — the framework drives physics `RigidBody` via `MobDriver.AbstractControlDriver`; `BotShip` entities use `MovementInput` (post-Issue #02; was `CharacterInput` pre-decision). The two paths are not compatible, and re-targeting the chicken framework to write `MovementInput` would touch every `Goal`/`Strategy`/`Action` site for no net architectural benefit. Cleaner to retire the chicken path and start fresh.

- **Flat Finite State Machine for the brain.** Considered for the first ~30 minutes of design; rejected when the target behaviour list (pursue, strafe, evade, fake, reposition, flock, prize-detour, team-coordinate) surfaced. FSM works for ~5 fixed states; this set is open-ended. BT composes; FSM does not.

- **Goal-Oriented Action Planning (GOAP) up front.** Rejected for v1 — overkill for moment-to-moment combat (GOAP plans, BT reacts). Combat ticks at 60+ Hz; GOAP plan-search at that frequency would either dominate the tick or need aggressive plan caching. A strategic / role-selection layer that calls GOAP and feeds a BT is the right shape if/when team-coordination AI lands; deferred until that demand is concrete.

## Resolved decisions

- **Package:** `infinity.ai.*` (top-level). **api/** module: `infinity.ai.steer.*`, `infinity.ai.bt.*`, `infinity.ai.brain.*`, `infinity.ai.Perception` + `PerceptionSnapshot`, `infinity.config.BotBrainConfig`, `infinity.es.BotBrain`. **server-tier**: `infinity.ai.BotBrainSystem`, `infinity.ai.PerceptionService` (Perception impl), `infinity.settings.GroovyBotBrainLoader`.
- **Layering:** Steering (pure math) → BT (pure logic) → Brain (composed BTs + blackboard) → System (ECS boundary). No layer skipping.
- **Decision model:** Behaviour Tree. Not FSM. Not HSM. Not GOAP.
- **Brain shape:** one BT instance per bot ship, held in `BotBrain` ECS component; per-bot `Blackboard` for memory.
- **Canonical writer (RaM):** `BotBrainSystem` is the canonical writer of `MovementInput` on `BotShip` entities (disjoint-entity-set with the existing player input writer, which writes `MovementInput` on non-`BotShip` ships). Weapons fire via `FireRequest` Change-entity intents — no direct component writes.
- **Input parity:** bots write the player input shape (`MovementInput`), processed by `PlayerDriver` — the same control driver that handles human-driven ships. No parallel "bot input" type, no "AI fast path" past server-side rate limits.
- **Tuning:** `BotBrainConfig` template in Groovy under `zone/conf/<preset>/bot-tuning.groovy`. Spawn projects to `BotBrain`. Hot-path reads component only ([ADR-0002](./0002-config-component-projection.md)).
- **Module-extensibility:** archetypes by Groovy fragment + named `BrainArchetype` factory. `FillUpXTeams`-shape modules select by name. No forking of `BotBrainSystem`.
- **Steering primitives v1:** `Seek`, `Pursue`, `Evade`, `Arrive`, `Wander`, `AvoidObstacles`, `OrbitTarget`, `Separation`, `Cohesion`, `Alignment`, `PrioritySteering`, `BlendedSteering`.
- **BT primitives v1:** `Selector`, `Sequence`, `Parallel`, `Inverter`, `Cooldown`, `Timeout`, `Repeater`, `UntilFailure`, plus `Condition` + `Action` leaf interfaces.
- **Layer-test enforcement:** existing `LayerDependencyTest` rules cover the new packages; no new architectural test needed for that. New `CanonicalWriterTest` entry guards `BotBrainSystem`-as-only-`MovementInput`-writer on bot ships, plus a negative assertion that `CharacterInput` is never stamped on a `BotShip`-marked entity.

## Open work

- **Bot input wire path / telemetry marker.** Players write `MovementInput` via an RMI path; bots write it directly server-side from `BotBrainSystem` (no RMI round-trip needed). Decide whether to introduce an "input source" marker (`PlayerInputSource` / `BotInputSource`) for telemetry, replay, or debug; the wire-shape parity holds either way. Optional, settled at implementation.
- **BT live debugger.** Deferred. Trigger to revisit: tuning fatigue when adjusting fragment knobs and not understanding why a bot picked a sub-tree.

## References

- [`docs/adr/0001-ecs-component-model.md`](./0001-ecs-component-model.md) — `BotBrainSystem` is canonical writer of `MovementInput` on bot ships; weapons via `FireRequest` Change-entity intents.
- [`docs/adr/0002-config-component-projection.md`](./0002-config-component-projection.md) — `BotBrainConfig` template projects to `BotBrain` component at spawn.
- [`docs/adr/0005-layered-architecture.md`](./0005-layered-architecture.md) — `infinity.ai.*` is server-tier; brain archetypes + config are api-tier.
- [`docs/adr/0006-tuning-knobs-vs-magic-numbers.md`](./0006-tuning-knobs-vs-magic-numbers.md) — operator-tunable values in Groovy; math identities + BT-node identity stay in Java.
- [`docs/adr/0008-arena-composition-and-modules.md`](./0008-arena-composition-and-modules.md) — `FillUpXTeams` selects archetype; modules ship their own archetypes; brain itself is horizontal, not an `ArenaModule`.
- [`.claude/rules/replacement-as-mutation.md`](../../.claude/rules/replacement-as-mutation.md) — Change-entity intent shape for `FireRequest`.
- [`api/src/main/java/infinity/sim/AIEntities.java`](../../api/src/main/java/infinity/sim/AIEntities.java) — `BotShip` spawn site; stamps `MovementInput` + `BotBrain` (Issue #02 landed the switch from `CharacterInput`).
- [`infinity-server/src/main/java/infinity/modules/mechanic/FillUpXTeams.java`](../../infinity-server/src/main/java/infinity/modules/mechanic/FillUpXTeams.java) — current bot-spawning `ArenaModule`; selects archetype by name.
- [`api/src/main/java/infinity/es/input/MovementInput.java`](../../api/src/main/java/infinity/es/input/MovementInput.java) — the input component bots and players both write under input-parity.
- [`api/src/main/java/infinity/es/input/CharacterInput.java`](../../api/src/main/java/infinity/es/input/CharacterInput.java) — the legacy NPC input shape, retained only for the chicken framework; deleted with `infinity.ai.legacy.*`.
- [`infinity-server/src/main/java/infinity/systems/MovementInputSystem.java`](../../infinity-server/src/main/java/infinity/systems/MovementInputSystem.java) — routes `MovementInput` → `PlayerDriver`, `CharacterInput` → `UprightDriver`; the latter path becomes dead code at chicken-retirement.
- [`infinity-server/src/main/java/infinity/sim/internal/PlayerDriver.java`](../../infinity-server/src/main/java/infinity/sim/internal/PlayerDriver.java) — the ship-dynamics driver that processes `MovementInput` for both players and bots after this ADR lands.
- Reynolds, Craig. *Steering Behaviors for Autonomous Characters.* GDC 1999. The source for `Seek`/`Pursue`/`Evade`/`Arrive`/`Wander`/`Separation`/`Cohesion`/`Alignment`. red3d.com/cwr/steer/
- Millington, Ian. *AI for Games* (3rd ed., 2019). Ch. 3 (movement / steering) + Ch. 5 (decision-making — BT framework shape).
- Isla, Damian. *Handling Complexity in the Halo 2 AI.* GDC 2005. The BT shape this ADR's brain layer takes.
- *Game AI Pro* volumes 1–3 (free PDFs at gameaipro.com). Production BT chapters from Halo, FEAR, Killzone, Tomb Raider; combat-AI design patterns.
- [`.scratch/adr-backlog.md`](../../.scratch/adr-backlog.md) — this ADR retires the "AI architecture" backlog entry (four questions: server-tier sub-layer? mob-behaviour model? module extension? path-finding? — answered above).
