# Settings Pipeline Tracker

End-to-end status of every Subspace fragment key (canonical reference:
[`REFERENCE.md`](subspace-ini-reference/REFERENCE.md)) as it travels from
operator-facing Groovy fragment → typed `*Config` → ECS component →
runtime consumer. Rows = canonical settings keys. Columns trace the five
gates a setting must pass to actually affect gameplay.

## Columns

| # | Column | Question | Marker meaning |
|---|--------|----------|----------------|
| 1 | **Authored?** | Does any preset's `.groovy` file declare this key? | ✅ yes (filename) / ❌ no |
| 2 | **Loader** | Which `Groovy*Loader` reads it from `SettingsSystem`? | Class name / — for keys read elsewhere / ❌ |
| 3 | **API config** | Which `*Config` record carries it? | `Class.field` / — for non-tuning / ❌ |
| 4 | **Applier** | If a prize type, which `*PrizeApplier` handles it? | Class name / — for non-prize keys / ❌ |
| 5 | **Subsystem** | Which Java consumer reads the value at runtime? | `Class.method` / ❌ if dead |

Marker glossary: ✅ wired · ❌ not wired · — not applicable. A row with all `❌` is unwired; a row with all `✅` (or `✅` + `—` where applicable) is fully gameplay-active.

## When to update this table

- **Adding a new fragment key** to a `.groovy` file → flip `Authored?` to ✅, add the row if missing.
- **Adding a key to a `Groovy*Loader`** → flip `Loader` to the loader class name.
- **Adding a `*Config` field** → flip `API config` to `Record.field`.
- **Implementing a prize applier** → flip `Applier` from stub to the impl class.
- **Wiring a runtime consumer** → flip `Subsystem` to `Class.method`.

Drift between this table and the code is worse than no table — see the
always-on rule in [`CLAUDE.md`](../CLAUDE.md#always-on-rules).

---

## [Bomb]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `BombDamageLevel` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBomb` | `BombConfig.damage` | — | `WeaponsSystem.createProjectileBomb` (also gravbomb) |
| `BombAliveTime` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBomb` | `BombConfig.decayMs` | — | `WeaponsSystem.createProjectileBomb` (also gravbomb) |
| `BombExplodeDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BombExplodePixels` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `ProximityDistance` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `JitterTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BombSafety` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `EBombShutdownTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `EBombDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BBombDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Brick]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `BrickTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BrickSpan` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Bullet]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `BulletDamageLevel` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBullet` | `BulletConfig.damage` | — | `WeaponsSystem.createProjectileGun` (via `damageAtLevel`) |
| `BulletDamageUpgrade` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBullet` | `BulletConfig.damageUpgrade` | — | `WeaponsSystem.createProjectileGun` (via `damageAtLevel`) |
| `BulletAliveTime` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBullet` | `BulletConfig.decayMs` | — | `WeaponsSystem.createProjectileGun` |
| `ExactDamage` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Burst]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `BurstDamageLevel` | ✅ misc.groovy | `GroovyWeaponsLoader.loadBurst` | `BurstFireConfig.damage` | — | `WeaponsSystem.createProjectileBurst` |

## [Cost]

Per-prize point cost for a shop mechanic Infinity does not implement. All ❌; preserved for canonical inventory.

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `PurchaseAnytime` | ❌ | ❌ | ❌ | — | ❌ |
| `Recharge`/`Energy`/`Rotation`/`Stealth`/`Cloak`/`XRadar`/`Gun`/`Bomb`/`Bounce`/`Thrust`/`Speed`/`MultiFire`/`Prox`/`Super`/`Shield`/`Shrap`/`AntiWarp`/`Repel`/`Burst`/`Decoy`/`Thor`/`Brick`/`Rocket`/`Portal` | ❌ | ❌ | ❌ | — | ❌ |

