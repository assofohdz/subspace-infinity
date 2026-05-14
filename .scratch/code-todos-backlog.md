# Code-extracted TODOs / FIXMEs

Single-file overflow for in-code TODOs that didn't map to any existing
`.scratch/<feature>/PRD.md` during the 2026-05-14 triage sweep.

Each row: actionable item + source file:line + brief context.

## Backlog

### Settings pipeline / gameplay knobs

- [ ] **Wire Thor projectile launch velocity / radius offset to typed
  settings (move literal `50` z-thrust and `thorRadius` lookup behind
  a typed `ThorStats`/`ConsumableConfig` field).** Source:
  `infinity-server/src/main/java/infinity/systems/ship/ConsumableSystem.java:470`
  (formerly `// TODO: Look these settings up in SettingsSystem.`).
  Pair with the settings-pipeline tracker once a slice is opened.
- [ ] **Burst attack should deduct an energy cost when fired.** Source:
  `infinity-server/src/main/java/infinity/systems/ship/WeaponsEligibility.java:319`
  (formerly `// TODO: Add cost to burst.`). Subspace canonical:
  `BurstShrapnel` / `BurstEnergy` — verify in
  `.scratch/subspace-ini-reference/REFERENCE.md` before scoping. Sister
  case to bomb/bullet/mine cost deduction which already routes through
  `deductCostOfAttackBomb` / `deductCostOfAttackBullet` /
  `deductCostOfAttackMine`.
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

### Performance / threading

- [ ] **`InfinityDefaultLeafWorld.setWorldCell` recalculates side masks
  per single cell — add a `setWorldCells(list)` variant that does one
  recalc for a batch.** Source:
  `infinity-server/src/main/java/infinity/sim/internal/InfinityDefaultLeafWorld.java:139`.
  Hot during bulk map load / live edit.
- [ ] **`DefaultColumnDb.storeObject` uses a single hard `synchronized
  (writeLock)` for all column writes — replace with per-column locks so
  unrelated columns can persist concurrently.** Source:
  `infinity-server/src/main/java/infinity/server/DefaultColumnDb.java:96`
  (formerly `// FIXME: column locks instead of hard sync`).
- [ ] **`DefaultColumnDb.writeColumn` resets the version counter before
  the file write — race window if a second writer mutates the column
  mid-flight. Pattern is common to all `DataVersion` use-cases in the
  Mythruna-derived persistence layer.** Source:
  `infinity-server/src/main/java/infinity/server/DefaultColumnDb.java:152`
  (formerly `// FIXME: fix the thread sync issue here that is pretty
  common with all DataVerison use-cases.`).

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
