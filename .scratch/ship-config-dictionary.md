# Ship Config Dictionary — typed `ShipConfig` port status

Tracks the per-ship tuning surface and which keys have been promoted to the typed Groovy `ShipConfig` template (Config-Component Projection per [ADR-0002](../docs/adr/0002-config-component-projection.md) — `ship(Ship.X) { … }` blocks in `ships.groovy`) vs. which still live as untyped `shipSection 'X' { Key value }` blocks in the per-preset Groovy fragments under `zone/conf/<preset>/ship-<name>.groovy`.

**Scope:** the 84 keys per ship that appear in every `ship-<name>.groovy` fragment under `zone/conf/trench-04-2026/` (verified to be the same set across all 8 ships). The same key set holds for the SVS preset family. (These keys came from the original Subspace `shipSection` surface; the conf-fragments-to-groovy migration ported the bag verbatim into `shipSection` blocks — the names and values are unchanged.)

**Why this file exists:** Always-on rule #3 in [CLAUDE.md](../CLAUDE.md) says "tuning knobs go in Groovy, not Java". All 84 keys are in Groovy now, but only 15 are *typed* — the rest sit in untyped `shipSection` blocks (read by `SettingsSystem.getInt/getString` if read at all). This dictionary is the running ledger of which knobs are typed (CCP — projected to ECS components at spawn, per [ADR-0002](../docs/adr/0002-config-component-projection.md)) vs. which still flow through the untyped flat-bag accessors. Without it, it's hard to answer "is `BulletFireEnergy` already a typed field, or do I need to add it?"

## How to use

- **Adding a new typed `ShipConfig` field that ports a `shipSection` key** → move the row from "Pending" to "Ported" and fill in the Groovy DSL name, `ShipConfig` field, projected component(s), and consumer.
- **Adding a brand-new typed field that has no fragment source** (e.g. `linearDamping`, `turnResponsiveness`, `bounceRestitution`) → add a row in the "Infinity-only Groovy fields (no fragment source)" table.
- **Extending the per-ship key surface** (a new `shipSection` key appears) → add a row in "Pending" with status `Pending — not yet read by any consumer`.
- **Removing a typed field** → either move the row back to "Pending" (if the `shipSection` key still exists) or delete it (if both are gone).

Update this file in the same change that adds/moves/removes a typed config field. See always-on rule #4 in [CLAUDE.md](../CLAUDE.md).

---

## Ported — `shipSection` keys with a typed `ShipConfig` binding

32 keys (5 stat triples + 3 rocket inventory/lifetime + 2 brick inventory + 2 cloak + 2 stealth + 2 xradar + 2 antiwarp + 3 projectile speeds + 1 bomb recoil). All projected at spawn by [`ShipSpawnSystem`](../infinity-server/src/main/java/infinity/systems/ship/ShipSpawnSystem.java) into per-entity ECS components.