## [Custom]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `SaveStatsTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Door]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `DoorDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ (DoorSystem reads via different path?) |
| `DoorMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Flag]

19 keys; flag-mode gameplay is not active. All ❌.

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `FlaggerOnRadar` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerKillMultiplier` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerGunUpgrade` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerBombUpgrade` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerFireCostPercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerSpeedAdjustment` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerThrustAdjustment` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlaggerBombFireDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `CarryFlags` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagDropDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagDropResetReward` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `EnterGameFlaggingDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagBlankDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `NoDataFlagDropDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagResetDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `MaxFlags` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `RandomFlags` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagReward` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagRewardMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagTerritoryRadius` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FlagTerritoryRadiusCentroid` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FriendlyTransfer` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Kill]

13 keys; kill-reward economy not implemented. All ❌.

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `MaxBonus` / `MaxPenalty` / `RewardBase` / `BountyIncreaseForKill` / `EnterDelay` / `KillPointsPerFlag` / `KillPointsMinimumBounty` / `DebtKills` / `NoRewardKillDelay` / `BountyRewardPercent` / `FixedKillReward` / `JackpotBountyPercent` | ✅ misc.groovy (most) | ❌ | ❌ | — | ❌ |

## [King]

6 keys; king-of-hill mode not implemented. All ❌.

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `DeathCount` / `ExpireTime` / `RewardFactor` / `NonCrownAdjustTime` / `NonCrownMinimumBounty` / `CrownRecoverKills` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Latency]

17 keys; client/server latency policing not implemented. All ❌. Subsystem column would be a network-layer class, not a game system.

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `SendRoutePercent` / `KickOutDelay` / `NoFlagDelay` / `NoFlagPenalty` / `SlowPacketKickoutPercent` / `SlowPacketTime` / `SlowPacketSampleSize` / `ClientSlowPacketKickoutPercent` / `ClientSlowPacketTime` / `ClientSlowPacketSampleSize` / `MaxLatencyForWeapons` / `MaxLatencyForPrizes` / `MaxLatencyForKickOut` / `LatencyKickOutTime` / `S2CNoDataKickoutDelay` / `CutbackWatermark` / `C2SNoDataAction` / `C2SNoDataTime` / `NegativeClientSlowPacketTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Message]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `MessageReliable` | ❌ | ❌ | ❌ | — | ❌ |
| `AllowAudioMessages` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BongAllowed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `QuickMessageLimit` | ❌ | ❌ | ❌ | — | ❌ |
| `MessageTeamReliable` | ❌ | ❌ | ❌ | — | ❌ |
| `MessageDistance` | ✅ misc.groovy (deprecated) | ❌ | ❌ | — | ❌ |

## [Mine]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `MineAliveTime` | ✅ misc.groovy | `GroovyWeaponsLoader.loadMine` | `MineConfig.decayMs` | — | `WeaponsSystem.createProjectileMine` |
| `TeamMaxMines` | ✅ misc.groovy | ❌ | ❌ (probably belongs in a TeamConfig) | — | ❌ |

## [Misc]

The biggest section; bounce/safety/spawn/timer knobs that mostly aren't read on the server hot path yet.

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `FrequencyShipTypes` | ❌ | ❌ | ❌ | — | ❌ |
| `WarpPointDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `DecoyAliveTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BounceFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `SafetyLimit` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `TickerDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `WarpRadiusLimit` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `ActivateAppShutdownTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `NearDeathLevel` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `VictoryMusic` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `BannerPoints` | ❌ | ❌ | ❌ | — | ❌ |
| `MaxLossesToPlay` | ❌ | ❌ | ❌ | — | ❌ |
| `SpectatorQuiet` | ❌ | ❌ | ❌ | — | ❌ |
| `MaxPlaying` | ❌ | ❌ | ❌ | — | ❌ |
| `TimedGame` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `ResetScoreOnFrequencyChange` | ❌ | ❌ | ❌ | — | ❌ |
| `SendPositionDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `SlowFrameCheck` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `SlowFrameRate` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `AllowSavedShips` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `FrequencyShift` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `ExtraPositionData` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `SheepMessage` | ❌ | ❌ | ❌ | — | ❌ |
| `MaxPlayers` | ❌ | ❌ | ❌ | — | ❌ |
| `GreetMessage` | ❌ | ❌ | ❌ | — | ❌ |
| `PeriodicMessage0..4` | ❌ | ❌ | ❌ | — | ❌ |
| `MaxXRes` / `MaxYRes` | ❌ | ❌ | ❌ | — | ❌ |
| `ContinuumOnly` | ❌ | ❌ | ❌ | — | ❌ |
| `LevelFiles` | ❌ | ❌ | ❌ | — | ❌ |
| `MinUsage` | ❌ | ❌ | ❌ | — | ❌ |
| `StartInSpec` | ❌ | ❌ | ❌ | — | ❌ |
| `MaxTimerDrift` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `DisableScreenshot` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `AntiWarpSettleDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `SaveSpawnScore` | ❌ | ❌ | ❌ | — | ❌ |

