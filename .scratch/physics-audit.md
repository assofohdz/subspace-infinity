---
slice: P2 — Physics implementation audit
status: complete (deliverable; open follow-ups in BACKLOG.md "Architecture refactors")
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

The audit is investigation + documentation; concrete refactors fall
out as follow-up slices (now tracked in [`BACKLOG.md`](BACKLOG.md)
under "Architecture refactors").

## Surface map

Physics-touching code, by file:

| File | Role | Notes |
|---|---|---|
| `infinity.sim.PlayerDriver` | per-ship `AbstractControlDriver` | car-curve thrust, mphys-native linear damping (`setDamping`, S1 landed), exponential angular ease, `Speed × shipMaxSpeedScale` cap (S1-cal), Y-snap |
| `infinity.systems.ContactSystem` | global `ContactListener` | reads `BounceRestitution` per-body; reimplements wall friction via custom tangential damping (sets `contact.friction = 0`) |
| `infinity.systems.ship.WeaponsDamageLogic` | bomb detonation splash | `physicsSpace.queryBounds` + Health-bearer gate (S4 landed in arch-review-tier1-bundle, commit `55a1f4b2`) |
| `infinity.systems.ship.WeaponsEligibility` | fire-attempt safety check | `physicsSpace.queryBounds` (S4 landed) |
| `infinity.systems.ship.ProximityFuseSystem` | per-tick proximity arming | `physicsSpace.queryBounds` (S4 landed) |
| `infinity.systems.ship.ConsumableSystem.getActionPosition` | thor / repel / brick / decoy / portal / rocket spawn | thors hardcoded `addLocal(0, 0, 50)`; repel/brick/decoy/portal are no-velocity markers; rocket is a buff |
| `infinity.systems.ship.WarpSystem` | teleport / spawn | uses `physicsSpace.teleport`; explicitly zeros velocity / acceleration / accumulators |
| `infinity.systems.LegacyMapProjector` | wormhole + door creation | hardcoded gravity-well force `5000`, `GravityWell.PULL` (open: per-ship `Gravity` per S9 in BACKLOG) |
| `api.infinity.sim.ShipFactory.createShip` | ship body composition | `Mass(1)`, `Gravity.ZERO`; tunable physics knobs deferred to `ShipSpawnSystem` projection |
| `api.infinity.config.EngineConfig` | engine-tier physics knobs | `subspaceVelocityScale`, `maxProjectileSpeedJme`, `shipMaxSpeedScale` (S1-cal), `bombThrustScale` (S2-cal), and 12 collision radii (bullet/bomb/mine/thor/prize/burst/repel/over1/over2/over5/flag/ship — slices projectile-radius-pattern4 + s6-ship-radius) |
| `api.infinity.config.ArenaConfig.wallFriction` | per-arena friction | divergence from canon (canon = frictionless), documented |

Note: projectile spawn velocity / inheritance / bomb-recoil logic
that was originally `WeaponsSystem.getAttackInfo` /
`applySplashDamage` lives across `WeaponsDamageLogic`,
`WeaponsEligibility`, and the consumable-spawn path under
`infinity.systems.ship.*` (split during arch-review). Inheritance
gating still uses the `INERT_DROPS` set.

## Findings

### F5 — Subspace canon physics knobs unwired

Authored in `ships.groovy` / `misc.groovy` but no typed Infinity
consumer reads them. The per-key live tracker is in
[`settings-pipeline.md`](settings-pipeline.md):

| Subspace key | REFERENCE.md role | Status |
|---|---|---|
| `BombBounceCount` | Bombs bounce N times before impact-explode | Authored, no consumer (BACKLOG: S3 design question) |
| `AfterburnerEnergy` | Afterburner activation cost | Authored, no consumer (BACKLOG: S10) |
| `Gravity` (per-ship) | Wormhole pull radius `R = 1.325 × g^0.507` | Authored, no consumer; `LegacyMapProjector` hardcodes 5000 (BACKLOG: S9) |
| `GravityTopSpeed` | Extra speed allowed under wormhole pull | Not wired (rolls into S9) |
| `BounceFactor` | Wall bounciness (0..16, 16=no speed loss) | Per-ship `BounceRestitution` (0..1 double) — divergent type/scope; conversion empirical |
| `SoccerBallFriction` | Soccer ball deceleration | Authored, no consumer; soccer mechanic absent |