| `shipSection` key | Groovy DSL (in `ships.groovy`) | `ShipConfig` field | Projected component(s) | Hot-path consumer(s) |
|---|---|---|---|---|
| `InitialRotation` | `rotation initial:` | `rotation.initial()` | `Rotation` (rad/sec; ×2π/400 at projection) | `PlayerDriver.update()` |
| `MaximumRotation` | `rotation max:` | `rotation.max()` | `RotationMax` | `RotationPrizeApplier` |
| `UpgradeRotation` | `rotation upgrade:` | `rotation.upgrade()` | `RotationUpgrade` | `RotationPrizeApplier` |
| `InitialThrust` | `thrust initial:` | `thrust.initial()` | `Thrust` | `PlayerDriver.update()` |
| `MaximumThrust` | `thrust max:` | `thrust.max()` | `ThrustMax` | `ThrusterPrizeApplier` |
| `UpgradeThrust` | `thrust upgrade:` | `thrust.upgrade()` | `ThrustUpgrade` | `ThrusterPrizeApplier` |
| `InitialSpeed` | `speed initial:` | `speed.initial()` | `Speed` | `PlayerDriver.update()` |
| `MaximumSpeed` | `speed max:` | `speed.max()` | `SpeedMax` | `TopSpeedPrizeApplier` |
| `UpgradeSpeed` | `speed upgrade:` | `speed.upgrade()` | `SpeedUpgrade` | `TopSpeedPrizeApplier` |
| `InitialRecharge` | `recharge initial:` | `recharge.initial()` | `Recharge` (energy/sec; ÷10 at projection) | `EnergySystem.update()` |
| `MaximumRecharge` | `recharge max:` | `recharge.max()` | `RechargeMax` | `RechargePrizeApplier` |
| `UpgradeRecharge` | `recharge upgrade:` | `recharge.upgrade()` | `RechargeUpgrade` | `RechargePrizeApplier` |
| `InitialEnergy` | `energy initial:` | `energy.initial()` | `Health` (live pool) + `Energy` (cap) | `EnergySystem.update()` (both); `WeaponsSystem` / `WarpSystem` filter on `Health` |
| `MaximumEnergy` | `energy max:` | `energy.max()` | `EnergyMax` | `EnergyPrizeApplier` |
| `UpgradeEnergy` | `energy upgrade:` | `energy.upgrade()` | `EnergyUpgrade` | `EnergyPrizeApplier` |
| `InitialRocket` | `rockets start:` | `rockets.start()` | `Rocket` | `RocketPrizeApplier` (reads cap to gate); `ConsumableSystem.canFireRocket` |
| `RocketMax` | `rockets max:` | `rockets.max()` | `RocketMax` | `RocketPrizeApplier` (cap check) |
| `RocketTime` | `rockets activeTimeCs:` (cs×10→ms at projection) | `rockets.activeTimeCs()` | `RocketTime` (ms) | `ConsumableSystem.createRocketBuff` (buff entity Decay deadline); `RocketBuffSystem` (revert seam) |
| `InitialBrick` | `bricks start:` | `bricks.start()` | `Brick` | `BrickPrizeApplier` (reads cap to gate); `ConsumableSystem.canPlaceBrick` |
| `BrickMax` | `bricks max:` | `bricks.max()` | `BrickMax` | `BrickPrizeApplier` (cap check) |
| `CloakStatus` | `cloak status:` | `cloak.status()` | `CloakStatus` | `CloakPrizeApplier` (tri-state gate); `ShipSpawnSystem.projectCloak` (seed `Cloak` toggle on `status==2`) |
| `CloakEnergy` | `cloak energy:` | `cloak.energyDrainPer1000Cs()` | `CloakEnergy` | `StatusDrainSystem.update` (drain rate while toggle on) |
| `StealthStatus` | `stealth status:` | `stealth.status()` | `StealthStatus` | `StealthPrizeApplier` (tri-state gate); `ShipSpawnSystem.projectStealth` |
| `StealthEnergy` | `stealth energy:` | `stealth.energyDrainPer1000Cs()` | `StealthEnergy` | `StatusDrainSystem.update` |
| `XRadarStatus` | `xradar status:` | `xradar.status()` | `XRadarStatus` | `XRadarPrizeApplier` (tri-state gate); `ShipSpawnSystem.projectXRadar` |
| `XRadarEnergy` | `xradar energy:` | `xradar.energyDrainPer1000Cs()` | `XRadarEnergy` | `StatusDrainSystem.update` |
| `AntiWarpStatus` | `antiwarp status:` | `antiwarp.status()` | `AntiwarpStatus` | `AntiWarpPrizeApplier` (tri-state gate); `ShipSpawnSystem.projectAntiwarp` |
| `AntiWarpEnergy` | `antiwarp energy:` | `antiwarp.energyDrainPer1000Cs()` | `AntiwarpEnergy` | `StatusDrainSystem.update` |
| `BulletSpeed` | `bullets speed:` | `bullets.speed()` | `BulletSpeed` (raw Subspace velocity units) | `WeaponsSystem.getAttackInfo` (case BULLET) → `effectiveProjectileSpeed(speed, EngineConfig.subspaceVelocityScale, .maxProjectileSpeedJme)` |
| `BombSpeed` | `bombs speed:` | `bombs.speed()` | `BombSpeed` | `WeaponsSystem.getAttackInfo` (case BOMB) → `effectiveProjectileSpeed(...)` |
| `BombThrust` | `bombs thrust:` | `bombs.thrust()` | `BombThrust` (raw Subspace velocity units) | `WeaponsSystem.applyBombRecoil` (hooked from `createProjectileBomb` + `createProjectileGravBomb`) → `effectiveProjectileSpeed(...)` → `Impulse` opposite ship forward. Slice S2. |
| `BurstSpeed` | `bursts speed:` | `bursts.speed()` | `BurstSpeed` | `WeaponsSystem.getAttackInfo` (case BURST — slice 10 latent fix) → `effectiveProjectileSpeed(...)` |

