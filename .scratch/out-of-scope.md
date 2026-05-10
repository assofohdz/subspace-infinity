# Out-of-Scope Subspace Settings

Inventory of canonical Subspace VIE / Continuum settings that Infinity has
**explicitly chosen not to implement**. These keys (and full sections) were
previously authored across the per-preset `conf/*/misc.groovy`,
`conf/*/cost.groovy`, and `conf/*/ship-*.groovy` files but were never read
by any consumer — they shipped as dead INI-mirror noise.

**Why this file exists.** As Infinity migrates to a typed-DSL config
pipeline (see [`config-pattern.md`](../.claude/rules/config-pattern.md) and
the [pipeline tracker](settings-pipeline.md)), every key in the `.groovy`
sources has to either become a typed field or be deleted. Carrying 🚫 keys
forward would force us to invent typed shapes for mechanics we don't run.
Cutting them keeps the tracker honest about what's actually wired.

**Recreating values.** The values that used to be authored for these keys
were either Subspace defaults or arena-specific copies of those defaults;
they were not driving any gameplay. If a section is later promoted back
into scope, look the canonical defaults up in
[`subspace-ini-reference/REFERENCE.md`](subspace-ini-reference/REFERENCE.md)
or recover the prior values from `git log -- zone/conf/`.

## Whole sections

| Section | Keys | Reason |
|---|---|---|
| `[Cost]` | 25 keys (`PurchaseAnytime`, `Recharge`, `Energy`, `Rotation`, `Stealth`, `Cloak`, `XRadar`, `Gun`, `Bomb`, `Bounce`, `Thrust`, `Speed`, `MultiFire`, `Prox`, `Super`, `Shield`, `Shrap`, `AntiWarp`, `Repel`, `Burst`, `Decoy`, `Thor`, `Brick`, `Rocket`, `Portal`) | Subspace shop economy not implemented. All preset values were `0` (disabled). Lived in dedicated `cost.groovy` files. |
| `[Flag]` | 24 keys (`FlaggerOnRadar`, `FlaggerKillMultiplier`, `FlaggerGunUpgrade`, `FlaggerBombUpgrade`, `FlaggerFireCostPercent`, `FlaggerDamagePercent`, `FlaggerBombFireDelay`, `FlaggerSpeedAdjustment`, `FlaggerThrustAdjustment`, `CarryFlags`, `FlagDropDelay`, `FlagDropResetReward`, `EnterGameFlaggingDelay`, `FlagBlankDelay`, `NoDataFlagDropDelay`, `FlagMode`, `FlagResetDelay`, `MaxFlags`, `RandomFlags`, `FlagReward`, `FlagRewardMode`, `FlagTerritoryRadius`, `FlagTerritoryRadiusCentroid`, `FriendlyTransfer`) | Flag-mode gameplay not active. |
| `[Kill]` | 12 keys (`MaxBonus`, `MaxPenalty`, `RewardBase`, `BountyIncreaseForKill`, `EnterDelay`, `KillPointsPerFlag`, `KillPointsMinimumBounty`, `DebtKills`, `NoRewardKillDelay`, `BountyRewardPercent`, `FixedKillReward`, `JackpotBountyPercent`) | Kill-reward economy not implemented. |
| `[King]` | 6 keys (`DeathCount`, `ExpireTime`, `RewardFactor`, `NonCrownAdjustTime`, `NonCrownMinimumBounty`, `CrownRecoverKills`) | King-of-the-hill mode not implemented. |
| `[Latency]` | ~17 keys (`SendRoutePercent`, `KickOutDelay`, `NoFlagDelay`, `NoFlagPenalty`, `SlowPacketKickoutPercent`, `SlowPacketTime`, `SlowPacketSampleSize`, `ClientSlowPacketKickoutPercent`, `ClientSlowPacketTime`, `ClientSlowPacketSampleSize`, `MaxLatencyForWeapons`, `MaxLatencyForPrizes`, `MaxLatencyForKickOut`, `LatencyKickOutTime`, `S2CNoDataKickoutDelay`, `CutbackWatermark`, `C2SNoDataAction`, `C2SNoDataTime`, `NegativeClientSlowPacketTime`) | Client/server latency policing not implemented; would belong in a network-layer subsystem, not a game system. |
| `[PacketLoss]` | 4 keys (`C2SKickOutPercent`, `S2CKickOutPercent`, `SpectatorPercentAdjust`, `PacketLossDisableWeapons`, plus authored extra `C2SNegativeKickOutPercent`) | Not enforced server-side. |
| `[Periodic]` | 3 keys (`RewardDelay`, `RewardMinimumPlayers`, `RewardPoints`) | Periodic-reward loop not implemented. |
| `[Routing]` | 6 keys authored (`RadarFavor`, `CloseEnoughBulletAdjust`, `CloseEnoughBombAdjust`, `DeathDistance`, `DoubleSendPercent`, `WallResendCount`) — Subspace lists ~10 in total | Net-layer concerns, not gameplay. |
| `[Security]` | 6 keys (`S2CKickOutPercentWeapons`, `SuicideLimit`, `MaxShipTypeSwitchCount`, `PacketModificationMax`, `MaxDeathWithoutFiring`, `SecurityKickOff`) | Anti-cheat policing not implemented. |
| `[Soccer]` | up to 14 authored keys (`BallBounce`, `AllowBombs`, `AllowGuns`, `PassDelay`, `Mode`, `BallBlankDelay`, `UseFlagger`, `BallLocation`, `BallCount`, `SendTime`, `Reward`, `CapturePoints`, `DisableBallKilling`, `DisableWallPass`) — REFERENCE.md lists 19 total | Soccer mode not active. |
| `[Team]` | 6 keys (`MaxFrequency`, `MaxPerTeam`, `MaxPerPrivateTeam`, `ForceEvenTeams`, `DesiredTeams`, `SpectatorFrequency`) | Freq-cap enforcement not implemented. |
| `[Territory]` | 4 keys (`RewardDelay`, `RewardBaseFlags`, `RewardMinimumPlayers`, `RewardPoints`) | Territory-rewards mechanic not implemented. |

## Single keys (kept their host section live)

| Key | Section | Reason |
|---|---|---|
| `MessageDistance` | `[Message]` | Deprecated in Subspace VIE — proximity-message radius mechanic abandoned upstream. The rest of `[Message]` (`MessageReliable`, `AllowAudioMessages`, `BongAllowed`, `QuickMessageLimit`, `MessageTeamReliable`) stays in scope. |

## Per-ship 🚫 keys

| Keys | Section scope | Reason |
|---|---|---|
| `SoccerBallFriction`, `SoccerBallProximity`, `SoccerBallSpeed`, `SoccerThrowTime` | every per-ship section (`[Warbird]`..`[Shark]`) across every preset | Soccer mode not active — same reason as the global `[Soccer]` block. |

## Promotion path

If a mechanic listed here becomes in-scope:

1. Move its row(s) out of this file.
2. Add a row to [`settings-pipeline.md`](settings-pipeline.md) with state ❌
   (in-scope but unstarted), and add a slice to
   [`settings-pipeline-slices.md`](settings-pipeline-slices.md).
3. Author the keys in a typed `.groovy` block (per the migration in
   progress) — not as `section('X') { Key Value }` INI-mirror DSL.
4. Look canonical defaults + units up in
   [`subspace-ini-reference/REFERENCE.md`](subspace-ini-reference/REFERENCE.md)
   — do not reinvent semantics.