Bomb bounces is the highest-gameplay-impact remaining knob; afterburner
is a self-contained mechanic; gravity is an arena-wide consequence.

### F9 — Subspace canon vs Infinity divergence ledger

| Mechanic | Canon | Infinity | Verdict |
|---|---|---|---|
| 2D top-down plane | Implicit (Subspace is 2D) | Y-snap in PlayerDriver | ✅ Faithful (framework-divergence comment) |
| Newton thrust + drag | Yes | car-curve + mphys `setDamping` (S1 landed) | ✅ Faithful |
| Wall bounce | `BounceFactor` (0..16) | `BounceRestitution` (0..1) | ⚠ Divergent type; conversion empirical |
| Wall friction | Frictionless | Per-arena `wallFriction` (0..1) | ⚠ Documented divergence — extension not deviation |
| Wormhole gravity | Per-ship `Gravity` formula | Hardcoded 5000 in `LegacyMapProjector` | ⚠ Canon partially wired (well exists, knob doesn't) |
| Velocity inheritance (projectile) | Yes | Yes (`INERT_DROPS` gate) | ✅ Faithful |
| Velocity cap | `MaxSpeed` per ship | `Speed × shipMaxSpeedScale` (S1-cal) | ✅ Faithful |
| Bomb recoil | `BombThrust` | Wired (S2 + S2-cal) | ✅ Faithful |
| Bomb bounce | `BombBounceCount` | Not wired (bombs detonate or decay) | ❌ Missing canon mechanic |
| Afterburner | `AfterburnerEnergy` | Not wired | ❌ Missing canon mechanic |
| Soccer ball physics | `SoccerBallFriction` etc. | Soccer mechanic absent | ❌ Whole mechanic missing |

Most divergences are extensions or unwired-canon, not wrong-feel.
**Bomb bounce** is the largest concrete-gameplay faithfulness gap —
a Subspace player will notice that wall-glance bombs don't bounce.

## Open design question

**BounceFactor unification** — do we accept the
Subspace-canon-int-vs-Infinity-double divergence permanently, or add a
Subspace-flavour adapter so operators can author `bounceFactor 16`
and the loader translates? Probably the latter once any other
per-ship Subspace knob comes through (operators are already authoring
`cloak status: 2`).

## mphys / sio2-mphys survey scope

The framework survey was a delegated investigation (Explore agent).
It did not enumerate every file in `libs/m2/com/simsilica/mphys/`
(40 source files, ~14 cited / mentioned) or
`libs/m2/com/simsilica/sio2-mphys/` (22 source files, ~10 cited /
mentioned). Adjacent libs `mblock-physb`, `sio2-mblock`, and `mblock`
itself were also out of scope. If a follow-up slice surfaces a new
mphys primitive that changes the implementation shape (e.g. an
`UprightDriver` that subsumes PlayerDriver's Y-snap, or
`SpatialEntityContainer` as an ECS-flavoured alternative to
`queryBounds`), revisit those gaps then; the audit's findings rest on
what was scanned, not on a complete file listing.

## Status

Slice P2 deliverable complete. Spatial-query promotion (S4) landed in
the arch-review tier-1 bundle (commit `55a1f4b2`). Open follow-ups —
S3 (bomb-bounce design question), S9 (wormhole `Gravity` per-ship),
and S10 (afterburner mechanic) — relocated to [`BACKLOG.md`](BACKLOG.md)
"Architecture refactors". Per-key wiring status for the unwired
Subspace knobs (F5) is tracked in [`settings-pipeline.md`](settings-pipeline.md).
