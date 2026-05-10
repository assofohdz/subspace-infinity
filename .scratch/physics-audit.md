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
| `infinity.sim.PlayerDriver` | per-ship `AbstractControlDriver` | car-curve thrust, mphys-native linear damping (`setDamping`, S1), exponential angular ease, hand-coded vMax cap (`setLinearVelocity`), Y-snap |
| `infinity.systems.ContactSystem` | global `ContactListener` | reads `BounceRestitution` per-body; reimplements wall friction via custom tangential damping (sets `contact.friction = 0`) |
| `infinity.systems.ship.WeaponsSystem.getAttackInfo` | projectile spawn | reads `BulletSpeed` / `BombSpeed` / `BurstSpeed` / `MineSpeed` × `EngineConfig.subspaceVelocityScale`, capped at `maxProjectileSpeedJme`; ship-velocity inheritance gated on `INERT_DROPS` |
| `infinity.systems.ship.ConsumableSystem.getActionPosition` | thor / repel / brick / decoy / portal / rocket spawn | thors hardcoded `addLocal(0, 0, 50)`; repel/brick/decoy/portal are no-velocity markers; rocket is a buff |
| `infinity.systems.ship.ProximityFuseSystem` | per-tick proximity arming | manually walks `EntitySet(Health.class)` for every in-flight bomb |
| `infinity.systems.ship.WeaponsSystem.applySplashDamage` | bomb detonation | manually walks `EntitySet(Health.class)` for every detonation |
| `infinity.systems.ship.WarpSystem` | teleport / spawn | uses `physicsSpace.teleport`; explicitly zeros velocity / acceleration / accumulators |
| `infinity.systems.LegacyMapProjector` | wormhole + door creation | hardcoded gravity-well force `5000`, `GravityWell.PULL` |
| `api.infinity.sim.ShipFactory.createShip` | ship body composition | `Mass(1)`, `Gravity.ZERO`; tunable physics knobs deferred to `ShipSpawnSystem` projection |
| `api.infinity.config.EngineConfig` | engine-tier physics knobs | Subspace→jME velocity scale + cap, ship/bomb scale calibrations (S1-cal/S2-cal), and 12 collision radii (bullet/bomb/mine/thor/prize/burst/repel/over1/over2/over5/flag/ship — projectile-radius-pattern4 + S6) |
| `api.infinity.config.ArenaConfig.wallFriction` | per-arena friction | divergence from canon (canon = frictionless), documented |

## Findings

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

`grep -r "queryBounds\|queryHits\|queryContacts" infinity-server/src infinity-client/src` → zero
hits.

**Slice P1** (already queued) is the right home for this. The
spatial-query swap is one of the lower-risk moves to make since the
ECS-side filtering (FF gate, arena membership, victim type) stays
unchanged.

### F5 — Subspace canon physics knobs unwired

`svsSettings.cfg` contains canonical Subspace per-ship knobs that no
typed Infinity consumer reads:

| Subspace key | REFERENCE.md role | Infinity status |
|---|---|---|
| `BombBounceCount` | Bombs bounce N times before impact-explode | Authored in cfg, no consumer |
| `AfterburnerEnergy` | Afterburner activation cost | Authored in cfg, no consumer |
| `SoccerBallFriction` | Soccer ball deceleration | Authored in cfg, no consumer; soccer mechanic absent |
| `Gravity` (per-ship) | Wormhole pull radius `R = 1.325 × g^0.507` | Wormholes exist (`GravityWell`), but pull is hardcoded `5000` in `LegacyMapProjector` — no per-ship `Gravity` read |
| `GravityTopSpeed` | Extra speed allowed under wormhole pull | Not wired |
| `BounceFactor` | Wall bounciness (0..16, 16=no speed loss) | Per-ship `BounceRestitution` (0..1 double) — divergent type/scope; conversion empirical |

Each row is a candidate for the settings-pipeline tracker. Bomb bounces
is the highest-gameplay-impact remaining knob; afterburner is a
self-contained mechanic; gravity is an arena-wide consequence.

### F8 — Engine-tier config has natural room to grow

`engine.groovy` today is two fields (`subspaceVelocityScale`,
`maxProjectileSpeedJme`). The audit surfaces several engine-tier
candidates:

