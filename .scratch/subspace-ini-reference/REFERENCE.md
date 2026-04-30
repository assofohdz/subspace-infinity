# Subspace INI Settings Reference

The canonical reference for Subspace zone INI fields, preserved here from the
570-line comment block previously living at the bottom of
`infinity/src/main/java/infinity/systems/SettingsSystem.java`. The format
originated from the in-game `?settings` command and is attributed to
"Mine GO BOOM ... Version 1.34.14, http://www.shanky.com/server/".

Field format: `**FieldName** (range, when present) — description`. Section
names match the INI section headers under
`infinity/zone/conf/<preset>/*.groovy` / `infinity/zone/arenas/<name>/arena.groovy`.

Use this as a guide when porting INI fragments to Groovy or when adding
typed accessors in the settings layer. Fields that have already been
promoted to Pattern 4 (`*Config` records) or live as ECS components do
**not** need their INI entries kept in sync — Groovy is the source of
truth there.

## Notes

- **SettingName** — Name of the Game the settings "create"
- **Maker** — Creator of the settings
- **CoMaker** — Original and/or helper of the zone
- **MapName** — Map name(s) used with settings
- **Mapper** — Map maker's name
- **Note1**..**Note5** — Misc notes you wish to add

## Bomb

- **BombDamageLevel** — Damage at center point (for all bomb levels)
- **BombAliveTime** — Time bomb is alive (1/100s)
- **BombExplodeDelay** — Delay after proximity sensor trigger before bomb explodes (immediate if ship leaves trigger area)
- **BombExplodePixels** — Blast radius for L1 (L2 doubles, L3 triples)
- **ProximityDistance** — Radius of proximity trigger in tiles. Each level adds 1.
- **JitterTime** — Screen jitter duration on bomb hit (1/100s)
- **BombSafety** (0/1) — Whether proximity bombs have a firing safety (won't fire if enemy in proximity)
- **EBombShutdownTime** — Max time recharge is stopped on EMP-bomb-hit players
- **EBombDamagePercent** — Percentage of normal damage applied by an EMP bomb (1000 = 100%)
- **BBombDamagePercent** — Percentage of normal damage applied by a bouncing bomb (1000 = 100%)

## Brick

- **BrickTime** — How long bricks last (1/100s)
- **BrickSpan** — How many tiles bricks span

## Bullet

- **BulletDamageLevel** — Max damage from L1 bullet. Formula: `damage = sqrt(rand# * (max^2 + 1))`
- **BulletDamageUpgrade** — Extra damage per bullet level
- **BulletAliveTime** — How long bullets live (1/100s)
- **ExactDamage** (0/1) — Damage exact (1) or random (0)

## Burst

- **BurstDamageLevel** — Max damage from a single burst bullet

## Cost

Costs in points to purchase a prize at the shop. `PurchaseAnytime` (0/1) controls where: 0=safe zone only, 1=anywhere.

- **PurchaseAnytime** (0/1) — Where prizes can be purchased (0=safe zone, 1=anywhere)
- **Recharge**, **Energy**, **Rotation**, **Stealth**, **Cloak**, **XRadar**, **Gun**, **Bomb**, **Bounce**, **Thrust**, **Speed**, **MultiFire**, **Prox**, **Super**, **Shield**, **Shrap**, **AntiWarp**, **Repel**, **Burst**, **Decoy**, **Thor**, **Brick**, **Rocket**, **Portal** — Each a per-prize point cost

## Custom

- **SaveStatsTime** (100,000..100,000,000) — How often a custom arena saves scores to disk

## Door

- **DoorDelay** — How often doors attempt to switch state
- **DoorMode** — `-2` = all doors random, `-1` = weighted random (some doors more often), `0..255` = fixed (1 bit per door)

## Flag

- **FlaggerOnRadar** (0/1) — Whether flaggers appear in red on radar
- **FlaggerKillMultiplier** — Point multiplier for flagger kills (1=double, 2=triple)
- **FlaggerGunUpgrade** (0/1) — Whether flaggers get a gun upgrade
- **FlaggerBombUpgrade** (0/1) — Whether flaggers get a bomb upgrade
- **FlaggerFireCostPercent** — Weapon firing cost % for flaggers (0=Super, 1000=100%)
- **FlaggerDamagePercent** — Damage taken by flaggers (0=Invincible, 1000=100%)
- **FlaggerSpeedAdjustment** — Speed change for flaggers (negative = slower)
- **FlaggerThrustAdjustment** — Thrust change for flaggers (negative = less)
- **FlaggerBombFireDelay** — Bomb fire delay for flaggers (0=normal; do not set < 20)
- **CarryFlags** (0..2) — Flags can be picked up (0=no, 1=yes, 2=yes-one-at-a-time)
- **FlagDropDelay** — Time before flag is dropped by carrier (0=never)
- **FlagDropResetReward** — Min kill reward to reset the flag drop timer
- **EnterGameFlaggingDelay** — Delay before new players see flags
- **FlagBlankDelay** — Time after which no-data hides flags from view (10s)
- **NoDataFlagDropDelay** — Time after which no-data drops carried flags
- **FlagMode** (0..2) — 0=dropped flags un-owned (carry all to win), 1=dropped flags owned (own all to win), 2=Turf
- **FlagResetDelay** — Time before un-won flag game resets (1/100s; > 1,000,000 advised)
- **MaxFlags** (0..32) — Max flags in arena (0=no flag game)
- **RandomFlags** (0/1) — Random count up to MaxFlags
- **FlagReward** — Points for flag victory. Formula: `players² × FlagReward / 1000` (0=never declared)
- **FlagRewardMode** (0/1) — 0=each member gets full points; 1=each gets `points × team / max`
- **FlagTerritoryRadius** — Drop spread radius from centroid in tiles (0=hide in center as normal)
- **FlagTerritoryRadiusCentroid** — Random centroid offset distance from drop location (1024=anywhere)
- **FriendlyTransfer** (0/1) — Whether flaggers can transfer flags to teammates

## Kill

- **MaxBonus** — Bonus points per kill if you carry flags
- **MaxPenalty** — Point penalty per kill if you carry flags
- **RewardBase** — Shown added to bounty but not actually awarded
- **BountyIncreaseForKill** — Points added to bounty per kill
- **EnterDelay** — Time before player can re-enter after dying
- **KillPointsPerFlag** — Bonus points by flag count (Turf-style)
- **KillPointsMinimumBounty** — Min bounty to qualify for KillPointsPerFlag
- **DebtKills** — Kills required after death/reset before earning kill points (0=normal)
- **NoRewardKillDelay** — Same-target double-kill within this gives no second reward (1/100s)
- **BountyRewardPercent** — % of own bounty added to kill reward
- **FixedKillReward** — Fixed points per kill (-1=use bounty)
- **JackpotBountyPercent** — % of kill value added to Jackpot (0=no jackpot, 1000=100%)

## King

- **DeathCount** — Deaths allowed before crown is removed
- **ExpireTime** — Initial round time per player
- **RewardFactor** — Round-winner reward (uses FlagReward formula)
- **NonCrownAdjustTime** — Extra time for killing a non-crown player
- **NonCrownMinimumBounty** — Min bounty to grant the extra time
- **CrownRecoverKills** — Crown kills required for non-crown player to recover crown

## Latency

- **SendRoutePercent** (300..800) — % of ping spent on C2S (clock sync)
- **KickOutDelay** (100..2000) — Max no-data time before kick
- **NoFlagDelay** (100..1000) — No-data time before player can't pick up flags
- **NoFlagPenalty** (300..32000) — Penalty time when NoFlagDelay exceeded
- **SlowPacketKickoutPercent** (0..1000) — % C2S slow packets before kick
- **SlowPacketTime** (20..200) — C2S latency that counts as slow
- **SlowPacketSampleSize** (50..1000) — C2S sample size for kick check
- **ClientSlowPacketKickoutPercent** (0..1000) — % S2C slow packets before kick
- **ClientSlowPacketTime** (20..200) — S2C latency that counts as slow
- **ClientSlowPacketSampleSize** (50..1000) — S2C sample size
- **MaxLatencyForWeapons** (20..200) — Max C2S latency before server disables weapons
- **MaxLatencyForPrizes** (50..800) — Max time before a shared-prize packet is ignored
- **MaxLatencyForKickOut** (40..200) — Max latency before kick
- **LatencyKickOutTime** (300..3000) — Time `MaxLatencyForKickOut` must be bad before kick
- **S2CNoDataKickoutDelay** (100..32000) — S2C no-data time before disconnect
- **CutbackWatermark** (500..32000) — Bytes/sec before server starts dropping non-critical packets
- **C2SNoDataAction** — 0=normal; 1=sysop warn; 2=spec; 3=warn+spec; 4=kick; 5=warn+kick; 6=kick+spec; 7=warn+kick+spec
- **C2SNoDataTime** — Delay (1/100s) ping can be maxed before action triggers
- **NegativeClientSlowPacketTime** — Future-timestamp threshold for slow-packet detection (0=disabled, experimental)

## Message

- **MessageReliable** (0/1) — Messages sent reliably
- **AllowAudioMessages** (0/1) — Audio messages allowed
- **BongAllowed** (0/1) — Bong sounds allowed
- **QuickMessageLimit** — Max messages in a row before kick
- **MessageTeamReliable** (0/1) — Team messages sent reliably
- **MessageDistance** — (deprecated)

## Mine

- **MineAliveTime** (0..60000) — Mine active time (1/100s)
- **TeamMaxMines** (0..32000) — Max mines per team

## Misc

- **FrequencyShipTypes** (0/1) — Ship type based on frequency
- **WarpPointDelay** — Portal point active time
- **DecoyAliveTime** — Decoy active time (1/100s)
- **BounceFactor** — Wall bounciness (16=no speed loss)
- **SafetyLimit** — Max safe-zone time (90,000=15min)
- **TickerDelay** — Ticker help message interval
- **WarpRadiusLimit** — Random spawn distance limit from arena center (1024=anywhere)
- **ActivateAppShutdownTime** — Shutdown time after app reactivation (windowed mode)
- **NearDeathLevel** — Energy threshold for near-death (bounty -1; for dueling)
- **VictoryMusic** (0/1) — Whether zone plays victory music
- **BannerPoints** — Points required to display banner
- **MaxLossesToPlay** — Deaths before forced spec (0=never)
- **SpectatorQuiet** (0/1) — 1=specs can't talk to active players
- **MaxPlaying** — Max active players (excludes specs; 0=arena limit)
- **TimedGame** — Timed-game duration (0=untimed)
- **ResetScoreOnFrequencyChange** (0/1) — Reset score on freq change (timed games like soccer)
- **SendPositionDelay** (0..20) — Time between client position packets
- **SlowFrameCheck** (0/1) — Check client frame rate (cheat detection; flawed on some machines)
- **SlowFrameRate** (0..35) — Min frame rate (0=disabled, 1-35=kick if below)
- **AllowSavedShips** (0/1) — 1=saved ship from last arena/lagout, 0=new ship on entry
- **FrequencyShift** (0..10000) — Random sound frequency shift
- **ExtraPositionData** (0/1) — Whether players get sysop ship data (leave 0)
- **SheepMessage** — String for `?sheep`
- **MaxPlayers** — Arena max (overrides `.ini`)
- **GreetMessage** — Message to new arrivals
- **PeriodicMessage0..4** — Periodic announcements: format `<repeat-min> <delay-min> <text>`. Text starting `*` is a *zone message; otherwise *arena command. 60=1h, 1440=1d. Set to `0` to blank.
- **MaxXRes**, **MaxYRes** — Resolution limits (0=no limit)
- **ContinuumOnly** (0/1) — 0=any client, 1=Continuum only (VIE locked in spec)
- **LevelFiles** — Comma-separated `.lvz` list (`+filename` = optional)
- **MinUsage** — Min usage hours required
- **StartInSpec** (0/1) — 1=all entrants start in spec
- **MaxTimerDrift** — % allowed timer drift between client and server
- **DisableScreenshot** (0/1) — 0=anyone, 1=spectators only
- **AntiWarpSettleDelay** — Time after warp/portal/attach that those actions are blocked (1/100s)
- **SaveSpawnScore** (0/1) — 1=save spawn scores to `arenaname.scr`

## Owner

- **UserId** — User ID for Owner's name
- **Name** — Owner's username

## PacketLoss

- **C2SKickOutPercent** — C2S packetloss kick threshold (800 = allow 20% loss)
- **S2CKickOutPercent** — S2C packetloss kick threshold (same convention)
- **SpectatorPercentAdjust** — Extra packetloss allowed for spectators
- **PacketLossDisableWeapons** (0/1) — Server disables weapons on high packetloss

## Periodic

- **RewardDelay** (0..720000) — Periodic reward interval (0=none)
- **RewardMinimumPlayers** (0..255) — Min players for periodic rewards
- **RewardPoints** (-500..1000) — Points per flag owned (negative = `flagCount × playersInArena`)

## Prize

- **MultiPrizeCount** — Random greens given by a MultiPrize
- **PrizeFactor** — Hidden-prize count formula multiplier (10000=max, 10 greens/player)
- **PrizeDelay** — Prize regeneration interval (1/100s)
- **PrizeHideCount** — Prizes regenerated per `PrizeDelay`
- **MinimumVirtual** — Distance from arena center for prize/flag/ball spawning
- **UpgradeVirtual** — Extra distance per player in game
- **PrizeMaxExist**, **PrizeMinExist** — Hidden prize lifetime range
- **PrizeNegativeFactor** — Odds of negative prize (1=every prize, 32000=extremely rare)
- **DeathPrizeTime** — Death-spawned prize lifetime
- **EngineShutdownTime** — Engine Shutdown duration (1/100s)
- **TakePrizeReliable** (0/1) — C2S prize reliability
- **S2CTakePrizeReliable** (0/1) — S2C prize reliability

## PrizeWeight

Likelihood of each prize type appearing.

- **Recharge** (= "Full Charge", not Recharge — VIE naming quirk)
- **QuickCharge** (= actual "Recharge")
- **Energy** (= "Energy Upgrade")
- **Rotation**, **Stealth**, **Cloak**, **AntiWarp**, **XRadar**, **Warp**
- **Gun** (= "Gun Upgrade"), **Bomb** (= "Bomb Upgrade")
- **BouncingBullets**, **Thruster**, **TopSpeed**, **MultiFire**, **Proximity**
- **Glue** (= "Engine Shutdown"), **AllWeapons** (= "Super!"), **Shields**, **Shrapnel**
- **Repel**, **Burst**, **Decoy**, **Thor**, **Portal**, **Brick**, **Rocket**, **MultiPrize**

## Radar

- **RadarMode** (0..4) — 0=normal, 1=half/half, 2=quarters, 3=half/half-see-team, 4=quarters-see-team
- **RadarNeutralSize** (0..1024) — Pixels between blinded zones
- **MapZoomFactor** (8..1000) — Radar view distance

## Repel

- **RepelSpeed** — Repulsion speed
- **RepelTime** — Affected duration (1/100s)
- **RepelDistance** — Affected radius (pixels)

## Rocket

- **RocketThrust** — Thrust while rocket active
- **RocketSpeed** — Speed while rocket active

## Routing

- **RadarFavor** (1..7) — Radar packet rate (1=every, 3=every 4th, 7=every 8th)
- **CloseEnoughBulletAdjust** (0..512) — Off-screen pixel distance to forward bullets
- **CloseEnoughBombAdjust** (0..4096) — Off-radar pixel distance to forward bombs (in bomb's direction)
- **DeathDistance** (1000..16384) — Death message broadcast distance
- **DoubleSendPercent** (500..900) — Packetloss % at which server double-sends weapons
- **WallResendCount** (0..3) — Extra unreliable wall packets (in addition to reliable)
- **QueuePositions** — 1=use the four queue settings below
- **PosSendRadar** — Radar queue (default 100ms)
- **PosSendEdge** — Edge-of-screen queue (default 30ms)
- **PosSendClose** — Close packets queue (default 20ms)
- **ClosePosPixels** — "Close" pixel threshold (default 250)

## Security

- **S2CKickOutPercentWeapons** — Kick threshold for missing weapon packets
- **SecurityKickOff** (0/1) — Kick on security violation
- **SuicideLimit** — Max suicides before kick (deprecated)
- **MaxShipTypeSwitchCount** — Ship-switch limit before removal
- **PacketModificationMax** — Max modified packets before violation
- **MaxDeathWithoutFiring** — Deaths without firing before removal

## Shrapnel

- **ShrapnelSpeed** — Shrapnel travel speed
- **InactiveShrapDamage** — Damage during first 1/4s of life
- **ShrapnelDamagePercent** — % of normal damage (1000=100% of L1 bullet)
- **Random** (0/1) — 0=circular, 1=random pattern

## Spawn

- **Team0-X**, **Team0-Y** — Center point for Freq 0 spawns
- **Team0-Radius** — Spawn-circle radius (tiles)
- **Team1-X**, **Team1-Y**, **Team1-Radius** — Same, Freq 1
- **Team2-X**, **Team2-Y**, **Team2-Radius** — Same, Freq 2 (if unset but 0/1 set, alternates between them)
- **Team3-X**, **Team3-Y**, **Team3-Radius** — Same, Freq 3 (Freq 4 → Team0, Freq 5 → Team1, etc.)

> Note: hyphenated keys must be quoted in Groovy fragments — see commit `c2209c2`.

## Soccer

- **BallBounce** (0/1) — 0=ball passes through walls, 1=bounces
- **AllowBombs** (0/1) — Carrier can fire bombs
- **AllowGuns** (0/1) — Carrier can fire guns
- **PassDelay** (0..10000) — Lockout after ball is fired (1/100s)
- **Mode** (0..6) — Goal config: 0=any, 1=L/R, 2=T/B, 3=quad-defend-1, 4=quad-defend-3, 5=sides-defend-1, 6=sides-defend-3
- **BallCount** — Ball count (0=soccer off)
- **SendTime** — Ball position update rate (raise for many balls)
- **Reward** — Negative=absolute points; positive=FlagReward formula
- **CapturePoints** — Positive=points per goal/team distributed; negative=first to `-CapturePoints` wins
- **UseFlagger** (0/1) — Carrier uses Flagger* adjustments
- **BallLocation** (0/1) — Always show ball location
- **BallBlankDelay** — No-data time still allowed for ball pickup
- **CatchMinimum** — Min goals to win
- **CatchPoints** — Max goals to win
- **WinBy** — Goal differential to win
- **DisableWallPass** (0/1) — Disable through-wall passing
- **DisableBallKilling** (0/1) — Disable safety-kill while carrying

## Team

- **MaxFrequency** — Max freq (5 → 0,1,2,3,4)
- **MaxPerTeam** — Max players per non-private freq
- **MaxPerPrivateTeam** — Max players per private freq (0=same as MaxPerTeam)
- **DesiredTeams** — Teams server creates before stacking new arrivals
- **ForceEvenTeams** — Variance allowed (0=no restrictions, 1-10=variance)
- **SpectatorFrequency** — Reserved spec freq (no MaxFrequency limit)

## Territory

- **RewardDelay** — Reward interval (0=disabled)
- **RewardBaseFlags** — Min flags to qualify
- **RewardMinimumPlayers** — Min players to qualify
- **RewardPoints** — Reward formula

## Toggle

- **AntiWarpPixels** — Anti-Warp range (enemy must also be on radar)

## Wormhole

- **GravityBombs** (0/1) — Wormhole affects bombs
- **SwitchTime** — Destination switch interval

## Per-ship `[All]` (and per-ship sections: Warbird, Javelin, Spider, etc.)

These apply per-ship; the `[All]` section sets defaults that ship sections override.

### Initial / Maximum / Upgrade stats

- **InitialRotation**, **MaximumRotation** — Rotation rate (0=can't rotate, 400=full rotation/s)
- **InitialThrust**, **MaximumThrust** — Thrust (0=none)
- **InitialSpeed**, **MaximumSpeed** — Speed (0=can't move)
- **InitialRecharge**, **MaximumRecharge** — Energy recharge rate
- **InitialEnergy**, **MaximumEnergy** — Energy capacity
- **UpgradeRotation**, **UpgradeThrust**, **UpgradeSpeed**, **UpgradeRecharge**, **UpgradeEnergy** — Per-prize upgrade increments

### Ship abilities

- **CloakStatus** (0..2) — 0=no, 1=yes, 2=yes/start-with
- **StealthStatus** (0..2) — Same
- **XRadarStatus** (0..2) — Same
- **AntiWarpStatus** (0..2) — Same
- **CloakEnergy** (0..32000) — Energy drain (1000ths/centisecond)
- **StealthEnergy** (0..32000) — Same
- **XRadarEnergy** (0..32000) — Same
- **AntiWarpEnergy** (0..32000) — Same

### Inventory caps and starts

- **InitialRepel**, **InitialBurst**, **InitialBrick**, **InitialRocket**, **InitialThor**, **InitialDecoy**, **InitialPortal** — Starting inventory
- **InitialGuns** (0..3), **InitialBombs** (0..3) — Starting weapon level
- **RepelMax**, **BurstMax**, **DecoyMax**, **RocketMax**, **ThorMax**, **BrickMax**, **PortalMax** — Inventory caps
- **MaxGuns** (0..3), **MaxBombs** (0..3) — Max weapon levels

### Bullets

- **BulletFireEnergy** — Energy per L1 bullet
- **BulletSpeed** — Bullet speed
- **BulletFireDelay** — Cooldown before next weapon (1/100s)
- **MultiFireEnergy** — Energy for multifire L1
- **MultiFireDelay** — Cooldown after multifire
- **MultiFireAngle** — Spread between multifire bullets and forward (111 = 1°, 1000 = 1 ship-rotation-point)
- **DoubleBarrel** (0/1) — Double-barrel firing

### Bombs

- **BombFireEnergy** — Energy per L1 bomb
- **BombFireEnergyUpgrade** — Extra energy per upgrade (L2 = base + upgrade)
- **BombThrust** — Back-thrust on fire
- **BombBounceCount** — Bomb bounces before impact-explode
- **BombSpeed** — Bomb speed
- **BombFireDelay** — Cooldown after firing bomb
- **EmpBomb** (0/1) — Fires EMP bombs
- **SeeBombLevel** (0..4) — 0=disabled, 1=all, 2=L2+, 3=L3+, 4=L4 only

### Mines

- **MaxMines** — Mine cap
- **SeeMines** (0/1) — Visible on radar
- **LandmineFireEnergy** — Energy per L1 mine
- **LandmineFireEnergyUpgrade** — Extra per upgrade
- **LandmineFireDelay** — Cooldown after place

### Shrapnel & burst

- **ShrapnelMax** (0..31) — Max shrapnel from one bomb
- **ShrapnelRate** (0..31) — Per-prize increment
- **BurstSpeed** — Burst shrapnel speed
- **BurstShrapnel** — Bullets per Burst

### Turret / misc

- **TurretThrustPenalty** — Thrust penalty when carrying a turret
- **TurretSpeedPenalty** — Speed penalty when carrying a turret
- **TurretLimit** — Max turrets on ship
- **RocketTime** — Rocket lifetime (1/100s)
- **InitialBounty** — Starting greens
- **AttachBounty** — Bounty needed to attach as turret
- **AfterburnerEnergy** — Afterburner activation cost
- **DisableFastShooting** (0/1) — Block bullet/bomb/thor while afterburning

### Ship physical properties

- **Radius** — Ship collision radius (pixels; standard 14)
- **DamageFactor** — Prize-loss probability (0=never, 1=very likely, 5000=almost never)
- **PrizeShareLimit** — Max bounty for receiving team prizes
- **SuperTime** — Super duration (1/100s)
- **ShieldsTime** — Shields duration (1/100s)
- **Gravity** — Wormhole pull. Formula: `R = 1.325 × (g ^ 0.507)`. e.g. g=500 → pull within 31 tiles
- **GravityTopSpeed** — Extra speed allowed under wormhole pull (0=no extra)

### Soccer (per-ship)

- **SoccerBallFriction** — Ball deceleration (higher = faster slowdown)
- **SoccerBallProximity** — Pickup range (pixels)
- **SoccerBallSpeed** — Speed when fired by carrier
- **SoccerThrowTime** — Carry time (1/100s)

## Spectator

- **HideFlags** (0/1) — 1=specs can't see dropped flags
- **NoXRadar** (0/1) — 0=specs have X, 1=specs don't

## Maker (footer)

> +Maker:Maker — Editing was done by Mine GO BOOM with the help of the letter K. Version 1.34.14. For more help, visit http://www.shanky.com/server/