## Infinity-only Groovy fields (no fragment source)

Added during CCP follow-up #4. Defaults match the historical Java globals so existing Groovy scripts keep the prior feel.

| Groovy DSL | `ShipConfig` field | Projected component | Hot-path consumer | Default | Notes |
|---|---|---|---|---|---|
| `linearDamping` | `linearDamping()` | `LinearDamping` | `PlayerDriver.update()` → `RigidBody.setDamping(linear, 1.0)` | `0.99` | Slice S1. Per-second velocity-retention multiplier (mphys-native). `0.99` = 1% loss/sec at typical speed; math fit against historical `dragFactor 0.05` coast-decay rate. Always-on (max-speed reach lands ~5% short). Continuum has no drag — Infinity extension. |
| `turnResponsiveness` | `turnResponsiveness()` | `TurnResponsiveness` | `PlayerDriver.update()` | `8.0` | Angular-velocity ease rate (1/sec). Continuum has no equivalent. |
| `bounceRestitution` | `bounceRestitution()` | `BounceRestitution` | `ContactSystem.newContact()` | `1.0` | Wall-bounce energy retention. Continuum walls are perfectly elastic by construction. |
| `radarRange` | `radarRange()` | `RadarRange` | `RadarState` | `250.0` | World-unit radius the client radar viewport displays around the ship. Spawn also projects `RadarShapeInfo` (server-side, not from a `ShipConfig` field) — the blip name is derived from `cfg.type().getName() + "_blip"`. |

---

## Pending — `shipSection` keys not yet ported to typed `ShipConfig`

60 keys, grouped by purpose. None are read through a typed `ShipConfig` field today; some are read via the untyped `SettingsSystem.getInt/getString` accessors against the per-arena merged fragment store, others have no consumer at all (orphan config).

### Weapons — gun / bomb / mine firing

| `shipSection` key | Notes |
|---|---|
| `BulletFireDelay` | Per-shot cooldown for bullets. |
| `BulletFireEnergy` | Energy cost per bullet shot. |
| `BombFireDelay` | Per-shot cooldown for bombs. |
| `BombFireEnergy` | Energy cost per bomb (level 1 baseline). |
| `BombFireEnergyUpgrade` | Per-level energy delta for bombs. |
| `BombBounceCount` | How many wall-bounces a bomb survives. |
| `EmpBomb` | Whether the ship's bombs deal EMP damage (boolean). |
| `LandmineFireDelay` | Per-shot cooldown for mines. |
| `LandmineFireEnergy` | Energy cost per mine (level 1 baseline). |
| `LandmineFireEnergyUpgrade` | Per-level energy delta for mines. |
| `MultiFireAngle` | Spread angle for multifire shots. |
| `MultiFireDelay` | Cooldown for multifire shots. |
| `MultiFireEnergy` | Energy cost per multifire shot. |
| `DoubleBarrel` | Whether the ship has the double-barrel gun (boolean). |
| `DisableFastShooting` | Anti-spam gate (boolean). |

### Inventory — initial counts and caps