- **Default per-body damping** — when migrating F1, the integrator
  fallback values (mphys's 0.9 / 0.8) deserve to live here, not in
  per-ship config, since they apply to every dynamic body the ship
  itself doesn't override.
- **Subspace → jME world-unit conversion** for ship-side `Thrust`.
  (`Speed` already wired via engine-tier `shipMaxSpeedScale`, slice
  S1-cal — `PlayerDriver.java:113` reads `speed.getSpeed() *
  shipScale`.) `Thrust` still flows through `PlayerDriver` without
  an engine-tier scale.

### F9 — Subspace canon vs Infinity divergence ledger

| Mechanic | Canon | Infinity | Verdict |
|---|---|---|---|
| 2D top-down plane | Implicit (Subspace is 2D) | Y-snap in PlayerDriver | ✅ Faithful (framework-divergence comment) |
| Newton thrust + drag | Yes | car-curve + force-drag | ✅ Faithful in feel; F1 is about implementation idiom |
| Wall bounce | `BounceFactor` (0..16) | `BounceRestitution` (0..1) | ⚠ Divergent type; conversion empirical (slice symptom #3) |
| Wall friction | Frictionless | Per-arena `wallFriction` (0..1) | ⚠ Documented divergence — extension not deviation |
| Wormhole gravity | Per-ship `Gravity` formula | Hardcoded 5000 in `LegacyMapProjector` | ⚠ Canon partially wired (well exists, knob doesn't) |
| Velocity inheritance (projectile) | Yes | Yes (WeaponsSystem step 3) | ✅ Faithful |
| Velocity cap | `MaxSpeed` per ship | `Speed` component + cap in PlayerDriver | ✅ Faithful |
| Bomb recoil | `BombThrust` | Wired (slices S2 + S2-cal) | ✅ Faithful |
| Bomb bounce | `BombBounceCount` | Not wired (bombs detonate or decay) | ❌ Missing canon mechanic |
| Afterburner | `AfterburnerEnergy` | Not wired | ❌ Missing canon mechanic |
| Soccer ball physics | `SoccerBallFriction` etc. | Soccer mechanic absent | ❌ Whole mechanic missing |

Most divergences are extensions or unwired-canon, not wrong-feel.
**Bomb bounce** is now the largest concrete-gameplay faithfulness
gap — a Subspace player will notice that wall-glance bombs don't
bounce. (Bomb recoil wired in slices S2 + S2-cal.)

## Recommended follow-up slices

Ordered by impact ÷ effort. Each is independently shippable.

### S4 — Promote scans to mphys spatial queries
**Effort:** medium. **Impact:** large at high arena counts.

Replace `ProximityFuseSystem.tryArm` + `WeaponsSystem.applySplashDamage`
+ `WeaponsSystem.bombSafetyClear` per-tick EntitySet walks with
`PhysicsSpace.queryBounds`. Keep the FF / arena / victim-type filters
in application code. Sits naturally inside Slice P1 (already queued).

**Risk:** behavioural deltas if `queryBounds` returns bodies the
EntitySet walks were missing (sleeping bodies?) — verify in test.

### S9 (deferred / open) — Wormhole `Gravity` per-ship
**Effort:** medium. **Impact:** medium (canon-faithful wormholes).

Per-ship `Gravity(int)` and `GravityTopSpeed` components projected
from `ShipConfig`. `GravityWellSystem` reads each ship's per-ship
gravity to compute pull radius `R = 1.325 × g^0.507`. Replace the
hardcoded `5000` in `LegacyMapProjector` with a per-wormhole value
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

- **F5 BounceFactor unification**: do we accept the
  Subspace-canon-int-vs-Infinity-double divergence permanently, or
  add a Subspace-flavour adapter so operators can author `bounceFactor
  16` and the loader translates? Probably the latter once any other
  per-ship Subspace knob comes through (operators are already
  authoring `cloak status: 2`).

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
  a future bricks-as-walls slice.
- `sio2-mblock` — SiO2 + mblock bindings. Likely peripheral to
  physics; mostly map / rendering. Lower priority.
- `mblock` itself — chunked block world. Map system territory, not
  ship physics.

If any S-slice lands and its implementation surface drifts, expect
to revisit `mblock-physb` and the unscanned `mphys` files first.

## Status & next step

Slice P2 deliverable lands here. Direct outcomes:

- One follow-up slice remaining (S4 — promote scans to mphys spatial
  queries). Three deferred slices (S9 wormhole gravity, S10
  afterburner, plus S3 bomb bounce as an open design question).
