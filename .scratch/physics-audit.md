---
slice: P2 — Physics implementation audit
status: in-progress (deliverable; spawns follow-up slices)
scope: general scan of Infinity's physics layer vs Subspace space-combat canon
       and vs idiomatic mphys / sio2-mphys usage
---

# Physics implementation audit (Slice P2)

## Lens

Two lenses, applied together:

1. **Canon-fidelity** — does Infinity's physics behave like Subspace /
   Continuum space combat (top-down 2D arena, newton-ish thrust+drag,
   wall bounces, wormhole gravity, projectile velocity inheritance,
   bomb recoil, bomb bounces, frictionless walls)?
2. **Framework idiom** — does Infinity use mphys / sio2-mphys
   primitives where they exist, or reinvent them in application code?

The audit is investigation + documentation. Concrete refactors fall
out as follow-up slices (listed at the end).

## Surface map

Physics-touching code, by file:

| File | Role | Notes |
|---|---|---|
| `infinity.sim.PlayerDriver` | per-ship `AbstractControlDriver` | car-curve thrust, custom drag-as-force, exponential angular ease, hand-coded vMax cap, Y-snap |
| `infinity.systems.ContactSystem` | global `ContactListener` | reads `BounceRestitution` per-body; reimplements wall friction via custom tangential damping (sets `contact.friction = 0`) |
| `infinity.systems.ship.WeaponsSystem.getAttackInfo` | projectile spawn | reads `BulletSpeed` / `BombSpeed` / `BurstSpeed` × `EngineConfig.subspaceVelocityScale`, capped at `maxProjectileSpeedJme`; mines force `velocity = (0,0,0)` |
| `infinity.systems.ship.ConsumableSystem.getActionPosition` | thor / repel / brick / decoy / portal / rocket spawn | thors hardcoded `addLocal(0, 0, 50)`; repel/brick/decoy/portal are no-velocity markers; rocket is a buff |
| `infinity.systems.ship.ProximityFuseSystem` | per-tick proximity arming | manually walks `EntitySet(Health.class)` for every in-flight bomb |
| `infinity.systems.ship.WeaponsSystem.applySplashDamage` | bomb detonation | manually walks `EntitySet(Health.class)` for every detonation |
| `infinity.systems.ship.WarpSystem` | teleport / spawn | uses `physicsSpace.teleport`; explicitly zeros velocity / acceleration / accumulators |
| `infinity.systems.MapSystem` | wormhole + door creation | hardcoded gravity-well force `5000`, `GravityWell.PULL` |
| `api.infinity.sim.GameEntities.createShip` | ship body composition | `Mass(1)`, `Gravity.ZERO`; tunable physics knobs deferred to `ShipSpawnSystem` projection |
| `api.infinity.sim.CorePhysicsConstants` | radii / masses | `BULLETSIZERADIUS = 0.125`, `BOMBSIZERADIUS = 0.5`, `SHIPMASS = 50` (stale — `createShip` uses `1`), `SHIPTHRUST = 10` (unused) |
| `api.infinity.config.EngineConfig` | engine-tier velocity bridge | only Subspace→jME projectile-speed scale + cap today |
| `api.infinity.config.ArenaConfig.wallFriction` | per-arena friction | divergence from canon (canon = frictionless), documented |

## Findings

### F1 — Drag is reimplemented, not delegated to mphys

PlayerDriver lines 125-129:

```java
// Drag: force opposite to current motion, magnitude = accelRate × dragFactor.
final Vec3d dragDir = currentVel.mult(-1.0 / currentSpeed);
body.addForce(dragDir.mult(accelRate * dragFactor));
```

mphys `RigidBody` ships with built-in linear and angular damping —
`setDamping(double linear, double angular)` — applied per-frame as
`velocity *= pow(damping, t)`. Default 0.9 / 0.8.

**`grep -r "setDamping" infinity/src api/src` → zero hits.**

