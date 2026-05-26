# `ArenaObjective` + `BotRole`: per-mechanic bias + per-bot role assignment

Status: in-progress (tracer cut — Inc A landed)
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 6. Implements [ADR-0015](../../../docs/adr/0015-arena-objective-and-roles.md).

## Increments (tracer cut 2026-05-26)

Cut tracer-first so the visible "bots navigate to the turf flag" win lands early.

- **Inc A — objective static-goal nav path** ✅ done (2026-05-26). `ArenaObjective`
  (plain interface) + `GoalTile` (cell coords) + `DeathmatchObjective` +
  `TurfObjective`; `MechanicModule.objective()` (placed on `MechanicModule`, not
  `ArenaModule`, because `infinity.sim` must not depend on `infinity.ai` per the
  layer rule); `TurfMechanic` (scopes orphan flags by `ArenaMap` bounds);
  `ArenaModuleSystem.objectiveFor`; objective threaded onto
  `ServerBotAiArenaContext` + static goals pinned in `ArenaSpatialFields.forArena`;
  `HoldPositionBehaviour` (catalog #09, partial) navigates to the objective goal so
  tanky hulls hold the flag. `mechanic 'turf'` added to trench.
  **Divergence from ADR-0015:** `staticGoalTiles()` returns cell coords, not moss
  `TileId` (an arena *is* one TileId; can't address a cell within it) — matches the
  existing `DistanceField` / `NavigateToTile` cell-space decision.
- **Inc B — objective `behaviourBias` consumption** ⬜ planner multiplies
  `objectiveBias(B)` into effective weight; `TurfObjective.behaviourBias()` boosts
  hold-position. (`behaviourBias()` is defined now but inert until the planner reads it.)
- **Inc C — `BotRole` + roles** ⬜ `BotRole` component + `BotRoleConfig` +
  `BotRoleRegistry` + `roles { }` Groovy + `GroovyBotRolesLoader` + `assignRole` +
  `ArenaSnapshot` + role bias + event-driven reassignment.

## What to build

The third weight source. Capability derivation (#04) answers "what can this
ship do?"; this slice answers "what is this arena trying to win at, and what's
this bot's job within it?" An `ArenaModule` ([ADR-0008](../../../docs/adr/0008-arena-composition-and-modules.md))
produces an `ArenaObjective` that contributes a multiplicative `behaviourBias`,
a set of static goal tiles (registered by the nav layer), and a per-bot
`BotRole` assignment at round start. The planner reads
`effectiveWeight(B) = derived(B) × objectiveBias(B) × roleBias(B)`.

Demo: in a KOTH arena, all bots bias toward `anchor` on the central tile
regardless of hull (a no-DPS hull still tries to hold the king tile); the
central tile is a pre-built `DistanceField` goal via the objective.

## Acceptance criteria

- [ ] `api/infinity.ai.objective.ArenaObjective` — **plain (non-sealed) interface** (`name()`, `behaviourBias()`, `staticGoalTiles()`, `assignRole(EntityId, ArenaSnapshot)`); switches use a default branch so community modules can ship gametypes
- [ ] Canonical record subtypes: `DeathmatchObjective` (identity bias), `KothObjective(centralTile)`, `CtfObjective(flagTiles)`, `TurfObjective`, `PowerballObjective(goals)`
- [ ] `api/infinity.ai.objective.ArenaSnapshot` — read-only arena-state view (team rosters, carriers, scores) passed to `assignRole`, decoupled from `EntityData`
- [ ] `api/infinity.es.BotRole` — server-only ECS **class** (not record), single immutable `String name`, no-arg ctor defaulting `"default"`; does not cross the wire
- [ ] `api/infinity.ai.objective.BotRoleConfig(name, Map<String,Double> behaviourBias)` template (defensive `Map.copyOf`) + `BotRoleRegistry` (`get(name)`, `registeredNames()`)
- [ ] `engine-bot-ai.groovy` role-bias maps loaded into `BotRoleRegistry`; load-time validation that every `assignRole` return value is a registered role
- [ ] `BotAiArenaContext` exposes the active `ArenaObjective`; `BotAiHostService.onArenaLoad` unions `objective.staticGoalTiles()` into nav goal registration (#03)
- [ ] `assignRole` invoked at round start / arena join; stamps `BotRole` on each bot (held until next round — no mid-round switching in v2.0)
- [ ] `TacticalPlanner` (#05) multiplies in `objectiveBias × roleBias`; role weight of 0 hard-mutes the behaviour
- [ ] `KothMechanic` wired to produce `KothObjective(centralTile)` as the demo consumer
- [ ] Unit tests: bias composition order + zero-mute; `assignRole` → `BotRole` stamp; unregistered role caught at load; objective static tiles reach nav registration
- [ ] Manual smoke: KOTH bots converge on / bias toward the central tile
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

- [#03 — Production flow-field navigation](./03-flow-field-navigation.md) (static-goal registration)
- [#04 — Capability-derivation pipeline](./04-capability-derivation.md) (the base weight vector bias multiplies)
- [#05 — `TacticalPlanner` + baseline behaviours](./05-tactical-planner-baseline-behaviours.md) (planner reads effective weight)

## Comments
