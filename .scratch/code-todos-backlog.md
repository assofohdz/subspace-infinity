# Code-extracted TODOs / FIXMEs

Single-file overflow for in-code TODOs that didn't map to any existing
`.scratch/<feature>/PRD.md` during the 2026-05-14 triage sweep.

Each row: actionable item + source file:line + brief context.

## Backlog

### Physics / collision response

- [ ] **Rigid-body-vs-rigid-body collision torque spins the ship's nose into the contact.**
  When the ship hits another `RigidBody` (other ship, asteroid, large static) at an
  off-angle, the mphys collision response applies an angular impulse around the Y axis.
  `PlayerDriver.applyRotationEase`
  ([infinity-server/.../sim/internal/PlayerDriver.java](../infinity-server/src/main/java/infinity/sim/internal/PlayerDriver.java))
  zeroes X/Z angular velocity each tick via `setRotationalVelocity(0, newAng, 0)`, but
  the Y component is *eased* from `currentAng` toward `targetAng = intent.x * rotSpeed`:
  `newAng = currentAng + (targetAng - currentAng) * t`. If collision left
  `currentAng` much larger than the ship can accelerate to under intent alone, the eased
  blend keeps spinning the ship for many ticks — visually the nose rotates *into* the
  contact surface instead of away from it.
  **Scope: rigid-body-vs-rigid-body only.** MWorld voxel-block collisions appear to
  behave differently (no equivalent torque grind observed) — investigate why if a fix
  unifies both paths.
  Symptoms in-game: ship "grinds" along the contacted body, nose pointed slightly into
  it. Bots are extra-affected because Pursue + thrust keeps them pressed against the
  contact; humans tend to release thrust and recover. **Suspected chain in bot-AI corner
  symptom:** bot pursues player → contacts player off-angle → spin torque rotates
  heading → bot continues thrusting in new (now-wrong) direction → drifts into a wall.
  Surfaced 2026-05-23 during bot-AI Issue #04 demo testing; recurring issue (user:
  "hit hard again").
  Possible fixes (each has trade-offs):
    1. **Clamp `currentAng` before easing**: `currentAng = max(-rotSpeed, min(rotSpeed, currentAng))`.
       One-line change; loses any "carry-over" from spin-up under thrust.
    2. **Replace ease with direct set**: `body.setRotationalVelocity(0, targetAng, 0)`.
       Removes collision-spin entirely; also removes the "ship feels heavy" responsiveness
       feel the ease currently provides.
    3. **Filter collision-applied Y torque at the mphys callback layer** — intercept the
       contact response and zero the Y component. Most surgical but needs mphys
       integration work.
    4. **Lock body inertia / friction at ship-spawn** so rigid-body contacts don't
       generate angular impulse on the ship in the first place. Cleanest if the angular
       inertia is the actual source; needs mphys body-config investigation.
  Decision likely needs a one-line spike on (1) to confirm it solves the bot
  observation, then a follow-up decision on whether to retain the ease for human feel.

### World coordinates / placement

- [ ] **Ship sphere vertical placement is misaligned with the wall block row.**
  Ships are spheres centered at `Y = GAMEPLAY_Y = 1.0` with radius `EngineConfig.shipRadius()`
  (~0.5), so a ship's bounding sphere occupies `Y ∈ [0.5, 1.5]`. Walls placed by
  `LegacyMapProjector` (`new Vec3d(xpos, 1, zpos)`) become 1×1×1 MBlock cells occupying
  `Y ∈ [1.0, 2.0]`. Vertical overlap is `Y ∈ [1.0, 1.5]` — only the top half of the
  sphere is in the wall cell row; the bottom half sits in the empty Y=0 cell row.
  Consequences: (a) collision contacts are biased (only top-half normals fire),
  (b) any future perception or raycast aimed at "the ship's plane" must be careful
  to use a Y clearly inside the wall row, not on the boundary at `Y=1.0` — the
  bot-AI Issue #03 raycast worked around this by using `Y = GAMEPLAY_Y + 0.5`
  (centred in the wall row); see `PerceptionService.castForwardWallRay`.
  Possible fixes (each has trade-offs):
    1. Place ship spheres centred at `Y = 1.5` so sphere range `[1.0, 2.0]` matches
       the wall row exactly. Requires updating `GAMEPLAY_Y` + `clampToGameplayPlane`
       + likely the camera + any HUD/radar Y math.
    2. Place wall cells at `Y = 0` so they occupy `[0.0, 1.0)` and the sphere range
       `[0.5, 1.5]` straddles them. Less invasive but walls are then visually below
       the gameplay plane; clients rendering walls may need adjustments.
    3. Replace ship spheres with 1×1×1 cubes aligned to cell Y=1. Loses canonical
       Subspace circle-collision semantics; significant rebalance risk.
  Surfaced 2026-05-22 during bot-AI Issue #03 wall-raycast debugging.

