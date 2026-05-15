# Code-extracted TODOs / FIXMEs

Single-file overflow for in-code TODOs that didn't map to any existing
`.scratch/<feature>/PRD.md` during the 2026-05-14 triage sweep.

Each row: actionable item + source file:line + brief context.

## Backlog

### Cross-tier comms

- [ ] **Server→client `EventBus` bridge.** `com.simsilica.event.EventBus` is a
  local in-process bus — events published server-side don't reach client
  subscribers. `PlayerKilledEvent` (api/events/arena) was authored as a
  cross-tier payload, but the publish in `EnergySystem.handleDeath` only fans
  out to server-side listeners. Client kill-feed UI / audio / scoreboard need
  either a network adapter that re-publishes selected `EventType`s over the
  wire (RMI broadcast), or a SimEthereal-synced marker component the client
  observes. Decide per use-case which mechanism fits; the api/-side payload
  shape (`PlayerKilledEvent`) is reusable for both.

### Architecture

- [ ] **Project `ShipRestrictionsConfig` to a per-arena component** instead
  of granting `ConfigShipRestrictor` an `infinity.config` exception in
  `LayerDependencyTest`. Per `.claude/rules/config-pattern.md`, "Hot-path
  consumers must not import from `infinity.config`. Spawn systems are the
  only boundary." `ConfigShipRestrictor` is a request-time gate, not a spawn
  system, but currently sits in the spawn-tier allow-list. A
  `ShipRestrictions` component projected at arena-load time would let
  `ConfigShipRestrictor` read the component and drop the template import.
  Defer until another arena-scoped gate appears with the same shape — single
  case may not justify the projection.

### ECS — components on entities

- [ ] **Player ships should carry a `Frequency` component.** Today, human
  player ships don't carry `Frequency` (only freq-aware spawn paths add it),
  so `AvatarSystem.requestShipChange` defaults to freq=0 when absent. The
  `FrequencySystem` already exists as the canonical writer for
  `FrequencyChange` intents — needs the spawn-time stamp at `ShipFactory` /
  `ShipSpawnSystem` (whichever owns initial ship membership). Without this,
  per-team `ShipRestrictor` and `ConfigShipRestrictor.maxPerTeam` silently
  treat all players as team 0. Surface: see comment removed from
  `AvatarSystem.requestShipChange` in the per-arena ship-restrictions slice.

### Test coverage gaps

- [ ] **`WeaponsEligibility.bombSafetyClear` + `effectiveBombSafetyRadius` are
  untested** (~35 LOC, ~30 branches in `WeaponsEligibility`). They require a
  `PhysicsSpace<EntityId, MBlockShape>` fixture (mphys integration). Closing
  this would ratchet `minInfinity-serverLineCoverage` / `minInfinity-serverBranchCoverage`
  back from 0.30/0.25 → 0.31/0.26 (pre-refactor baseline). Owner: future
  test-fixture slice. Reference: 2026-05-14 shape-interface consolidation
  recaptured the ratchet at the post-refactor floor.

### Performance / threading

- [ ] **`InfinityDefaultLeafWorld.setWorldCell` recalculates side masks
  per single cell — add a `setWorldCells(list)` variant that does one
  recalc for a batch.** Source:
  `infinity-server/src/main/java/infinity/sim/internal/InfinityDefaultLeafWorld.java:139`.
  Hot during bulk map load / live edit.

### AI behaviour

- [ ] **`onMoved` brain hook for the wandering mob currently ignores
  every moved object — extend to chase fast-moving prey (filter by
  size to skip stationary `corn` decorations).** Source:
  `infinity-server/src/main/java/infinity/ai/BrainConfigurations.java:337`.
- [ ] **`Actor.search` walks all objects linearly — replace with a
  positional grid / physics broadphase query (split static vs dynamic;
  cone or sphere query in physics-space).** Source:
  `infinity-server/src/main/java/infinity/ai/MobDriver.java:229`.
- [ ] **`MobSystem` movement-event distribution is brute-force —
  replace with a spatial bin index so each moving body only wakes
  brains whose perception radius overlaps.** Source:
  `infinity-server/src/main/java/infinity/ai/MobSystem.java:263`.
- [ ] **Perception radius is hard-coded in two places (`MobSystem` and
  `Actor.look`) — consolidate into a single source.** Source:
  `infinity-server/src/main/java/infinity/ai/MobSystem.java:276`.

### Settings re-projection

- [ ] **`BombSafetyRadius` (per-ship snapshot of `BombConfig.bombSafety`
  + `BombConfig.proximityDistance`) is projected at ship spawn but
  edits to `bomb.groovy` do not re-project onto existing ships —
  selective per-component reproject is future work (consistent with
  the broader fragment hot-reload story per ADR-0004).** Source:
  `api/src/main/java/infinity/es/ship/weapons/BombSafetyRadius.java:19`.