The custom drag is force-coupled to `accelRate` (a pun: the same
constant that scales the player's intent also scales the drag). The
mphys-native approach would be a per-body damping coefficient
projected from `ShipConfig` at spawn time (Pattern 4 split: template
→ component → applied at spawn via `body.setDamping(linear, angular)`
in a small post-spawn hook).

**Risk if changed:** existing `dragFactor` values are hand-tuned
against the force-coupled formula. A naive swap will change ship
feel. Migration would need a calibration pass per ship.

**Why this matters:** the force-based drag is what creates the
"unit confusion" the slice description called out. `dragFactor` reads
like a 0..1 scalar (fraction-of-thrust subtracted as drag) but it's
actually `force = accelRate × dragFactor` — a coupling that's
invisible at the call site. Native damping decouples those.

### F2 — `applyImpulse` / sio2-mphys `Impulse` component unused

`grep -r "applyImpulse" infinity/src api/src` → zero hits. sio2-mphys
exposes an `Impulse` ECS component that the `MPhysSystem` reads and
applies before integration each frame — the framework-native way to
do one-shot velocity deltas (knockback, repel push, network
correction).

Today every velocity-change site uses `body.setLinearVelocity(...)`
directly (WarpSystem.150, PlayerDriver.110-111 safety cap,
ContactSystem.118-122 wall friction). That bypasses the integrator's
ordering guarantees and is fragile around contact resolution.

**Slice 1's open follow-up** ("Repel still fires a visual/audio
effect only. The plumbing is canonical and ready; the impulse system
is the next gate.") is exactly the kind of thing `applyImpulse`
exists for. Implementing repel impulses by reading `RepelSpeed` /
`RepelDistance` and pushing each victim via `applyImpulse` is the
idiomatic path.

### F3 — Spatial queries done by manual EntitySet walks

`ProximityFuseSystem.tryArm` (per slice 9b note) walks every
`Health`-bearing entity for every in-flight proximity bomb every
tick. `WeaponsSystem.applySplashDamage` does the same on detonation.
`WeaponsSystem.bombSafetyClear` does it per fire-attempt.

mphys provides `PhysicsSpace.queryBounds(...)`, `queryHits(...)`,
`queryContacts(...)` — bin-index-backed spatial queries. Each of the
three scans above is a textbook `queryBounds` use case ("give me
bodies within radius R of point P"); the bin index is already keeping
the index they'd consume.

`grep -r "queryBounds\|queryHits\|queryContacts" infinity/src` → zero
hits.

**Slice P1** (already queued) is the right home for this. The
spatial-query swap is one of the lower-risk moves to make since the
ECS-side filtering (FF gate, arena membership, victim type) stays
unchanged.

### F4 — Wall friction is reimplemented to dodge unwanted torque

ContactSystem.105-124:

```java
// By keeping contact.friction = 0 we get a pure normal impulse
// (r ∥ n on a sphere → zero torque), and we separately scale the
// body's tangential velocity component here.
```

This is correct — for arcade ship physics the player owns heading,
and the resolver's friction-as-torque is wrong-shaped. The audit's
question is whether mphys offers a primitive that says "no friction
torque" (it doesn't, per the survey: `Contact.friction` and
`restitution` are scalar, no rotation lock).

**Conclusion:** the workaround is justified. Recommend documenting
it on `ContactSystem.newContact` Javadoc (already partially there in
the inline comment) and flagging it as a deliberate framework
divergence rather than a candidate for refactor.

The same pattern shows up in `PlayerDriver.145-151` (Y-axis snap to
keep gameplay in 2D) — also justified, also a framework-divergence
to document not refactor.

### F5 — Subspace canon physics knobs unwired

`svsSettings.cfg` contains canonical Subspace per-ship knobs that no
typed Infinity consumer reads:

| Subspace key | REFERENCE.md role | Infinity status |
|---|---|---|
| `BombThrust` | Back-thrust on bomb fire (recoil) | Authored in cfg, no consumer |
| `BombBounceCount` | Bombs bounce N times before impact-explode | Authored in cfg, no consumer |
| `AfterburnerEnergy` | Afterburner activation cost | Authored in cfg, no consumer |
| `SoccerBallFriction` | Soccer ball deceleration | Authored in cfg, no consumer; soccer mechanic absent |
| `Gravity` (per-ship) | Wormhole pull radius `R = 1.325 × g^0.507` | Wormholes exist (`GravityWell`), but pull is hardcoded `5000` in MapSystem.451 — no per-ship `Gravity` read |
| `GravityTopSpeed` | Extra speed allowed under wormhole pull | Not wired |
| `Radius` (per-ship) | Ship collision radius (px, default 14) | `CorePhysicsConstants.SHIPSIZERADIUS = 1f` (jME world units) — single global value |
| `BounceFactor` | Wall bounciness (0..16, 16=no speed loss) | Per-ship `BounceRestitution` (0..1 double) — divergent type/scope; conversion empirical |

Each row is a candidate for the settings-pipeline tracker. Bomb recoil
and bomb bounces are the two with the largest gameplay-feel impact;
afterburner is a self-contained mechanic; gravity and radius are
arena-wide consequences.

### F6 — Mines are velocity-zeroed in a special-case branch

`WeaponsSystem.getAttackInfo` line 810:

```java
if (weaponFlag == WeaponsSystem.MINE) {
    projectileVelocity.set(0, 0, 0);
}
```

This survives because mines reuse the bomb-spawn pipeline but want
zero velocity. The Pattern 4 / Slice 10 fix is to introduce a
`MineSpeed` component (defaults to 0) projected from `ShipConfig`,
and remove the special-case. Mirrors the bullet/bomb/burst slot
shape exactly.

Tiny slice; obvious follow-up.

### F7 — Stale magic numbers in CorePhysicsConstants

Several constants in `api.infinity.sim.CorePhysicsConstants` are
stale or misplaced:

- `SHIPMASS = 50` — `GameEntities.createShip` uses `Mass(1)`.
  Constant is dead.
- `SHIPTHRUST = 10` — no consumer (`grep` returns only the
  declaration). Dead.
- `ARENAWIDTH = 1024` — Subspace canon (16-bit tile coord), but
  belongs in `InfinityConstants` (world-coord rule), not in a
  physics constants file.
- `PHYSICS_SCALE = 1` — placeholder; no consumer. Dead.
- Several `*MASS` values (`OVER1MASS`, `OVER2MASS`, `OVER5MASS`)
  may also be dead — needs a per-constant `grep` pass.

Cleanup-bag candidate. Low risk; pure deletion + one constant
relocation. Could land alongside any other physics-touching slice.

### F8 — Engine-tier config has natural room to grow

`engine.groovy` today is two fields (`subspaceVelocityScale`,
`maxProjectileSpeedJme`). The audit surfaces several engine-tier
candidates:

- **Default per-body damping** — when migrating F1, the integrator
  fallback values (mphys's 0.9 / 0.8) deserve to live here, not in
  per-ship config, since they apply to every dynamic body the ship
  itself doesn't override.
- **Subspace → jME world-unit conversion** for ship-side stats
  (`Speed`, `Thrust`) — currently each value flows through
  `PlayerDriver` without an explicit boundary. If migration to
  Subspace-canonical authoring (REFERENCE.md units) happens, it
  needs the same scale-and-cap pattern as projectiles.
- **Ship `Radius` resolution** — Subspace's per-ship radius is in
  pixels; Infinity's is in jME world units. The conversion factor
  belongs here.

### F9 — Subspace canon vs Infinity divergence ledger

| Mechanic | Canon | Infinity | Verdict |
|---|---|---|---|
| 2D top-down plane | Implicit (Subspace is 2D) | Y-snap in PlayerDriver | ✅ Faithful (framework-divergence comment) |
| Newton thrust + drag | Yes | car-curve + force-drag | ✅ Faithful in feel; F1 is about implementation idiom |
| Wall bounce | `BounceFactor` (0..16) | `BounceRestitution` (0..1) | ⚠ Divergent type; conversion empirical (slice symptom #3) |
| Wall friction | Frictionless | Per-arena `wallFriction` (0..1) | ⚠ Documented divergence — extension not deviation |
| Wormhole gravity | Per-ship `Gravity` formula | Hardcoded 5000 in MapSystem | ⚠ Canon partially wired (well exists, knob doesn't) |
| Velocity inheritance (projectile) | Yes | Yes (WeaponsSystem step 3) | ✅ Faithful |
| Velocity cap | `MaxSpeed` per ship | `Speed` component + cap in PlayerDriver | ✅ Faithful |
| Bomb recoil | `BombThrust` | Not wired | ❌ Missing canon mechanic |
| Bomb bounce | `BombBounceCount` | Not wired (bombs detonate or decay) | ❌ Missing canon mechanic |
| Afterburner | `AfterburnerEnergy` | Not wired | ❌ Missing canon mechanic |
| Soccer ball physics | `SoccerBallFriction` etc. | Soccer mechanic absent | ❌ Whole mechanic missing |

Most divergences are extensions or unwired-canon, not wrong-feel.
The **bomb recoil + bomb bounce** pair is the largest faithfulness
gap with concrete gameplay impact — a Subspace player will notice
their bomb-fire doesn't push them backward, and that wall-glance
bombs don't bounce.

## Recommended follow-up slices

Ordered by impact ÷ effort. Each is independently shippable.

### S1 — Migrate ship drag from force-coupled to mphys native damping
**Effort:** medium. **Impact:** large (decouples a unit confusion;
cuts custom integrator code).

Add `linearDamping` / `angularDamping` to `ShipConfig` (Pattern 4).
Project to per-entity components at spawn. Add a small post-spawn
hook in `ShipSpawnSystem` that calls `body.setDamping(linear,
angular)` once. Delete the force-based drag branch in
`PlayerDriver`. Calibration pass to land trench/deva at the same
ship-feel they have today.

**Risk:** ship feel change requires per-ship recalibration; acceptable
because the existing values aren't "right" — they're an artifact of
the coupling.

### S2 — Wire `BombThrust` (bomb recoil)
**Effort:** small. **Impact:** medium (canon-faithful gameplay).

Per-ship `BombThrust` ECS component. `WeaponsSystem.canAttackBomb` /
`createProjectileBomb` hook applies an `Impulse` to the firing ship
body opposite the bomb-fire direction (uses the F2 path). REFERENCE.md
canon: `BombThrust 400` for trench warbird (= back-impulse magnitude
in Subspace velocity units). Slice O1 / engine-tier scale converts.

**Risk:** none — canon mechanic, additive.

### S3 — Wire `BombBounceCount`
**Effort:** medium. **Impact:** medium (canon-faithful gameplay; opens
a bomb-physics design surface).

Per-projectile `BombBouncesRemaining(int)` ECS component. ContactSystem
on bomb-vs-static hit decrements; if `> 0`, applies bounce (existing
`BounceRestitution` path) instead of detonating. When `== 0`,
detonates as today.

**Risk:** small. Need to decide whether bomb-vs-bomb / bomb-vs-ship
counts as a bounce (canon: probably not — only walls). Fixed by the
`body2 == null` (static) branch already in ContactSystem.

### S4 — Promote scans to mphys spatial queries
**Effort:** medium. **Impact:** large at high arena counts.

Replace `ProximityFuseSystem.tryArm` + `WeaponsSystem.applySplashDamage`
+ `WeaponsSystem.bombSafetyClear` per-tick EntitySet walks with
`PhysicsSpace.queryBounds`. Keep the FF / arena / victim-type filters
in application code. Sits naturally inside Slice P1 (already queued).

**Risk:** behavioural deltas if `queryBounds` returns bodies the
EntitySet walks were missing (sleeping bodies?) — verify in test.

### S5 — Wire `Repel` impulse (Slice 1 follow-up)
**Effort:** small. **Impact:** medium (completes Slice 1).

New `RepelSystem` (parallels `ProximityFuseSystem`) reads new repel
effect entities, queries bodies within `RepelDistance`, applies
`Impulse` away from the centre with magnitude `RepelSpeed`. Uses
the F2 (sio2-mphys `Impulse` component) path.

**Risk:** small. Canonical, additive.

### S6 — Promote ship `Radius` to per-ship typed config
**Effort:** small. **Impact:** small (consistency with Pattern 4).

Replace `CorePhysicsConstants.SHIPSIZERADIUS = 1` with a per-ship
`shipRadius` field on `ShipConfig`, projected to the ship's body shape
at spawn. Subspace canon: 14 px (= 14/16 = 0.875 jME tiles by today's
conversion). Engine-tier conversion factor (F8) is the natural home
for the px↔jME translation.

**Risk:** trivial — value is the same on every ship in current
fragments.

### S7 — Wire `MineSpeed` (eliminate the mine special-case)
**Effort:** small. **Impact:** small (cleanup; consistency with Slice
10).

Per-ship `MineSpeed` component (default 0). Slot it into the bomb-
spawn pipeline. Delete the `if (weaponFlag == MINE) { … set(0,0,0); }`
branch.

**Risk:** none.

### S8 — CorePhysicsConstants cleanup
**Effort:** trivial. **Impact:** small (cleanup).

Delete dead constants (`SHIPMASS`, `SHIPTHRUST`, `PHYSICS_SCALE`,
likely some of the `OVER*MASS`). Move `ARENAWIDTH` to
`InfinityConstants` per the world-coordinates rule. Pure deletion +
one move.

**Risk:** none — verified dead via grep.

### S9 (deferred / open) — Wormhole `Gravity` per-ship
**Effort:** medium. **Impact:** medium (canon-faithful wormholes).

Per-ship `Gravity(int)` and `GravityTopSpeed` components projected
from `ShipConfig`. `GravityWellSystem` reads each ship's per-ship
gravity to compute pull radius `R = 1.325 × g^0.507`. Replace the
hardcoded `5000` in `MapSystem.451` with a per-wormhole value
(probably zone-tier).

**Risk:** medium. Subspace's gravity is per-ship-experiences-pull,
not per-wormhole-emits-pull, which inverts the natural ECS shape
(today: gravity-well entity has the force; canon: ship has the
susceptibility). Needs a design pass before sliceification.

### S10 (deferred) — Afterburner mechanic
**Effort:** medium. **Impact:** medium (canon mechanic).

Self-contained slice once the input-binding queue catches up.
`AfterburnerEnergy` per-ship + new client input + temporary
`Speed`/`Thrust` boost while held. Sits with Slice 14 (multifire
firing modes) in the per-ship-input-mechanic queue.

## Open questions deferred to slice grilling

- **F1 calibration**: do we recalibrate trench/deva ship feel under
  native damping during S1, or land S1 with a behaviour-preserving
  conversion (`linear = pow(0.5, dragFactor / accelRate)` or similar
  fit)? Math fit is the safer first move; calibration is a polish-bag
  follow-up.
- **F5 BounceFactor unification**: do we accept the
  Subspace-canon-int-vs-Infinity-double divergence permanently, or
  add a Subspace-flavour adapter so operators can author `bounceFactor
  16` and the loader translates? Probably the latter once any other
  per-ship Subspace knob comes through (operators are already
  authoring `cloak status: 2`).
- **F9 priority**: bomb recoil (S2) vs bomb bounce (S3) — which one
  hurts the canon-faithfulness story more? Probably recoil; bounce is
  rare in modern Subspace play.

## Survey scope — what mphys / sio2-mphys files were NOT scanned

Transparency note: the framework survey was a delegated investigation
(Explore agent). It did not enumerate every file in the libraries.
Findings F1–F4 above rest on what *was* scanned; gaps below could
contain primitives that change the picture.

### mphys — `libs/m2/com/simsilica/mphys/1.0.0-SNAPSHOT/` (40 source files)

**Cited directly in the survey** (8): `RigidBody`, `PhysicsSpace`,
`Contact`, `BodyMass`, `AbstractShape`, `Joint`, `QueryFilter`,
`ContactListener`.

**Mentioned in the survey content** (6): `TandemContactResolver`,
`BinIndex`, `AnnealingFunction`, `FilteredContactListener`,
`ControlDriver`, `AbstractControlDriver`.

**Not surveyed** (26):

- `AbstractBody.java`
- `BallJoint.java`
- `Bin.java`
- `BinListener.java`
- `CollisionSystem.java`
- `ContactAccumulator.java`
- `ContactResolver.java`
- `DefaultAnnealingFunction.java`
- `DefaultContactListener.java`
- `DynamicHingeJoint.java`
- `DynArray.java`
- `Frustum.java`
- `Hit.java`
- `HitResults.java`
- `LargeStaticBinDriver.java`
- `PhysicsFactory.java`
- `PhysicsListener.java`
- `PhysicsStats.java`
- `QueryVolume.java`
- `simple/SimpleCollisionSystem.java`
- `simple/SimpleShape.java`
- `SphereVolume.java`
- `SpringJoint.java`
- `StaticBody.java`
- `StaticHingeJoint.java`
- `UprightDriver.java`

**Concrete things the gaps could change:**
- `Hit` / `HitResults` / `QueryVolume` / `SphereVolume` / `Frustum` —
  the spatial-query API surface F3/S4 depend on. The survey claims
  `queryBounds` / `queryHits` exist; these files would confirm
  signatures and capabilities (e.g., does query-by-volume support
  arbitrary shapes or only spheres?).
- `UprightDriver` — a built-in `ControlDriver` that locks one axis.
  Might subsume PlayerDriver's Y-snap workaround (F4 footnote).
- `BallJoint` / `SpringJoint` / `DynamicHingeJoint` / `StaticHingeJoint`
  — the joint primitives. Relevant if Slice 15 (Turrets) ever lands;
  not relevant to F1–F9.
- `LargeStaticBinDriver` — large-static body integration (already
  used via `CollidesWithLargeStatics` component). Worth checking if
  there are missed primitives for the wormhole / portal / brick
  static-marker entities.
- `simple/SimpleCollisionSystem` / `simple/SimpleShape` — the
  reference collision impl. mblock-based shapes are what Infinity
  uses; the simple variants likely don't apply but worth confirming.
- `PhysicsFactory` — entry point for setting up a `PhysicsSpace`.
  Possibly relevant to S1 (where would default damping be set
  globally?).
- `DefaultContactListener` / `DefaultAnnealingFunction` — defaults
  to compare ContactSystem and the sleep-temperature heuristic
  against. Low signal.

### sio2-mphys — `libs/m2/com/simsilica/sio2-mphys/1.0.0-SNAPSHOT/` (22 source files)

**Cited directly in the survey** (6): `MPhysSystem`, `EntityBodyFactory`,
`Mass`, `Impulse`, `Gravity`, `ShapeFactory`.

**Mentioned in the survey content** (4): `BinEntityManager`,
`SpawnPosition`, `ShapeInfo`, `CompositeShapeFactory`.

**Not surveyed** (12):

- `com/simsilica/es/GridComponentIndex.java`
- `com/simsilica/es/SpatialEntityContainer.java`
- `com/simsilica/es/SpawnPositionIndex.java`
- `com/simsilica/ext/mphys/debug/BinStatusState.java`
- `com/simsilica/ext/mphys/debug/BodyDebugState.java`
- `com/simsilica/ext/mphys/debug/ContactDebugState.java`
- `com/simsilica/ext/mphys/debug/DebugShapeFactory.java`
- `com/simsilica/ext/mphys/ObjectStatusAdapter.java`
- `com/simsilica/ext/mphys/ObjectStatusListener.java`
- `com/simsilica/ext/mphys/ShapeFactoryRegistry.java`
- `com/simsilica/ext/mphys/net/ZoneNetworkListener.java`
- `com/simsilica/ext/mphys/net/ZoneNetworkSystem.java`

**Concrete things the gaps could change:**
- `SpatialEntityContainer` / `GridComponentIndex` /
  `SpawnPositionIndex` — entity-side spatial indexing primitives.
  Plausibly relevant to F3/S4 (the per-tick scan replacements). If
  these provide an ECS-flavoured alternative to mphys's
  `queryBounds`, S4's implementation shape changes.
- `ObjectStatusAdapter` / `ObjectStatusListener` — body
  add/remove/update lifecycle hooks. Possibly relevant to S1's
  "post-spawn hook to call `body.setDamping`" — could be the right
  seam.
- `net/ZoneNetwork*` — zone-network bindings. Out of scope for the
  audit; pure networking surface.
- `debug/*` (4 files) — debug visualization states. Out of scope.

### Adjacent modules that might host physics primitives

`libs/m2/com/simsilica/` enumerates: `mblock`, `mblock-physb`,
`mphys`, `sio2-mblock`, `sio2-mphys`. The audit covered `mphys`
+ `sio2-mphys`. Specifically **not surveyed**:

- `mblock-physb` — mblock + physics binding. Likely wraps
  `MBlockShape` for mphys (Infinity's `RigidBody<EntityId,
  MBlockShape>` typing already touches this). Could host static-body
  shape primitives (door / wormhole / brick markers) that shape
  S6 (per-ship Radius config) and a future bricks-as-walls slice.
- `sio2-mblock` — SiO2 + mblock bindings. Likely peripheral to
  physics; mostly map / rendering. Lower priority.
- `mblock` itself — chunked block world. Map system territory, not
  ship physics.

If any S-slice lands and its implementation surface drifts, expect
to revisit `mblock-physb` and the unscanned `mphys` files first.

## Status & next step

Slice P2 deliverable lands here. Direct outcomes:

- Eight follow-up slices (S1–S8) sized and ordered for the kanban.
- Two deferred slices (S9–S10) noted for future scoping.
- Three canon-divergence comments to add as documentation:
  - `ContactSystem.newContact` — wall-friction-as-tangential-damping
    rationale (already partially inline; promote to Javadoc).
  - `PlayerDriver.update` — Y-snap rationale (already inline).
  - `ArenaConfig.wallFriction` — frictionless-canon divergence
    (already documented in field Javadoc).

Recommend picking S1 (drag → native damping) or S2 (bomb recoil) as
the immediate follow-up, depending on whether the appetite is for
implementation-cleanup-with-ship-feel-risk (S1) or
canon-faithful-additive-feature (S2).
