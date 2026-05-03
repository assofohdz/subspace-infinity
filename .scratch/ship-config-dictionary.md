# Ship Config Dictionary — typed `ShipConfig` port status

Tracks the per-ship tuning surface and which keys have been promoted to the typed Groovy `ShipConfig` template (Pattern 4 — `ship(Ship.X) { … }` blocks in `ships.groovy`) vs. which still live as untyped `shipSection 'X' { Key value }` blocks in the per-preset Groovy fragments under `infinity/zone/conf/<preset>/ship-<name>.groovy`.

**Scope:** the 84 keys per ship that appear in every `ship-<name>.groovy` fragment under `infinity/zone/conf/trench-04-2026/` (verified to be the same set across all 8 ships). The same key set holds for the SVS preset family. (These keys came from the original Subspace `shipSection` surface; the conf-fragments-to-groovy migration ported the bag verbatim into `shipSection` blocks — the names and values are unchanged.)

**Why this file exists:** Always-on rule #5 in [CLAUDE.md](../CLAUDE.md) says "tuning knobs go in Groovy, not Java". All 84 keys are in Groovy now, but only 15 are *typed* — the rest sit in untyped `shipSection` blocks (read by `SettingsSystem.getInt/getString` if read at all). This dictionary is the running ledger of which knobs are typed (Pattern 4 — projected to ECS components at spawn) vs. which still flow through the untyped flat-bag accessors. Without it, it's hard to answer "is `BulletFireEnergy` already a typed field, or do I need to add it?"

## How to use

- **Adding a new typed `ShipConfig` field that ports a `shipSection` key** → move the row from "Pending" to "Ported" and fill in the Groovy DSL name, `ShipConfig` field, projected component(s), and consumer.
- **Adding a brand-new typed field that has no fragment source** (e.g. `dragFactor`, `turnResponsiveness`, `bounceRestitution`) → add a row in the "Infinity-only Groovy fields (no fragment source)" table.
- **Extending the per-ship key surface** (a new `shipSection` key appears) → add a row in "Pending" with status `Pending — not yet read by any consumer`.
- **Removing a typed field** → either move the row back to "Pending" (if the `shipSection` key still exists) or delete it (if both are gone).

Update this file in the same change that adds/moves/removes a typed config field. See always-on rule #6 in [CLAUDE.md](../CLAUDE.md).

---

## Ported — `shipSection` keys with a typed `ShipConfig` binding

20 keys (5 stat triples + 3 rocket inventory/lifetime + 2 brick inventory). All projected at spawn by [`ShipSpawnSystem`](../infinity/src/main/java/infinity/systems/ship/ShipSpawnSystem.java) into per-entity ECS components.

| `shipSection` key | Groovy DSL (in `ships.groovy`) | `ShipConfig` field | Projected component(s) | Hot-path consumer(s) |
|---|---|---|---|---|
| `InitialRotation` | `rotation initial:` | `rotation.initial()` | `Rotation` (rad/sec; ×2π/400 at projection) | `PlayerDriver.update()` |
| `MaximumRotation` | `rotation max:` | `rotation.max()` | `RotationMax` | `PrizeSystem.handleAcquireRotation()` |
| `UpgradeRotation` | `rotation upgrade:` | `rotation.upgrade()` | `RotationUpgrade` | `PrizeSystem.handleAcquireRotation()` |
| `InitialThrust` | `thrust initial:` | `thrust.initial()` | `Thrust` | `PlayerDriver.update()` |
| `MaximumThrust` | `thrust max:` | `thrust.max()` | `ThrustMax` | `PrizeSystem.handleAcquireThruster()` |
| `UpgradeThrust` | `thrust upgrade:` | `thrust.upgrade()` | `ThrustUpgrade` | `PrizeSystem.handleAcquireThruster()` |
| `InitialSpeed` | `speed initial:` | `speed.initial()` | `Speed` | `PlayerDriver.update()` |
| `MaximumSpeed` | `speed max:` | `speed.max()` | `SpeedMax` | `PrizeSystem.handleAcquireTopSpeed()` |
| `UpgradeSpeed` | `speed upgrade:` | `speed.upgrade()` | `SpeedUpgrade` | `PrizeSystem.handleAcquireTopSpeed()` |
| `InitialRecharge` | `recharge initial:` | `recharge.initial()` | `Recharge` (energy/sec; ÷10 at projection) | `EnergySystem.update()` |
| `MaximumRecharge` | `recharge max:` | `recharge.max()` | `RechargeMax` | `PrizeSystem.handleAcquireRecharge()` |
| `UpgradeRecharge` | `recharge upgrade:` | `recharge.upgrade()` | `RechargeUpgrade` | `PrizeSystem.handleAcquireRecharge()` |
| `InitialEnergy` | `energy initial:` | `energy.initial()` | `Health` (live pool) + `Energy` (cap) | `EnergySystem.update()` (both); `WeaponsSystem` / `WarpSystem` filter on `Health` |
| `MaximumEnergy` | `energy max:` | `energy.max()` | `EnergyMax` | `PrizeSystem.handleAcquireEnergy()` |
| `UpgradeEnergy` | `energy upgrade:` | `energy.upgrade()` | `EnergyUpgrade` | `PrizeSystem.handleAcquireEnergy()` |
| `InitialRocket` | `rockets start:` | `rockets.start()` | `Rocket` | `RocketPrizeApplier` (reads cap to gate); `ConsumableSystem.canFireRocket` |
| `RocketMax` | `rockets max:` | `rockets.max()` | `RocketMax` | `RocketPrizeApplier` (cap check) |
| `RocketTime` | `rockets activeTimeCs:` (cs×10→ms at projection) | `rockets.activeTimeCs()` | `RocketTime` (ms) | `ConsumableSystem.createRocketBuff` (buff entity Decay deadline); `RocketBuffSystem` (revert seam) |
| `InitialBrick` | `bricks start:` | `bricks.start()` | `Brick` | `BrickPrizeApplier` (reads cap to gate); `ConsumableSystem.canPlaceBrick` |
| `BrickMax` | `bricks max:` | `bricks.max()` | `BrickMax` | `BrickPrizeApplier` (cap check) |