## [Owner]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `UserId` | ❌ | ❌ | ❌ | — | ❌ |
| `Name` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [PacketLoss]

4 keys; not enforced server-side. All ❌.

## [Periodic]

3 keys; periodic-reward loop not implemented. All ❌.

## [Prize]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `MultiPrizeCount` | ✅ misc.groovy | ❌ | ❌ | (used by `MultiPrizePrizeApplier` stub) | ❌ |
| `PrizeFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `PrizeDelay` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `PrizeHideCount` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `MinimumVirtual` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `UpgradeVirtual` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `PrizeMaxExist` | ✅ misc.groovy | `GroovyWeaponsLoader.loadPrize` | `PrizeConfig.defaultDecayMs` | — | `PrizeSystem` (decay routing for prize entities) |
| `PrizeMinExist` | ✅ misc.groovy | ❌ | ❌ (would need a range field) | — | ❌ |
| `PrizeNegativeFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `DeathPrizeTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `EngineShutdownTime` | ✅ misc.groovy | ❌ | ❌ | (Glue family — see Status appliers) | ❌ |
| `TakePrizeReliable` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `S2CTakePrizeReliable` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [PrizeWeight] — applier dispatch

[PrizeWeight] keys are spawn-frequency multipliers per prize type, read by `PrizeSystem.handlePrizeAcquisition` via the merged `Ini`. The "Applier" column tracks the per-prize implementation status. "Subsystem" column = which ship-side system the applier writes through (or delegates to).

| Prize type | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `Recharge` (= "Full Charge") | ✅ misc.groovy | `PrizeSystem` (PrizeWeights) | — | `QuickChargePrizeApplier` ✅ | `EnergySystem.refillHealth` |
| `Energy` | ✅ misc.groovy | `PrizeSystem` | — | `EnergyPrizeApplier` ✅ | ECS component bump (Energy/EnergyMax) |
| `Rotation` | ✅ misc.groovy | `PrizeSystem` | — | `RotationPrizeApplier` ✅ | ECS component bump (Rotation/Max) |
| `Stealth` | ✅ misc.groovy | `PrizeSystem` | — | `StealthPrizeApplier` ❌ stub | (needs Status family + StealthAvailable component) |
| `Cloak` | ✅ misc.groovy | `PrizeSystem` | — | `CloakPrizeApplier` ❌ stub | (needs Status family) |
| `XRadar` | ✅ misc.groovy | `PrizeSystem` | — | `XRadarPrizeApplier` ❌ stub | (needs Status family) |
| `Gun` (= "Gun Upgrade") | ✅ misc.groovy | `PrizeSystem` | — | `GunPrizeApplier` ✅ | ECS component bump (GunCurrentLevel) |
| `Bomb` (= "Bomb Upgrade") | ✅ misc.groovy | `PrizeSystem` | — | `CompositePrizeApplier(BombPrizeApplier, MinePrizeApplier)` ✅ | ECS bumps |
| `Bounce` | — | — | — | — (covered by `BouncingBulletsPrizeApplier`) | ❌ |
| `Thrust` | ✅ misc.groovy | `PrizeSystem` | — | `ThrusterPrizeApplier` ✅ | ECS component bump (Thrust) |
| `Speed` (= "Top Speed") | ✅ misc.groovy | `PrizeSystem` | — | `TopSpeedPrizeApplier` ✅ | ECS component bump (Speed) |
| `MultiFire` | ✅ misc.groovy | `PrizeSystem` | — | `MultiFirePrizeApplier` ❌ stub | (needs Status family) |
| `Proximity` | ✅ misc.groovy | `PrizeSystem` | — | `ProximityPrizeApplier` ❌ stub | (needs Status family) |
| `Super` | ✅ misc.groovy | `PrizeSystem` | — | `SuperPrizeApplier` ❌ stub | (needs Status family) |
| `Shields` | ✅ misc.groovy | `PrizeSystem` | — | `ShieldsPrizeApplier` ❌ stub | (needs Status family) |
| `Shrap` (= "Shrapnel") | ✅ misc.groovy | `PrizeSystem` | — | `ShrapnelPrizeApplier` ❌ stub | needs Shrapnel/ShrapnelMax components |
| `AntiWarp` | ✅ misc.groovy | `PrizeSystem` | — | `AntiWarpPrizeApplier` ❌ stub | (needs Status family) |
| `Repel` | ✅ misc.groovy | `PrizeSystem` | — | `RepelPrizeApplier` ✅ | ECS component bump (Repel/RepelMax) |
| `Burst` | ✅ misc.groovy | `PrizeSystem` | — | `BurstPrizeApplier` ✅ | ECS component bump (Burst/BurstMax) |
| `Decoy` | ✅ misc.groovy | `PrizeSystem` | — | `DecoyPrizeApplier` ✅ | ECS component bump (Decoy/DecoyMax) |
| `Thor` | ✅ misc.groovy | `PrizeSystem` | — | `ThorPrizeApplier` ✅ | ECS component bump |
| `Brick` | ✅ misc.groovy | `PrizeSystem` | — | `BrickPrizeApplier` ✅ | ECS component bump (Brick/BrickMax) |
| `Rocket` | ✅ misc.groovy | `PrizeSystem` | — | `RocketPrizeApplier` ✅ | ECS component bump (Rocket/RocketMax) |
| `Portal` | ✅ misc.groovy | `PrizeSystem` | — | `PortalPrizeApplier` ✅ | ECS component bump (Portal/PortalMax) |
| `Warp` | ✅ misc.groovy | `PrizeSystem` | — | `WarpPrizeApplier` ✅ | `WarpSystem.warpToCenter` |
| `BouncingBullets` | ✅ misc.groovy | `PrizeSystem` | — | `BouncingBulletsPrizeApplier` ❌ stub | (needs Status family) |
| `Glue` (= "Engine Shutdown") | ✅ misc.groovy | `PrizeSystem` | — | `GluePrizeApplier` ❌ stub | (needs Status family + EngineShutdownTime) |
| `AllWeapons` (= "Super!") | ✅ misc.groovy | `PrizeSystem` | — | `CompositePrizeApplier(BombPrizeApplier, BurstPrizeApplier, GunPrizeApplier, MinePrizeApplier)` ✅ | ECS bumps |
| `MultiPrize` | ✅ misc.groovy | `PrizeSystem` | — | `MultiPrizePrizeApplier` ❌ stub | (needs recursive dispatch + RNG) |
| `Dud` (negative) | — | — | — | `DudPrizeApplier` (placeholder) | — |

## [Radar]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `RadarMode` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `RadarNeutralSize` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `MapZoomFactor` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Repel]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `RepelSpeed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ (consumed at fire-time by `ConsumableSystem`) |
| `RepelTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `RepelDistance` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Rocket]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `RocketThrust` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `RocketSpeed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Routing]