### Input pipeline

- [ ] **Replace `MovementInput` with a rate-only `MovementIntent(turnRate, thrustRate, flags)`.**
  Surfaced 2026-05-22 during bot-AI Issue #02 input-abstraction audit
  ([ADR-0009](../docs/adr/0009-bot-ai-architecture.md)). `MovementInput.facing`
  (`Quatd`) is dead-on-the-wire: client `AvatarMovementState.update` never
  updates it (always identity), server `PlayerDriver.applyMovementInput`
  ignores it, only `move.x` (rotation rate) + `move.z` (thrust) are read.
  Refactor would drop the dead field, rename the type to make the rate-based
  abstraction explicit, and shrink the wire payload. Touches:
  `MovementInput` (api/), `AvatarMovementState` (client), `PlayerDriver` +
  `MovementInputSystem` (server), `AIEntities` + `BotBrainSystem` (server).
  Defer until the bot-AI v1 stack is stable so the refactor lands on settled
  ground.

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

### Test coverage gaps

- [ ] **`WeaponsEligibility.bombSafetyClear` + `effectiveBombSafetyRadius` are
  untested** (~35 LOC, ~30 branches in `WeaponsEligibility`). They require a
  `PhysicsSpace<EntityId, MBlockShape>` fixture (mphys integration). Closing
  this would ratchet `minInfinity-serverLineCoverage` / `minInfinity-serverBranchCoverage`
  back from 0.30/0.25 → 0.31/0.26 (pre-refactor baseline). Owner: future
  test-fixture slice. Reference: 2026-05-14 shape-interface consolidation
  recaptured the ratchet at the post-refactor floor.

### Lighting

- [ ] **Add an `arena.groovy` setting that toggles dynamic lighting on world
  MBlocks.** When enabled, `PointLight`s emitted by ships / projectiles affect
  block geometry (current behaviour via `MatDefs/TileLit.j3md`, see
  `BlockGeometryIndex.java:240,248`). When disabled, blocks render with a
  flat default (swap to `Common/MatDefs/Misc/Unshaded.j3md`, or a `TileLit`
  path that ignores the `LightList`) so the arena reads as a uniformly-lit
  field independent of nearby dynamic lights. Useful for arenas that want
  classic Subspace flat-lit visuals without ripping out `PointLight`s on
  ships/projectiles. Wire-up: new typed adapter under
  `infinity.settings.*` → `LightingConfig` (or fold into an existing
  `engine.groovy`-tier config if a "rendering" tier emerges) → consumer in
  `BlockGeometryIndex` (or a wrapping app state) that picks the matdef based
  on the arena's flag. Default `true` (keep current behaviour). Pair with
  the dynamic/ambient mix work below — this toggle is the binary "off"
  switch; the mix knob is the analogue dial.