| `shipSection` key | Notes |
|---|---|
| `InitialBombs` / `MaxBombs` | Bomb-level inventory (already partially handled by `Bombs` enum + `BombCurrentLevel`/`BombMaxLevel` components, but not yet driven by Groovy). |
| `InitialGuns` / `MaxGuns` | Same for `Guns` / `BulletCurrentLevel` / `BulletMaxLevel`. |
| `MaxMines` | Mine inventory cap. (No `InitialMines` in the `shipSection` surface.) |
| `InitialBurst` / `BurstMax` | Burst grenades. `Burst` / `BurstMax` components exist; not Groovy-driven. |
| `BurstShrapnel` | Shrapnel count per burst. |
| `InitialRepel` / `RepelMax` | Repel charges. |
| `InitialDecoy` / `DecoyMax` | Decoy charges. |
| `InitialThor` / `ThorMax` | Thor charges. `ThorCurrentCount` / `ThorMaxCount` components exist; not Groovy-driven. |
| `InitialPortal` / `PortalMax` | Portal charges. |
| `ShrapnelMax` / `ShrapnelRate` | Shrapnel-on-bomb-detonation. |

### Abilities — energy costs and on/off

| `shipSection` key | Notes |
|---|---|
| `AfterburnerEnergy` | Energy/sec cost while afterburner is held. |
| `SuperTime` | Duration of the SUPER prize effect. |
| `ShieldsTime` | Duration of the SHIELDS prize effect. |

### Turret

| `shipSection` key | Notes |
|---|---|
| `TurretLimit` | Max riders allowed on this ship. |
| `TurretSpeedPenalty` | Speed reduction per attached turret. |
| `TurretThrustPenalty` | Thrust reduction per attached turret. |

### Bounty / damage / share

| `shipSection` key | Notes |
|---|---|
| `AttachBounty` | Bounty awarded for attaching as a turret. |
| `InitialBounty` | Spawn bounty. |
| `PrizeShareLimit` | Distance threshold for prize-share with teammates. |
| `DamageFactor` | Per-ship damage multiplier (defensive/offensive). |

### Visibility

| `shipSection` key | Notes |
|---|---|
| `SeeBombLevel` | Which bomb levels this ship can see in radar/HUD. |
| `SeeMines` | Whether this ship sees mines. |

### Physics

| `shipSection` key | Notes |
|---|---|
| `Gravity` | Gravity well pull strength (per-ship). |
| `GravityTopSpeed` | Top speed under gravity well influence. |
| `Radius` | Collision radius. (Currently `0` in trench fragments.) |

### Soccer

| `shipSection` key | Notes |
|---|---|
| `SoccerBallFriction` | How fast the ball decelerates when fired by a ship (like throwing a ball) |
| `SoccerBallProximity` | Pickup radius. |
| `SoccerBallSpeed` | Throw speed. |
| `SoccerThrowTime` | Throw cooldown. |

---

## Notes on porting

- **Add fields incrementally.** Don't try to port all 69 in one pass — port the subset a feature actually needs, wire its consumers (per CCP / [ADR-0002](../docs/adr/0002-config-component-projection.md): typed `*Config` field → projection → component → consumer), then update this file.
- **Group related keys into nested config records.** `BulletFireDelay` / `BulletFireEnergy` / `BulletSpeed` likely become a `BulletConfig` record nested in `ShipConfig` rather than 3 flat fields. Same for `BombConfig`, `BurstConfig`, etc. Keep the Groovy DSL ergonomic — flat top-level setters per cluster, like the existing `rotation initial: ..., max: ..., upgrade: ...`.
- **Cap-style stats follow the CCP triple shape** (`initial / max / upgrade`); see [`config-pattern.md`](rules/config-pattern.md) / [ADR-0002](../docs/adr/0002-config-component-projection.md). Pure config with no upgrade axis (e.g. `BombSpeed`) is just a single field.
- **Don't port "orphan" keys** — those without any consumer in the Java code today. Adding them to Groovy creates orphan config (declared, never read) without delivering value. Wire the consumer first or skip.
