# Code-extracted TODOs / FIXMEs

Single-file overflow for in-code TODOs that didn't map to any existing
`.scratch/<feature>/PRD.md` during the 2026-05-14 triage sweep.

Each row: actionable item + source file:line + brief context.

## Backlog

### Cross-tier comms

- [ ] **Extend the server→client `EventBus` bridge to additional EventTypes
  as they're authored.** Framework lives in `EventBusBroadcastHostedService`
  (server) ↔ `EventBusBroadcastListener` (api/) ↔ `EventBusBroadcastClientService`
  (client). `PlayerKilledEvent` is wired end-to-end (server publishes on
  `playerKilled`; client republishes on `playerKilledLocal` to avoid
  single-JVM RMI loop — same pattern as `TargetedEvent` / `PlayerEnteredSession`).
  Add new EventTypes by extending the RMI listener interface, the curated
  `addListener` set in the hosted service's `onInitialize`, and the matching
  `onXxx` method pair. Informational only — clients receive bus events but do
  not mutate authoritative state through them (ADR-0005).

### Architecture

- [ ] **Roster / player list UI must be state-replicated (Zay-ES + SimEthereal),
  not built by subscribing to `PlayerEnteredSession` events on the client.**
  Late-joiner blindness: a client connecting after others entered the session
  will not receive past `PlayerEnteredSession` events through
  `EventBusBroadcastClientService` (Photon-style `AddToRoomCache` does not
  apply here). When the roster slice lands, build it from arena-membership
  component visibility — the entity-sync framework handles late-joiner
  correctness for free.

- [ ] **Bridge throughput discipline:** keep `EventBusBroadcastHostedService`
  for low-frequency lifecycle events only (kill, join, leave, achievements).
  Any new event added to the bridge needs an explicit publish-rate review and
  should be bounded at < 1/sec/connection. Tick-rate state goes through
  SimEthereal component sync, not RMI — reliable RMI buffer flood is an
  Unreal-canon anti-pattern.

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

### Zone vs arena scope

- [ ] **Move `repelFriendlies` from `zone.groovy` to an arena module.**
  Currently a zone-wide ops knob (`SettingsSystem.repelFriendlies()` consumed
  by `RepelSystem`), but the semantics are per-arena gameplay — Trench may want
  it on while a CTF arena wants it off. Lift the knob into the future
  `repel-mechanic` (or absorb into a broader physics-mechanic module) per
  ADR-0008's "horizontal modules own per-arena tuning"; drop the zone-tier
  field once consumers read from the module. Source: `zone/zone.groovy:25`.

### Settings re-projection

- [ ] **`BombSafetyRadius` (per-ship snapshot of `BombConfig.bombSafety`
  + `BombConfig.proximityDistance`) is projected at ship spawn but
  edits to `bomb.groovy` do not re-project onto existing ships —
  selective per-component reproject is future work (consistent with
  the broader fragment hot-reload story per ADR-0004).** Source:
  `api/src/main/java/infinity/es/ship/weapons/BombSafetyRadius.java:19`.

### Client connection flow

- [ ] **"Connect" button in the main menu is a no-op when a second
  client instance tries to join a local host.** Repro: launch the
  game twice on the same machine; first instance starts a local
  server via "New game"; second instance enters the host/port and
  clicks "Connect" → nothing happens (no transition, no error log,
  no UI feedback). Likely the menu state never advances to
  `ConnectionState` or the click handler is unwired. Source: client
  HostState / ConnectionState / main-menu Lemur action wiring.
- [ ] **"New game" when the bind port is already in use logs the
  bind failure to the terminal but never surfaces it to the UI.**
  Repro: start two local hosts back-to-back; the second prints
  `address already in use` on stderr and silently leaves the user
  on the menu. Catch the `BindException` (or equivalent) at the
  host-server boot site and route it to a Lemur error dialog /
  toast so the user knows the port is taken. Source: server boot
  path on the client process (likely `infinity.client.states.HostState`
  or the `GameServer.start()` invocation it wraps).