- [ ] **Balance ambient vs dynamic light on world blocks.**
  `WallLightDecorator` bakes vertex-colour light data into wall cells at
  map-load ([infinity-server/.../systems/WallLightDecorator.java](../infinity-server/src/main/java/infinity/systems/WallLightDecorator.java));
  blocks don't react to dynamic `PointLight`s from ships/projectiles.
  Ambient too high → everything reads as a flat-lit picture, ship lights
  have no perceived effect; ambient too low → world goes black and only
  the ship-radius is visible (Doom-like, but disorienting on Subspace
  overhead view). Want a middle ground: maybe per-block partial
  contribution from nearby dynamic lights (small radius, low intensity),
  OR a fragment-shader pass that samples nearby ship-light positions per
  block face, OR a "fake ambient" floor that's slightly modulated by
  local density of ship lights. Needs a small shader prototype + an
  `engine.groovy` tunable for the dynamic/ambient mix.

### Visual / animation

- [ ] **Sprite-sheet animation frame-rate vs entity `Decay` lifetime are
  not in sync — non-looping effects run ~1.5× their tilesheet length.**
  The animation isn't endlessly looping (Decay still reaps the entity),
  but the per-frame advance is slower than the entity's lifetime, so the
  tilesheet wraps mid-life: explosion plays once, restarts, plays partway
  through a second loop, then the entity decays. Visually reads as a
  stutter/double-flash. Intentional loopers (black hole) are unaffected
  because their Decay is long.

  Two ways to fix:
  - **Tune frame-rate to total lifetime per effect.** For one-shot
    effects, set `framesPerSec = frameCount / decaySeconds` so the last
    frame lands right at Decay expiry. Requires the client to know both
    values (frame count of the tilesheet + entity Decay) at spawn time.
  - **Clamp on last frame.** Animation advances at its tuned rate but
    holds on the final frame once reached. Entity still decays
    independently. Simpler shader/state change; tilesheet rate doesn't
    need to scale with Decay.

  Surface to look at: effect-spatial creation
  ([infinity-client/src/main/java/infinity/client/states/EffectSpatialFactory.java](../infinity-client/src/main/java/infinity/client/states/EffectSpatialFactory.java))
  + the j3md / shader pair sampling the sprite sheet. A `LoopMode`
  marker on the entity (`LOOP` vs `ONE_SHOT_CLAMP`) plus a tilesheet-
  metadata hook (frame count) would let the spatial factory honour
  both paths without server-side timing knowledge.

### Map element materialization

- [ ] **Asteroids should be world blocks, not ECS entities.** Today
  `LegacyMapProjector` calls `MapFactory.createAsteroidSmall` /
  `createAsteroidMedium` for each asteroid tile in the `.lvl` (see
  [infinity-server/src/main/java/infinity/systems/LegacyMapProjector.java:108-120](../infinity-server/src/main/java/infinity/systems/LegacyMapProjector.java)),
  producing a separate physics-bodied entity per asteroid. Static asteroids
  (no Subspace canon for moving them in-game) would be better as tile cells
  in the Moss `World` — same shape as wall blocks. Cuts entity count on
  asteroid-heavy maps, eliminates per-asteroid collision-listener pairs, and
  lets the existing block-render pipeline draw them without a separate
  `ShapeNames.ASTEROID` mesh path. Check first whether anything reads the
  asteroid entity post-spawn (kill prize? collision damage? destructible
  asteroids in any KOTH/event mode?); if so, those readers need either a
  tile-side equivalent or to stay on the entity model. Source:
  `MapFactory.createAsteroidSmall` / `createAsteroidMedium` at
  `api/src/main/java/infinity/sim/MapFactory.java:114,128`.

### Performance / threading

- [ ] **`InfinityDefaultLeafWorld.setWorldCell` recalculates side masks
  per single cell — add a `setWorldCells(list)` variant that does one
  recalc for a batch.** Source:
  `infinity-server/src/main/java/infinity/sim/internal/InfinityDefaultLeafWorld.java:139`.
  Hot during bulk map load / live edit.

### Identity / ECS