10 keys; net-layer concerns. All ❌.

## [Security]

6 keys; anti-cheat policing not implemented. All ❌.

## [Shrapnel]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `ShrapnelSpeed` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `InactiveShrapDamage` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `ShrapnelDamagePercent` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `Random` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Spawn]

12 keys (4 teams × 3 each: X, Y, Radius). Spawn-point selection not yet wired through ConfigRegistry; today driven by hardcoded centerOfArena in `WarpSystem`. All ❌.

## [Soccer]

19 keys; soccer mode not active. All ❌.

## [Spectator]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `HideFlags` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `NoXRadar` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

## [Team]

6 keys; freq-cap enforcement not implemented. All ❌.

## [Territory]

4 keys; territory rewards not implemented. All ❌.

## [Toggle]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `AntiWarpPixels` | ✅ misc.groovy | ❌ | ❌ | — | ❌ (relevant once AntiWarpPrizeApplier lands) |

## [Wormhole]

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `GravityBombs` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |
| `SwitchTime` | ✅ misc.groovy | ❌ | ❌ | — | ❌ |

---

## Per-ship sections (`[All]`, `[Warbird]`, `[Javelin]`, `[Spider]`, `[Leviathan]`, `[Terrier]`, `[Weasel]`, `[Lancaster]`, `[Shark]`)