## Infinity-only Groovy fields (no fragment source)

Added during Pattern 4 follow-up #4. Defaults match the historical Java globals so existing Groovy scripts keep the prior feel.

| Groovy DSL | `ShipConfig` field | Projected component | Hot-path consumer | Default | Notes |
|---|---|---|---|---|---|
| `dragFactor` | `dragFactor()` | `DragFactor` | `PlayerDriver.update()` | `0.05` | Coast-drag fraction of `Thrust` when no thrust intent. Continuum has no equivalent (client-authoritative). |
| `turnResponsiveness` | `turnResponsiveness()` | `TurnResponsiveness` | `PlayerDriver.update()` | `8.0` | Angular-velocity ease rate (1/sec). Continuum has no equivalent. |
| `bounceRestitution` | `bounceRestitution()` | `BounceRestitution` | `ContactSystem.newContact()` | `1.0` | Wall-bounce energy retention. Continuum walls are perfectly elastic by construction. |
| `radarRange` | `radarRange()` | `RadarRange` | `RadarState` (TBD, issue radar-viewport/02) | `250.0` | World-unit radius the client radar viewport displays around the ship. Spawn also projects `RadarShapeInfo` (server-side, not from a `ShipConfig` field) — the blip name is derived from `cfg.type().getName() + "_blip"`. |

---

## Pending — `shipSection` keys not yet ported to typed `ShipConfig`

64 keys, grouped by purpose. None are read through a typed `ShipConfig` field today; some are read via the untyped `SettingsSystem.getInt/getString` accessors against the per-arena merged fragment store, others have no consumer at all (orphan config — see [`config-consumers.md`](config-consumers.md)).

### Weapons — gun / bomb / mine firing

| `shipSection` key | Notes |
|---|---|
| `BulletFireDelay` | Per-shot cooldown for guns. |
| `BulletFireEnergy` | Energy cost per gun shot. |
| `BulletSpeed` | Gun projectile speed. |
| `BombFireDelay` | Per-shot cooldown for bombs. |
| `BombFireEnergy` | Energy cost per bomb (level 1 baseline). |
| `BombFireEnergyUpgrade` | Per-level energy delta for bombs. |
| `BombSpeed` | Bomb projectile speed. |
| `BombThrust` | Recoil thrust applied to the firing ship. |
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
| `InitialGuns` / `MaxGuns` | Same for `Guns` / `GunCurrentLevel` / `GunMaxLevel`. |
| `MaxMines` | Mine inventory cap. (No `InitialMines` in the `shipSection` surface.) |
| `InitialBurst` / `BurstMax` | Burst grenades. `Burst` / `BurstMax` components exist; not Groovy-driven. |
| `BurstShrapnel` | Shrapnel count per burst. |
| `BurstSpeed` | Burst projectile speed. |
| `InitialRepel` / `RepelMax` | Repel charges. |
| `InitialDecoy` / `DecoyMax` | Decoy charges. |
| `InitialThor` / `ThorMax` | Thor charges. `ThorCurrentCount` / `ThorMaxCount` components exist; not Groovy-driven. |
| `InitialPortal` / `PortalMax` | Portal charges. |
| `ShrapnelMax` / `ShrapnelRate` | Shrapnel-on-bomb-detonation. |

### Abilities — energy costs and on/off

| `shipSection` key | Notes |
|---|---|
| `AfterburnerEnergy` | Energy/sec cost while afterburner is held. |
| `CloakEnergy` / `CloakStatus` | Cloak cost + initial state. |
| `StealthEnergy` / `StealthStatus` | Stealth cost + initial state. |
| `XRadarEnergy` / `XRadarStatus` | XRadar cost + initial state. |
| `AntiWarpEnergy` / `AntiWarpStatus` | Antiwarp cost + initial state. |
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

- **Add fields incrementally.** Don't try to port all 69 in one pass — port the subset a feature actually needs, wire its consumers (per Pattern 4: typed `*Config` field → projection → component → consumer), then update this file.
- **Group related keys into nested config records.** `BulletFireDelay` / `BulletFireEnergy` / `BulletSpeed` likely become a `BulletConfig` record nested in `ShipConfig` rather than 3 flat fields. Same for `BombConfig`, `BurstConfig`, etc. Keep the Groovy DSL ergonomic — flat top-level setters per cluster, like the existing `rotation initial: ..., max: ..., upgrade: ...`.
- **Cap-style stats follow the Pattern 4 triple shape** (`initial / max / upgrade`); see [`config-pattern.md`](rules/config-pattern.md). Pure config with no upgrade axis (e.g. `BombSpeed`) is just a single field.
- **Don't port "orphan" keys** — those without any consumer in the Java code today. Adding them to Groovy creates orphan config (declared, never read), which makes [`config-consumers.md`](config-consumers.md) noisier without delivering value. Wire the consumer first or skip.
- **Update [`config-consumers.md`](config-consumers.md)** when you wire the new field's consumer (always-on rule #4).