- [ ] **Everything created inside an arena should be stamped with `ArenaId`.**
  Several entity types are spawned without an `ArenaId`, forcing consumers to
  recover the arena by spatial bounds-testing the position against each arena's
  map box — fragile and O(arenas) per query. Concrete case found 2026-05-25
  during bot-AI spatial-fields (#07): `MapFactory.createPrize` stamps
  `SpawnPosition` / `Bounty` / `PrizeType` / `Decay` but **no `ArenaId`**, so
  `BotBrainSystem.refreshDynamicFields` has to assign prizes to arenas by
  point-in-map-bounds (`prizesInArena`). Audit every `MapFactory.*` /
  `WeaponFactory.*` spawn path (prizes, projectiles, doors, decorations,
  effects) and the spawner systems: anything whose lifetime is arena-scoped
  should carry `ArenaId` at creation (the spawner usually already has it —
  `PrizeSpawnerSystem` reads `spawnerArenaId` then drops it). Once stamped,
  replace bounds-test assignment with a direct `ArenaId` read. Watch the
  `ArenaMembershipSystem` / `ArenaSystem` writer split (per
  `replacement-as-mutation.md`) so the stamp doesn't create a second writer.

- [ ] **Player-vs-ship identity: two ECS entities per logged-in human.**
  `AccountHostedService.login`
  ([infinity-server/src/main/java/infinity/server/AccountHostedService.java:187](../infinity-server/src/main/java/infinity/server/AccountHostedService.java))
  creates a player entity and registers it in `playerConnectionMap` (the key
  `lookupConnection(EntityId)` walks for `postPrivateMessage`). Independently,
  `GameSessionImpl` ctor
  ([infinity-server/src/main/java/infinity/server/GameSessionHostedService.java:225](../infinity-server/src/main/java/infinity/server/GameSessionHostedService.java))
  creates a SECOND `playerEntityId` used as the ship's `Parent`. These are two
  ECS entities for the same logged-in human — both get `Name(playerName)`, only
  one is in `playerConnectionMap`, only the other is reachable from the ship
  via `Parent`. The identity PRD
  ([`.scratch/player-vs-ship-identity/PRD.md`](player-vs-ship-identity/PRD.md))
  is explicit that there should be one durable player entity per session;
  refactor `0d42d12d` didn't finish the consolidation. Fix: `AccountHostedService`
  stops creating its own entity; on `login()` it reads
  `GameSessionImpl.playerEntityId` (already exists at connection-attached time)
  and registers THAT in `playerConnectionMap` + sets
  `ATTRIBUTE_PLAYER_ENTITYID`. Surfaced 2026-05-20 during F4 round-timer chat
  filtering — the chat-by-arena fix (`postArenaMessage(ArenaId)`) sidesteps the
  problem by filtering via `getAvatarEntity(conn)` instead of using
  `playerConnectionMap`. Originally filed alongside a `FillUpXTeams` spawn-
  cull storm (id climb at 8 bots); that primary cause was fixed 2026-05-25
  via the `spawnedBots` tracker rework + regression test
  `FillUpXTeamsTest.boundedTwoTeams_stuckBotLosesArenaId_notRespawned`. The
  identity duplication is the remaining open concern.

### Chat lifecycle

- [ ] **`InfinityChatHostedService` should defer system chat output until the
  player has loaded into an arena.** Today, registration-time advertisements
  from `registerPatternTriConsumer` / `registerPatternBiConsumer` (each calls
  `postPublicMessage(SYSTEM_MESSAGE_SENDER, ..., description)` with the
  command's help string) broadcast to every connected session — including
  sessions that have authenticated but haven't yet entered an arena (the
  player's avatar is null / arenaId unset). Welcome / help text shows up
  before the player has gameplay context. Suggested fix: queue per-session
  pre-arena messages and flush them on the first observed
  `getAvatarEntity(conn).ArenaId != null` transition, or simply gate the
  `postPublicMessage` broadcast loop on session readiness (e.g. a
  `ChatSessionImpl.arenaJoined` boolean flipped when the avatar's `ArenaId`
  is first observed). Source: `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java`
  (the broadcast loop in `postPublicMessage` + the registration sites that
  call it).

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