Per-ship keys flow through `GroovyShipLoader` (parses `ships.groovy`'s
typed DSL into `ShipConfig`), not `GroovyWeaponsLoader`. Authoritative
per-ship coverage lives in [`ship-config-dictionary.md`](ship-config-dictionary.md);
this table is the macro view.

### Initial / Maximum / Upgrade stats

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `InitialRotation` / `MaximumRotation` / `UpgradeRotation` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.rotation` (`ShipStat` triple) | `RotationPrizeApplier` (Upgrade) | `PlayerDriver.update` via `Rotation` component |
| `InitialThrust` / `MaximumThrust` / `UpgradeThrust` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.thrust` | `ThrusterPrizeApplier` (Upgrade) | `PlayerDriver.update` via `Thrust` |
| `InitialSpeed` / `MaximumSpeed` / `UpgradeSpeed` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.speed` | `TopSpeedPrizeApplier` (Upgrade) | `PlayerDriver.update` via `Speed` |
| `InitialRecharge` / `MaximumRecharge` / `UpgradeRecharge` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.recharge` | `RechargePrizeApplier` (Upgrade) | `EnergySystem.update` via `Recharge` |
| `InitialEnergy` / `MaximumEnergy` / `UpgradeEnergy` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.energy` | `EnergyPrizeApplier` (Upgrade) | `EnergySystem.update` via `Health`/`Energy` |

### Ship abilities (Status family)

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `CloakStatus` | ✅ ships.groovy | ❌ (not yet read) | ❌ | `CloakPrizeApplier` ❌ stub | ❌ |
| `StealthStatus` | ✅ ships.groovy | ❌ | ❌ | `StealthPrizeApplier` ❌ stub | ❌ |
| `XRadarStatus` | ✅ ships.groovy | ❌ | ❌ | `XRadarPrizeApplier` ❌ stub | ❌ |
| `AntiWarpStatus` | ✅ ships.groovy | ❌ | ❌ | `AntiWarpPrizeApplier` ❌ stub | ❌ |
| `CloakEnergy` / `StealthEnergy` / `XRadarEnergy` / `AntiWarpEnergy` | ✅ ships.groovy | ❌ | ❌ | — (drain rates) | ❌ |

### Inventory caps and starts

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `InitialRepel` / `RepelMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.repels` (`CountStats`) | `RepelPrizeApplier` ✅ | `WeaponsSystem` / `ConsumableSystem` |
| `InitialBurst` / `BurstMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bursts` | `BurstPrizeApplier` ✅ | `WeaponsSystem.createProjectileBurst` |
| `InitialBrick` / `BrickMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bricks` (or similar) | `BrickPrizeApplier` ✅ | (ConsumableSystem fire-time) |
| `InitialRocket` / `RocketMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.rockets` | `RocketPrizeApplier` ✅ | (ConsumableSystem) |
| `InitialThor` / `ThorMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.thors` | `ThorPrizeApplier` ✅ | `ConsumableSystem.actOut` (FIRETHOR) |
| `InitialDecoy` / `DecoyMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.decoys` | `DecoyPrizeApplier` ✅ | (ConsumableSystem fire-time) |
| `InitialPortal` / `PortalMax` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.portals` | `PortalPrizeApplier` ✅ | (ConsumableSystem fire-time) |
| `InitialGuns` / `MaxGuns` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.guns` (`GunStats`) | `GunPrizeApplier` ✅ | `WeaponsSystem.createProjectileGun` |
| `InitialBombs` / `MaxBombs` | ✅ ships.groovy | `GroovyShipLoader` | `ShipConfig.bombs` (`BombStats`) | `BombPrizeApplier` ✅ | `WeaponsSystem.createProjectileBomb` |

### Bullets / Bombs / Mines (per-ship cost & cadence)

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `BulletFireEnergy` | ✅ ships.groovy | `GroovyShipLoader` | `GunStats.fireCost` | — | `WeaponsSystem.deductCostOfAttackGun` |
| `BulletSpeed` | ✅ ships.groovy | ❌ (per-ship — ShipConfig field?) | ❌ | — | ❌ (today inline `addLocal(0,0,50)` in WeaponsSystem) |
| `BulletFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `GunStats.fireDelayMs` | — | `WeaponsSystem` cooldown |
| `MultiFireEnergy` / `MultiFireDelay` / `MultiFireAngle` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `DoubleBarrel` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `BombFireEnergy` / `BombFireEnergyUpgrade` | ✅ ships.groovy | `GroovyShipLoader` | `BombStats.fireCost`/`fireCostUpgrade` | — | `WeaponsSystem.deductCostOfAttackBomb` |
| `BombThrust` / `BombBounceCount` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `BombSpeed` | ✅ ships.groovy | ❌ | ❌ | — | ❌ (inline `25` today) |
| `BombFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `BombStats.fireDelayMs` | — | `WeaponsSystem` cooldown |
| `EmpBomb` / `SeeBombLevel` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `MaxMines` | ✅ ships.groovy | `GroovyShipLoader` | `MineStats.max` | — | `WeaponsSystem` mine cap check |
| `SeeMines` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `LandmineFireEnergy` / `LandmineFireEnergyUpgrade` | ✅ ships.groovy | `GroovyShipLoader` | `MineStats.fireCost`/`upgrade` | — | `WeaponsSystem.deductCostOfAttackMine` |
| `LandmineFireDelay` | ✅ ships.groovy | `GroovyShipLoader` | `MineStats.fireDelayMs` | — | `WeaponsSystem` cooldown |

### Shrapnel / Burst / Turret / Misc / Physical

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `ShrapnelMax` | ✅ ships.groovy | ❌ | ❌ | `ShrapnelPrizeApplier` ❌ stub | ❌ |
| `ShrapnelRate` | ✅ ships.groovy | ❌ | ❌ | (used by `ShrapnelPrizeApplier` increment) | ❌ |
| `BurstSpeed` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `BurstShrapnel` | ✅ ships.groovy | ❌ (lives in `BurstFireConfig.projectileCount`?) | partial | — | `WeaponsSystem.createProjectileBurst` |
| `TurretThrustPenalty` / `TurretSpeedPenalty` / `TurretLimit` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `RocketTime` | ✅ ships.groovy | ❌ | ❌ | (used by `RocketPrizeApplier` for active duration) | ❌ |
| `InitialBounty` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `AttachBounty` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `AfterburnerEnergy` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `DisableFastShooting` | ❌ | ❌ | ❌ | — | ❌ |
| `Radius` | ✅ ships.groovy | ❌ | ❌ | — | ❌ (physics path) |
| `DamageFactor` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `PrizeShareLimit` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |
| `SuperTime` | ✅ ships.groovy | ❌ | ❌ | (used by `SuperPrizeApplier`) | ❌ |
| `ShieldsTime` | ✅ ships.groovy | ❌ | ❌ | (used by `ShieldsPrizeApplier`) | ❌ |
| `Gravity` / `GravityTopSpeed` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |

### Soccer (per-ship)

| Setting | Authored? | Loader | API config | Applier | Subsystem |
|---|---|---|---|---|---|
| `SoccerBallFriction` / `SoccerBallProximity` / `SoccerBallSpeed` / `SoccerThrowTime` | ✅ ships.groovy | ❌ | ❌ | — | ❌ |

---

## Summary

- **Wired tuning settings (`misc.groovy`):** `[Bullet]` (3 keys), `[Bomb]` (2), `[Mine]` (1), `[Burst]` (1), `[Prize]` (1) — **8 keys** total flow from preset → `*Config` → `WeaponsSystem`/`PrizeSystem`.
- **Wired prize appliers (out of 30 PrizeTypes):** 17 done, 13 stubs (mostly Status family, Shrapnel, MultiPrize).
- **Per-ship `ShipConfig`:** thrust/speed/rotation/recharge/energy stat triples + 8 inventory CountStats + 3 weapon stats — fully wired via `GroovyShipLoader`.
- **Status family `*Status` / `*Energy` ship keys:** authored in ships.groovy but not yet read into ECS (Status appliers are stubbed).
- **Big unwired surfaces:** `[Flag]` (24 keys), `[Misc]` (35 keys), `[Latency]` (~17), `[Soccer]` (19), `[Kill]` (13), `[King]` (6), all-or-most ❌ — these track gameplay modes Infinity does not yet implement.
