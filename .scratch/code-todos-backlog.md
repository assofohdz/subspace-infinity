# Code-extracted TODOs / FIXMEs

Single-file overflow for in-code TODOs that didn't map to any existing
`.scratch/<feature>/PRD.md` during the 2026-05-14 triage sweep.

Each row: actionable item + source file:line + brief context.

## Backlog

### Settings pipeline / gameplay knobs

- [ ] **GravBomb energy-cost drift — no writer of `GravityBombCost`.** `GravityBombCost`
  is read by `WeaponsEligibility` (cost extractor) and used as an `ed.getEntities`
  filter key in `WeaponsFireEligibilitySystem` (gravbomb eligibility), but
  **zero `new GravityBombCost(...)` calls exist in the tree**. No spawn projector,
  no factory, no system stamps it — so no ship ever enters the gravityBombs
  EntitySet and firing a gravbomb is impossible. Also no `GravBombStats` record
  exists (Bullet/Bomb/Mine all have one); `GravBombConfig` only carries
  `delayMs` + `wormholeForce`, no cost field. Fix shape: add
  `GravBombStats(BombLevel max, int fireCostEnergy, long fireDelayMillis, int speed, int thrust)`
  mirroring `BombStats`, extend `GravBombConfig` with the corresponding fields,
  add `ShipWeaponsProjector.projectGravBomb` (mirror of `projectBomb`), switch
  `WeaponsEligibility` GRAVBOMB arm from `GravityBombCost::getCost` to
  `GravBombStats::fireCostEnergy`, delete `GravityBombCost`. Pair with a
  Subspace-canon gravbomb tuning sweep (per `[Bomb]` shares with bombs; gravbomb
  has no dedicated section in Subspace canon — Infinity extension).

- [ ] **Wire Thor projectile launch velocity / radius offset to typed
  settings (move literal `50` z-thrust and `thorRadius` lookup behind
  a typed `ThorStats`/`ConsumableConfig` field).** Source:
  `infinity-server/src/main/java/infinity/systems/ship/ConsumableSystem.java:470`
  (formerly `// TODO: Look these settings up in SettingsSystem.`).
  Pair with the settings-pipeline tracker once a slice is opened.
- [ ] **Ship-change request should require full energy (Subspace
  canonical `EnterShipEnergy=100%`).** Source:
  `infinity-server/src/main/java/infinity/systems/AvatarSystem.java:124`
  (formerly `// TODO: Check for energy (full energy to switch ships)`).
- [ ] **Frequency-change request should validate against per-team
  ship-restrictor / max-team-size before granting.** Source:
  `infinity-server/src/main/java/infinity/systems/AvatarSystem.java:231`
  (formerly `// TODO: Check the ship restrictor in place to make sure
  the new frequency is allowed`). `ShipRestrictor` already exists and is
  applied on `*setship`; freq change bypasses it.
- [ ] **Team reset (`AvatarSystem.reset(int team)`) is unimplemented —
  clear players, refresh ship slots.** Source:
  `infinity-server/src/main/java/infinity/systems/AvatarSystem.java:280`
  (formerly `// TODO: implement team reset (clear players, refresh ship
  slots).`). May fold into the `.scratch/squadrons/` PRD if that lands
  first.

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

### Client view

- [ ] **`ShipLightControl` does not track the body-position pipeline
  (`BodyPosition` from SimEthereal) — currently uses the spatial's
  world translation directly. Update to consume `BodyPosition` so the
  ship-attached light stays in sync with the authoritative position.**
  Source: `infinity-client/src/main/java/infinity/client/view/ShipLightControl.java:11`.
