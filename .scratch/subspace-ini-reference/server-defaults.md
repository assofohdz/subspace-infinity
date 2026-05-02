# Canonical Subspace/Continuum Server Defaults

Recovered from `infinity/src/main/resources/server.set` and
`infinity/src/main/resources/template.sss` immediately before deletion in
the same change that retired `SSSLoader`. The `.set` / `.sss` files were no
longer being loaded at runtime — `SSSLoader` was registered for the
extensions but no code called `assetManager.loadAsset(*.sss|*.set)`. The
**data**, however, is the canonical Mine GO BOOM reference set and worth
keeping for tuning work and Pattern 4 promotions.

This file pairs with [`REFERENCE.md`](./REFERENCE.md) — that page has the
field **descriptions**, this page has the canonical **defaults and ranges**.

## Source format

Each line was `Section:Field:default:min:max:Description`. Empty `min:max`
means no documented range — the value is freeform within the field's type.

Example: `Warbird:InitialBombs:1:0:3:Initial level a ship's bombs fire`

## Per-ship defaults — side-by-side comparison

The 8 canonical Subspace ships (97 fields each in canonical order). Use
this table when porting per-ship knobs to Pattern 4 `ShipConfig` records or
when a Groovy preset wants to use canonical defaults as a starting point.

| Field | Warbird | Javelin | Spider | Leviathan | Terrier | Weasel | Lancaster | Shark | Range | Description |
|---|---|---|---|---|---|---|---|---|---|---|
| AfterburnerEnergy | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Amount of energy required to have 'Afterburners' activated |
| AntiWarpEnergy | 100 | 100 | 100 | 100 | 100 | 100 | 100 | 100 |  | Amount of energy required to have 'Anti-Warp' activated (thousanths per tick) |
| AntiWarpStatus | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0..2 | Whether ships are allowed to receive 'Anti-Warp' 0=no 1=yes 2=yes/start-with |
| AttachBounty | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Bounty required by ships to attach as a turret |
| BombBounceCount | 1 | 0 | 8 | 0 | 3 | 0 | 2 | 0 |  | Number of times a ship's bombs bounce before they explode on impact |
| BombFireDelay | 70 | 60 | 90 | 20 | 25 | 130 | 15 | 35 |  | delay that ship waits after a bomb is fired until another weapon may be fired (in ticks) |
| BombFireEnergy | 180 | 200 | 300 | 200 | 260 | 250 | 250 | 225 |  | Amount of energy it takes a ship to fire a single bomb |
| BombFireEnergyUpgrade | 75 | 75 | 75 | 50 | 50 | 75 | 35 | 60 |  | Extra amount of energy it takes a ship to fire an upgraded bomb. i.e. L2 = BombFireEnergy+BombFireEnergyUpgrade |
| BombSpeed | 5000 | 6500 | 5500 | 4500 | 5100 | 6500 | 5000 | 3900 |  | How fast bombs travel |
| BombThrust | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Amount of back-thrust you receive when firing a bomb |
| BrickMax | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Maximum number of Bricks allowed in ships |
| BulletFireDelay | 5 | 3 | 1 | 3 | 0 | 2 | 11 | 5 |  | Delay that ship waits after a bullet is fired until another weapon may be fired (in ticks) |
| BulletFireEnergy | 30 | 28 | 30 | 30 | 50 | 22 | 50 | 25 |  | Amount of energy it takes a ship to fire a single L1 bullet |
| BulletSpeed | 5500 | 5000 | 6500 | 4000 | 6000 | 5000 | 6000 | 6000 |  | How fast bullets travel |
| BurstMax | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Maximum number of Bursts allowed in ships |
| BurstShrapnel | 40 | 50 | 30 | 40 | 50 | 70 | 40 | 60 |  | Number of bullets released when a 'Burst' is activated |
| BurstSpeed | 4500 | 6000 | 6500 | 2500 | 5000 | 2500 | 3000 | 7000 |  | How fast the burst shrapnel is for this ship |
| CloakEnergy | 0 | 0 | 0 | 0 | 0 | 100 | 0 | 100 |  | Amount of energy required to have 'Cloak' activated (thousanths per tick) |
| CloakStatus | 0 | 0 | 1 | 0 | 0 | 2 | 0 | 0 | 0..2 | Whether ships are allowed to receive 'Cloak' 0=no 1=yes 2=yes/start-with |
| DamageFactor | 4000 | 2500 | 4500 | 3000 | 2500 | 5000 | 4000 | 2000 |  | How likely a the ship is to take damamage (ie. lose a prize) (0=special-case-never, 1=extremely likely, 5000=almost never) |
| DecoyMax | 1 | 1 | 2 | 2 | 1 | 1 | 2 | 1 |  | Maximum number of Decoys allowed in ships |
| DisableFastShooting | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | If firing bullets, bombs, or thors is disabled after using afterburners (1=enabled) (Cont .36+) |
| DoubleBarrel | 0 | 1 | 0 | 0 | 1 | 1 | 0 | 1 |  | Whether ships fire with double barrel bullets |
| EmpBomb | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 0 |  | Whether ships fire EMP bombs |
| Gravity | 800 | 800 | 800 | 800 | 800 | 800 | 800 | 800 |  | How strong of an effect the wormhole has on this ship (0 = none) |
| GravityTopSpeed | 250 | 250 | 250 | 250 | 250 | 250 | 250 | 250 |  | Ship are allowed to move faster than their maximum speed while effected by a wormhole.  This determines how much faster they can go (0 = no extra speed) |
| InitialBombs | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0..3 | Initial level a ship's bombs fire |
| InitialBounce | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..1 | Initial bouncing bullets upon spawn |
| InitialBounty | 750 | 750 | 750 | 750 | 750 | 750 | 750 | 750 |  | Number of 'Greens' given to ships when they start |
| InitialBrick | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Initial number of Bricks given to ships when they start |
| InitialBurst | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Initial number of Bursts given to ships when they start |
| InitialDecoy | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Initial number of Decoys given to ships when they start |
| InitialEnergy | 1800 | 2000 | 2200 | 1600 | 1500 | 2000 | 2200 | 1900 |  | Initial amount of energy that the ship can have |
| InitialGuns | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 0..3 | Initial level a ship's guns fire |
| InitialMultifire | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..1 | Multifire upon spawn |
| InitialPortal | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Initial number of Portals given to ships when they start |
| InitialProx | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..1 | Prox upon spawn |
| InitialRecharge | 3800 | 3200 | 3700 | 3900 | 3000 | 4000 | 3700 | 3500 |  | Initial recharge rate, or how quickly this ship recharges its energy |
| InitialRepel | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Initial number of Repels given to ships when they start |
| InitialRocket | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Initial number of Rockets given to ships when they start |
| InitialRotation | 360 | 300 | 380 | 260 | 300 | 340 | 320 | 260 |  | Initial rotation rate of the ship (0 = can't rotate, 400 = full rotation in 1 second) |
| InitialShield | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..1 | Temporary shields upon spawn |
| InitialShrapnel | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..31 | one or more shrapnel prizes upon spawn |
| InitialSpeed | 2800 | 2600 | 2800 | 2000 | 2600 | 3000 | 2500 | 3000 |  | Initial speed of ship (0 = can't move) |
| InitialSuper | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..1 | Temporary super upon spawn |
| InitialThor | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Initial number of Thor's Hammers given to ships when they start |
| InitialThrust | 24 | 24 | 24 | 24 | 24 | 24 | 24 | 24 |  | Initial thrust of ship (0 = none) |
| LandmineFireDelay | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 5 |  | Delay that ship waits after a mine is fired until another weapon may be fired (in ticks) |
| LandmineFireEnergy | 200 | 200 | 200 | 100 | 200 | 200 | 100 | 200 |  | Amount of energy it takes a ship to place a single L1 mine |
| LandmineFireEnergyUpgrade | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Extra amount of energy it takes to place an upgraded landmine. i.e. L2 = LandmineFireEnergy+LandmineFireEnergyUpgrade |
| LimitPerTeam | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> |  | The maximum number of this ship on any given frequency. -1 means no limit. |
| MaxBombs | 2 | 2 | 3 | 2 | 2 | 2 | 3 | 2 | 0..3 | Maximum level a ship's bombs can fire |
| MaxGuns | 3 | 2 | 3 | 2 | 3 | 3 | 2 | 3 | 0..3 | Maximum level a ship's guns can fire |
| MaximumEnergy | 5000 | 5500 | 5200 | 5200 | 5100 | 5000 | 5500 | 5000 |  | Maximum amount of energy that the ship can have |
| MaximumRecharge | 4500 | 5200 | 4700 | 5400 | 4200 | 5500 | 5000 | 4500 |  | Maximum recharge rate, or how quickly this ship recharges its energy |
| MaximumRotation | 380 | 300 | 380 | 320 | 340 | 400 | 340 | 360 |  | Maximum rotation rate of the ship (0 = can't rotate, 400 = full rotation in 1 second) |
| MaximumSpeed | 3400 | 3600 | 3600 | 2800 | 2900 | 3200 | 3000 | 3600 |  | Maximum speed of ship (0 = can't move) |
| MaximumThrust | 35 | 35 | 35 | 35 | 35 | 35 | 35 | 35 |  | Maximum thrust of ship (0 = none) |
| MaxMines | 2 | 3 | 2 | 12 | 4 | 2 | 7 | 3 |  | Maximum number of mines allowed in ships |
| MultiFireAngle | 645 | 500 | 505 | 800 | 999 | 200 | 800 | 1500 |  | Angle spread between multi-fire bullets and standard forward firing bullets (111 = 1 degree, 1000 = 1 ship-rotation-point) |
| MultiFireDelay | 10 | 7 | 8 | 6 | 7 | 9 | 7 | 13 |  | Delay that ship waits after a multifire bullet is fired until another weapon may be fired (in ticks) |
| MultiFireEnergy | 20 | 48 | 35 | 40 | 55 | 35 | 62 | 35 |  | Amount of energy it takes a ship to fire multifire L1 bullets |
| PortalMax | 2 | 2 | 2 | 2 | 2 | 2 | 2 | 2 |  | Maximum number of Portals allowed in ships |
| PrizeShareLimit | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Maximum bounty that ships receive Team Prizes |
| Radius | 0 | 14 | 14 | 14 | 14 | 0 | 14 | 14 | 0..255 | The ship's radius from center to outside, in pixels. (Cont .37+) |
| RepelMax | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Maximum number of Repels allowed in ships |
| RocketMax | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  | Maximum number of Rockets allowed in ships |
| RocketTime | 1200 | 1200 | 1200 | 9999 | 1200 | 1200 | 1200 | 1200 |  | How long a Rocket lasts (in ticks) |
| SeeBombLevel | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0..4 | If ship can see bombs on radar (0=Disabled, 1=All, 2=L2 and up, 3=L3 and up, 4=L4 bombs only) |
| SeeMines | 0 | 0 | 1 | 1 | 1 | 1 | 0 | 0 |  | Whether ships see mines on radar |
| ShieldsTime | 3000 | 3000 | 3000 | 3000 | 3000 | 3000 | 3000 | 3000 |  | How long Shields lasts on the ship (in ticks)  |
| ShrapBounce | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> |  | Wether this ship has bouncing shrapnel -1 = never; 1 = always When set to non 0 weapon packets will be rewritten |
| ShrapLevel | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..4 | The shrapnel level of this ship When set to non 0 weapon packets will be rewritten |
| ShrapnelMax | 15 | 20 | 15 | 31 | 15 | 15 | 22 | 20 |  | Maximum amount of shrapnel released from a ship's bomb |
| ShrapnelRate | 15 | 20 | 15 | 31 | 16 | 15 | 15 | 20 |  | Amount of additional shrapnel gained by a 'Shrapnel Upgrade' prize. |
| SoccerBallFriction | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 |  | Amount the friction on the soccer ball (how quickly it slows down -- higher numbers mean faster slowdown) |
| SoccerBallProximity | 150 | 150 | 150 | 150 | 150 | 150 | 150 | 150 |  | How close the player must be in order to pick up ball (in pixels) |
| SoccerBallSpeed | 4000 | 4000 | 4000 | 4000 | 4000 | 4000 | 4000 | 4000 |  | Initial speed given to the ball when fired by the carrier |
| SoccerThrowTime | 2000 | 2000 | 2000 | 2000 | 2000 | 2000 | 2000 | 2000 |  | Time player has to carry soccer ball (in ticks) |
| StealthEnergy | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Amount of energy required to have 'Stealth' activated (thousanths per tick) |
| StealthStatus | 1 | 0 | 1 | 1 | 0 | 1 | 0 | 0 | 0..2 | Whether ships are allowed to receive 'Stealth' 0=no 1=yes 2=yes/start-with |
| SuperTime | 2000 | 2000 | 2000 | 2000 | 700 | 2000 | 2000 | 2000 |  | How long Super lasts on the ship (in ticks) |
| ThorLevel | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..4 | The thor level this ship has. When set to non 0 weapon packets will be rewritten |
| ThorMax | 5 | 5 | 5 | 255 | 5 | 5 | 5 | 5 |  | Maximum number of Thor's Hammers allowed in ships |
| ThorShrap | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..31 | The amount of shrap thors have When set to non 0 weapon packets will be rewritten |
| ThorShrapBounce | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..1 | Wether this ship has bouncing shrapnel for thors -1 = never; 1 = always. When set to non 0 weapon packets will be rewritten |
| ThorShrapLevel | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | <unset> | 0..4 | The thor shrapnel level this ship has. When set to non 0 weapon packets will be rewritten |
| TurretLimit | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 4 |  | Number of turrets allowed on a ship |
| TurretSpeedPenalty | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Amount the ship's speed is decreased with a turret riding |
| TurretThrustPenalty | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Amount the ship's thrust is decreased with a turret riding |
| UpgradeEnergy | 800 | 800 | 800 | 800 | 800 | 800 | 800 | 800 |  | Amount added per 'Energy Upgrade' Prize |
| UpgradeRecharge | 350 | 350 | 350 | 350 | 350 | 350 | 350 | 350 |  | Amount added per 'Recharge Rate' Prize |
| UpgradeRotation | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 |  | Amount added per 'Rotation' Prize |
| UpgradeSpeed | 200 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |  | Amount added per 'Speed' Prize |
| UpgradeThrust | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 |  | Amount added per 'Thruster' Prize |
| XRadarEnergy | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |  | Amount of energy required to have 'X-Radar' activated (thousanths per tick) |
| XRadarStatus | 1 | 1 | 2 | 0 | 0 | 0 | 1 | 1 | 0..2 | Whether ships are allowed to receive 'X-Radar' 0=no 1=yes 2=yes/start-with |

## Zone-wide defaults (server.set)

Sections that apply zone-wide rather than per-ship. Format kept as raw
`Section:Field:default:min:max:Description` lines.

### Bomb

```
Bomb:BBombDamagePercent:1300:::Percentage of normal damage applied to a bouncing bomb (in 0.1%)
Bomb:BombAliveTime:250:::Time bomb is alive (in ticks)
Bomb:BombDamageLevel:300:::Amount of damage a bomb causes at its center point (for all bomb levels)
Bomb:BombExplodeDelay:111:::How long after the proximity sensor is triggered before bomb explodes
Bomb:BombExplodePixels:12:::Blast radius in pixels for an L1 bomb (L2 bombs double this, L3 bombs triple this)
Bomb:BombSafety:1:::Whether proximity bombs have a firing safety.  If enemy ship is within proximity radius, will it allow you to fire
Bomb:EBombDamagePercent:500:::Percentage of normal damage applied to an EMP bomb (in 0.1%)
Bomb:EBombShutdownTime:1000:::Maximum time recharge is stopped on players hit with an EMP bomb
Bomb:JitterTime:15:::How long the screen jitters from a bomb hit (in ticks)
Bomb:ProximityDistance:4:::Radius of proximity trigger in tiles (each bomb level adds 1 to this amount)
```

### Brick

```
Brick:AntibrickwarpDistance:<unset>:::Squared smallest distance allowed between players and new bricks before new bricks are cancelled to prevent brickwarping. 0 disables antibrickwarp feature.
Brick:BrickMode:3:::How bricks behave when they are dropped (BRICK_VIE=improved vie style, BRICK_AHEAD=drop in a line ahead of player, BRICK_LATERAL=drop laterally across player, BRICK_CAGE=drop 4 bricks simultaneously to create a cage)
Brick:BrickSpan:10:::The maximum length of a dropped brick.
Brick:BrickTime:1000:::How long bricks last (in ticks)
Brick:CountBricksAsWalls:1:::Whether bricks snap to the edges of other bricks (as opposed to only snapping to walls)
```

### Bullet

```
Bullet:BulletAliveTime:115:::How long bullets live before disappearing (in ticks)
Bullet:BulletDamageLevel:280:::Maximum amount of damage that a L1 bullet will cause
Bullet:BulletDamageUpgrade:80:::Amount of extra damage each bullet level will cause
Bullet:ExactDamage:0:::Whether to use exact bullet damage (Cont .36+)
```

### Burst

```
Burst:BurstDamageLevel:350:::Maximum amount of damage caused by a single burst bullet
```

### Chat

```
Chat:RestrictChat:<unset>:::This specifies an initial chat mask for the arena. Don't use this unless you know what you're doing.
```

### Cost

```
Cost:AntiWarp:0:::Points cost for AntiWarp Ability. 0 to disallow purchase.
Cost:Bomb:0:::Points cost for Bomb Upgrade. 0 to disallow purchase.
Cost:Bounce:0:::Points cost for Bouncing Bullets. 0 to disallow purchase.
Cost:Brick:0:::Points cost for Brick. 0 to disallow purchase.
Cost:Burst:0:::Points cost for Burst. 0 to disallow purchase.
Cost:Cloak:0:::Points cost for Cloak Ability. 0 to disallow purchase.
Cost:Decoy:0:::Points cost for Decoy. 0 to disallow purchase.
Cost:Energy:0:::Points cost for Energy Upgrade. 0 to disallow purchase.
Cost:Gun:0:::Points cost for Gun Upgrade. 0 to disallow purchase.
Cost:MultiFire:0:::Points cost for MultiFire. 0 to disallow purchase.
Cost:Portal:0:::Points cost for Portal. 0 to disallow purchase.
Cost:Prox:0:::Points cost for Proximity Bombs. 0 to disallow purchase.
Cost:PurchaseAnytime:1:::Whether players can buy items outside a safe zone.
Cost:Recharge:0:::Points cost for Recharge Upgrade. 0 to disallow purchase.
Cost:Repel:0:::Points cost for Repel. 0 to disallow purchase.
Cost:Rocket:0:::Points cost for Rocket. 0 to disallow purchase.
Cost:Rotation:0:::Points cost for Rotation Upgrade. 0 to disallow purchase.
Cost:Shield:0:::Points cost for Shields. 0 to disallow purchase.
Cost:Shrap:0:::Points cost for Shrapnel Upgrade. 0 to disallow purchase.
Cost:Speed:0:::Points cost for Top Speed. 0 to disallow purchase.
Cost:Stealth:0:::Points cost for Stealth Ability. 0 to disallow purchase.
Cost:Super:0:::Points cost for Super. 0 to disallow purchase.
Cost:Thor:0:::Points cost for Thor. 0 to disallow purchase.
Cost:Thrust:0:::Points cost for Thrust Upgrade. 0 to disallow purchase.
Cost:XRadar:0:::Points cost for XRadar. 0 to disallow purchase.
```

### CTF

```
CTF:NeutAfterKill:<unset>:::If enabled, a killed flagger drops his flag on the ground instead of resetting it An enemy can then steal it, or a friendly can return it home by touching it
CTF:Team0-Name:<unset>:::The name for this freq to display in arena messages
CTF:Team0-Region:<unset>:::The region where an enemy player can score
CTF:Team0-X:<unset>:::The X coordinate where the flag for this team will spawn (in tiles)
CTF:Team0-Y:<unset>:::The Y coordinate where the flag for this team will spawn (in tiles)
CTF:Team1-Name:<unset>:::The name for this freq to display in arena messages
CTF:Team1-Region:<unset>:::The region where an enemy player can score
CTF:Team1-X:<unset>:::The X coordinate where the flag for this team will spawn (in tiles)
CTF:Team1-Y:<unset>:::The Y coordinate where the flag for this team will spawn (in tiles)
CTF:WinCaptures:<unset>:::How many flag captures a team needs to win the game
```

### Door

```
Door:DoorDelay:0:::How often doors attempt to switch their state
Door:DoorMode:255:::Door mode (-2=all doors completely random, -1=weighted random (some doors open more often than others), 0-255=fixed doors (1 bit of byte for each door specifying whether it is open or not)
```

### DPrizeWeight

```
DPrizeWeight:AllWeapons:<unset>:::Likelihood of 'Super!' prize appearing
DPrizeWeight:AntiWarp:<unset>:::Likelihood of 'AntiWarp' prize appearing
DPrizeWeight:Bomb:<unset>:::Likelihood of 'Bomb Upgrade' prize appearing
DPrizeWeight:BouncingBullets:<unset>:::Likelihood of 'Bouncing Bullets' prize appearing
DPrizeWeight:Brick:<unset>:::Likelihood of 'Brick' prize appearing
DPrizeWeight:Burst:<unset>:::Likelihood of 'Burst' prize appearing
DPrizeWeight:Cloak:<unset>:::Likelihood of 'Cloak' prize appearing
DPrizeWeight:Decoy:<unset>:::Likelihood of 'Decoy' prize appearing
DPrizeWeight:Energy:<unset>:::Likelihood of 'Energy Upgrade' prize appearing
DPrizeWeight:Glue:<unset>:::Likelihood of 'Engine Shutdown' prize appearing
DPrizeWeight:Gun:<unset>:::Likelihood of 'Gun Upgrade' prize appearing
DPrizeWeight:MultiFire:<unset>:::Likelihood of 'MultiFire' prize appearing
DPrizeWeight:MultiPrize:<unset>:::Likelihood of 'Multi-Prize' prize appearing
DPrizeWeight:Portal:<unset>:::Likelihood of 'Portal' prize appearing
DPrizeWeight:Proximity:<unset>:::Likelihood of 'Proximity Bomb' prize appearing
DPrizeWeight:QuickCharge:<unset>:::Likelihood of 'Recharge' prize appearing
DPrizeWeight:Recharge:<unset>:::Likelihood of 'Full Charge' prize appearing (not 'Recharge')
DPrizeWeight:Repel:<unset>:::Likelihood of 'Repel' prize appearing
DPrizeWeight:Rocket:<unset>:::Likelihood of 'Rocket' prize appearing
DPrizeWeight:Rotation:<unset>:::Likelihood of 'Rotation' prize appearing
DPrizeWeight:Shields:<unset>:::Likelihood of 'Shields' prize appearing
DPrizeWeight:Shrapnel:<unset>:::Likelihood of 'Shrapnel Upgrade' prize appearing
DPrizeWeight:Stealth:<unset>:::Likelihood of 'Stealth' prize appearing
DPrizeWeight:Thor:<unset>:::Likelihood of 'Thor' prize appearing
DPrizeWeight:Thruster:<unset>:::Likelihood of 'Thruster' prize appearing
DPrizeWeight:TopSpeed:<unset>:::Likelihood of 'Speed' prize appearing
DPrizeWeight:Warp:<unset>:::Likelihood of 'Warp' prize appearing
DPrizeWeight:XRadar:<unset>:::Likelihood of 'XRadar' prize appearing
```

### Flag

```
Flag:CarryFlags:0:::Whether the flags can be picked up and carried (0=no, 1=yes, 2=yes-one at a time, 3=yes-two at a time, 4=three, etc..)
Flag:DropCenter:<unset>:::Whether flags dropped normally go in the center of the map, as opposed to near the player.
Flag:DropOwned:<unset>:::Whether flags you drop are owned by your team.
Flag:DropRadius:5:::How far from a player do dropped flags appear (in tiles).
Flag:EnterGameFlaggingDelay:1000:::Time a new player must wait before they are allowed to see flags
Flag:FlagBlankDelay:200:::Amount of time that a user can get no data from server before flags are hidden from view for 10 seconds
Flag:FlagCount:0:0:256:How many flags are present in this arena.
Flag:FlagDropDelay:18000:::Time before flag is dropped by carrier (0=never)
Flag:FlagDropResetReward:0:::Minimum kill reward that a player must get in order to have his flag drop timer reset
Flag:FlaggerBombFireDelay:0:::Delay given to flaggers for firing bombs (zero is ships normal firing rate) (do not set this number less than 20)
Flag:FlaggerBombUpgrade:1:::Whether the flaggers get a bomb upgrade
Flag:FlaggerDamagePercent:1500:::Percentage of normal damage received by flaggers (in 0.1%)
Flag:FlaggerFireCostPercent:1200:::Percentage of normal weapon firing cost for flaggers (in 0.1%)
Flag:FlaggerGunUpgrade:1:::Whether the flaggers get a gun upgrade
Flag:FlaggerKillMultiplier:2:::Number of times more points are given to a flagger (1 = double points, 2 = triple points)
Flag:FlaggerOnRadar:1:::Whether the flaggers appear on radar in red
Flag:FlaggerSpeedAdjustment:0:::Amount of speed adjustment player carrying flag gets (negative numbers mean slower)
Flag:FlaggerThrustAdjustment:0:::Amount of thrust adjustment player carrying flag gets (negative numbers mean less thrust)
Flag:FlagReward:10000:::The basic flag reward is calculated as (players in arena)^2 * reward / 1000.
Flag:FriendlyTransfer :<unset>:::Whether you get a teammates flags when you kill him.
Flag:NeutCenter:<unset>:::Whether flags that are neut-droped go in the center, as opposed to near the player who dropped them.
Flag:NeutOwned:<unset>:::Whether flags you neut-drop are owned by your team.
Flag:NoDataFlagDropDelay:500:::Amount of time that a user can get no data from server before flags he is carrying are dropped
Flag:ResetDelay:<unset>:::The length of the delay between flag games.
Flag:SafeCenter:<unset>:::Whether flags dropped from a safe zone spawn in the center, as opposed to near the safe zone player.
Flag:SafeOwned:0:::Whether flags dropped from a safe zone are owned by your team, as opposed to neutral.
Flag:SpawnRadius:50:::How far from the spawn center that new flags spawn (in tiles).
Flag:SpawnX:<unset>:::The X coordinate that new flags spawn at (in tiles).
Flag:SpawnY:<unset>:::The Y coordinate that new flags spawn at (in tiles).
Flag:SplitPoints:1:::Whether to split a flag reward between the members of a freq or give them each the full amount.
Flag:TeamChangeGrace:<unset>:::Period of time during which players are allowed to switch back to the winning team after leaving it.
Flag:TKCenter:<unset>:::Whether flags dropped by a team-kill spawn in the center, as opposed to near the killed player.
Flag:TKOwned:0:::Whether flags dropped by a team-kill are owned by your team, as opposed to neutral.
Flag:WinDelay:<unset>:::The delay between dropping the last flag and winning (ticks).
```

### GameCredits

```
GameCredits:GoalCreditsEnabled:<unset>:0:1:Enable credits for goals; Note that this requires a module calling credits->Goal(...)
GameCredits:GoalCreditsPerPlaying:<unset>:::
GameCredits:GoalExtraCredits:<unset>:::
GameCredits:GoalMaximumCredits:<unset>:::
GameCredits:goalScorerBonusMultiplier:<unset>:::
GameCredits:GoalSplit:<unset>:0:1:
GameCredits:KillAnnounce:<unset>:0:1:Send arena messages for kills
GameCredits:KillCreditsEnabled:<unset>:0:1:Do players recieve credits for kills
GameCredits:KillCreditsShareBetweenFreqs:<unset>:0:1:If you kill someone, do you get a portion of the credits for it on your other freqs
GameCredits:KillerSpreeCredits:<unset>:::When the killed is on a spree, the killer receives this much credits extra for every kill
GameCredits:KillerTeammateCreditsMultiplier:<unset>:::The maximum distance a teammate receives credits for kills
GameCredits:KillExtraCredits:<unset>:::Credits added to every kill
GameCredits:KillMaximumCredits:<unset>:::Maximum Credits you can receive for a kill
GameCredits:LVZEnabled:<unset>:::The maximum number of digits in the lvz
GameCredits:LVZImageStart:<unset>:::
GameCredits:LVZObjectStart:<unset>:::
GameCredits:MaxCredits:<unset>::: The maximum amount of credits someone may have; The credits go to his team members when full
GameCredits:OreCalculatePer:<unset>:::When calculating ore reward, divide it by this
GameCredits:OreCreditsEnabled:<unset>:0:1:Enable credits for ore mining; Note that this requires a module calling credits->Ore(...)
GameCredits:OreCreditsPerPlaying:<unset>:::
GameCredits:OreExtraCredits:<unset>:::
GameCredits:OreMaximumCredits:<unset>:::
GameCredits:OreScorerBonusPromille:<unset>::: How much more credits does the scorer get then his team members
GameCredits:OreSplit:<unset>:0:1:
GameCredits:OtherFreqGainPermille:<unset>:::How many credits (in permille) you get for freqs you are not on
```

### General

```
General:DesiredPlaying:<unset>:::This controls when the server will create new public arenas.
General:ExtraLevelFilesKeys:<unset>:0:15:How many extra keys (LevelFiles1, LevelFiles2, etc.) to use to generate the final arena LVZ list.
General:LevelFiles:spree.lvz gfx.lvz crown.lvz bd_safes.lvz hwgfx.lvz prizes.lvz:::The main LVZ list for the arena.
General:Map:maps/uploads/bd.lvl:::The name of the level file for this arena.
General:MaxPlaying:<unset>:::This is the most players that will be allowed to play in the arena at once. Zero means no limit.
General:NeedCap:<unset>:::If this setting is present for an arena, any player entering the arena must have the capability specified this setting. This can be used to restrict arenas to certain groups of players.
General:ScoreGroup:<unset>:::If this is set, it will be used as the score identifier for shared scores for this arena (unshared scores, e.g. per-game scores, always use the arena name as the identifier). Setting this to the same value in several different arenas will cause them to share scores.
```

### Kill

```
Kill:BountyIncreaseForKill:100:::Number of points added to players bounty each time he kills an opponent
Kill:EnterDelay:250:::How long after a player dies before he can re-enter the game (in ticks)
Kill:FixedKillReward:-1:::If -1 use the bounty of the killed player to calculate kill reward. Otherwise use this fixed value */
Kill:FlagMinimumBounty:<unset>:::The minimum bounty the killing player must have to get any bonus kill points for flags transferred, carried or owned.
Kill:JackpotBountyPercent:120000:::The percent of a player's bounty added to the jackpot on each kill. Units: 0.1%.
Kill:MaxBonus:0:::FIXME: fill this in
Kill:MaxPenalty:0:::FIXME: fill this in
Kill:PointsPerCarriedFlag:<unset>:::The number of extra points to give for each flag the killing player is carrying. Note that flags that were transfered to the killer as part of the kill are counted here, so adjust PointsPerKilledFlag accordingly.
Kill:PointsPerKilledFlag:<unset>:::The number of extra points to give for each flag a killed player was carrying. Note that the flags don't actually have to be transferred to the killer to be counted here.
Kill:PointsPerTeamFlag:<unset>:::The number of extra points to give for each flag owned by the killing team. Note that flags that were transfered to the killer as part of the kill are counted here, so adjust PointsPerKilledFlag accordingly.
Kill:RewardBase:0:::FIXME: fill this in
```

### Lag

```
Lag:C2SLossToDisallowFlags:<unset>:::The C2S packetloss when a player isn't allowed to pick up flags or balls. Units 0.1%.
Lag:C2SLossToSpec:<unset>:::The C2S packetloss at which to force a player to spec. Units 0.1%.
Lag:PingToDisallowFlags:500:::The average ping when a player isn't allowed to pick up flags or balls.
Lag:PingToIgnoreAllWeapons:<unset>:::The average ping when all weapons should be ignored.
Lag:PingToSpec:1000:::The average ping at which to force a player to spec.
Lag:PingToStartIgnoringWeapons:<unset>:::The average ping to start ignoring weapons at.
Lag:S2CLossToDisallowFlags:<unset>:::The S2C packetloss when a player isn't allowed to pick up flags or balls. Units 0.1%.
Lag:S2CLossToIgnoreAllWeapons:<unset>:::The S2C packetloss when all weapons should be ignored. Units 0.1%.
Lag:S2CLossToSpec:100:::The S2C packetloss at which to force a player to spec. Units 0.1%.
Lag:S2CLossToStartIgnoringWeapons:<unset>:::The S2C packetloss to start ignoring weapons at. Units 0.1%.
Lag:SpikeToSpec:3000:::The amount of time the server can get no data from a player before forcing him to spectator mode (in ms).
Lag:WeaponLossToDisallowFlags:<unset>:::The weapon packetloss when a player isn't allowed to pick up flags or balls. Units 0.1%.
Lag:WeaponLossToIgnoreAllWeapons:<unset>:::The weapon packetloss when all weapons should be ignored. Units 0.1%.
Lag:WeaponLossToSpec:<unset>:::The weapon packetloss at which to force a player to spec. Units 0.1%.
Lag:WeaponLossToStartIgnoringWeapons:<unset>:::The weapon packetloss to start ignoring weapons at. Units 0.1%.
```

### Latency

```
Latency:ClientSlowPacketSampleSize:1000:::Number of packets to sample S2C before checking for kickout
Latency:ClientSlowPacketTime:300:::Amount of latency S2C that constitutes a slow packet
Latency:S2CNoDataKickoutDelay:10000:::Amount of time a user can receive no data from server before connection is terminated
Latency:SendRoutePercent:500:::Percentage of the ping time that is spent on the C2S portion of the ping (used in more accurately syncronizing clocks)
```

### Legalship

```
Legalship:ArenaMask:<unset>:0:255:The ship mask of allowed ships in the arena. 1=warbird, 2=javelin, etc.
Legalship:Freq0Mask:<unset>:0:255:The ship mask allowed on freq 0. Ships must also be allowed on the arena mask. You can define a mask for any freq (FreqXMask).
Legalship:Freq1Mask:<unset>:0:255:The ship mask allowed on freq 1. Ships must also be allowed on the arena mask. You can define a mask for any freq (FreqXMask).
```

### log_staff

```
log_staff:commands:<unset>:::A list of commands that trigger messages to all logged-in staff.
```

### Message

```
Message:AllowAudioMessages:0:::Whether players can send audio messages
```

### Mine

```
Mine:MineAliveTime:5000:::Time that mines are active (in ticks)
Mine:TeamMaxMines:100:::Maximum number of mines allowed to be placed by an entire team
```

### Misc

```
Misc:ActivateAppShutdownTime:0:::Amount of time a ship is shutdown after application is reactivated
Misc:AllowSavedShips:0:::Whether saved ships are allowed (do not allow saved ship in zones where sub-arenas may have differing parameters)
Misc:AntiwarpFlagShipChange:<unset>:::prevents players with flags from changing ships while antiwarped.
Misc:AntiWarpSettleDelay:0:::How many ticks to activate a fake antiwarp after attaching, portaling, or warping.
Misc:AntiwarpShipChange:<unset>:::prevents players without flags from changing ships while antiwarped.
Misc:BounceFactor:26:::How bouncy the walls are (16 = no speed loss)
Misc:DecoyAliveTime:1500:::Time a decoy is alive (in ticks)
Misc:DisableScreenshot:0:::Whether to disable Continuum's screenshot feature (Cont .37+)
Misc:ExtraPositionData:0:::Whether regular players receive sysop data about a ship
Misc:FrequencyShift:5000:::Amount of random frequency shift applied to sounds in the game
Misc:GreetMessage:Welcome to BaseDuel! Two teams and one base that isn't big enough for the both of them! Clear the other team out, or reach their SafeZone first. This roundeye steak is so good! And .. DO THE TRUFFLE SHUFFLE!:::The message to send to each player on entering the arena.
Misc:MaxResArea:<unset>:::Maximum screen area (x*y) allowed in the arena, Zero means no limit.
Misc:MaxXres::::Maximum screen width allowed in the arena. Zero means no limit.
Misc:MaxYres::::Maximum screen height allowed in the arena. Zero means no limit.
Misc:NearDeathLevel:0:::Amount of energy that constitutes a near-death experience (ships bounty will be decreased by 1 when this occurs -- used for dueling zone)
Misc:NoSafeAntiwarp:<unset>:::Disables antiwarp on players in safe zones.
Misc:PeriodicMessage0:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage1:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage2:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage3:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage4:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage5:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage6:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage7:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage8:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:PeriodicMessage9:<unset>:::10 20 periodic message. 10 is the interval and 20 is the initial delay (in minutes)
Misc:RegionCheckInterval:<unset>:::How often to check for region enter/exit events (in ticks).
Misc:SafetyLimit:0:::Amount of time that can be spent in the safe zone (in ticks)
Misc:SeeEnergy:$SEE_ALL:::Whose energy levels everyone can see: SEE_NONE means nobody else's, SEE_ALL is everyone's, SEE_TEAM is only teammates.
Misc:SelfScoreReset:0:::Whether players can reset their own scores using ?scorereset. */
Misc:SendPositionDelay:10:::Amount of time between position packets sent by client
Misc:SheepMessage:Bahh:::The message that appears when someone says ?sheep
Misc:ShipChangeInterval:<unset>:::The allowable interval between player ship changes, in ticks.
Misc:SlowFrameCheck:0:::Whether to check for slow frames on the client (possible cheat technique) (flawed on some machines, do not use)
Misc:SpecSeeEnergy:$SEE_ALL:::Whose energy levels spectators can see. The options are the same as for Misc:SeeEnergy, with one addition: SEE_SPEC means only the player you're spectating.
Misc:SpecSeeExtra:<unset>:::Whether spectators can see extra data for the person they're spectating.
Misc:TeamKillPoints:<unset>:::Whether points are awarded for a team-kill.
Misc:TickerDelay:1000:::Amount of time between ticker help messages
Misc:TimedGame:0:::How long the game timer lasts (in ticks). Zero to disable.
Misc:VictoryMusic:1:::Whether the zone plays victory music or not
Misc:WarpPointDelay:12000:::How long a portal is active
Misc:WarpRadiusLimit:10:::When ships are randomly placed in the arena, this parameter will limit how far from the center of the arena they can be placed (1024=anywhere)
Misc:WarpTresholdDelta:<unset>:::The amount of change in a players position that is considered a warp (only while he is flashing). value is in pixels
```

### Modules

```
Modules:AttachModules:fm_normal,points_kill,points_flag,basewarp,score,handicap,ruletemplate:::This is a list of modules that you want to take effect in this arena. Not all modules need to be attached to arenas to function, but some do.
```

### Net

```
Net:AntiWarpSendPercent:<unset>:::Percent of position packets with antiwarp enabled to send to the whole arena.
Net:BulletPixels:30000:::How far away to always send bullets (in pixels)
Net:PositionExtraPixels:30000:::How far away to send positions of players on radar
Net:WeaponPixels:30000:::How far away to always send weapons (in pixels)
```

### Periodic

```
Periodic:RewardDelay:5000:::The interval between periodic rewards (in ticks). Zero to disable.
Periodic:RewardMinimumPlayers:1:::The minimum players necessary in the arena to give out periodic rewards.
Periodic:RewardPoints:1000:::Periodic rewards are calculated as follows: If this setting is positive, you get this many points per flag. If it's negative, you get it's absolute value points per flag, times the number of players in the arena.
Periodic:SendZeroRewards:<unset>:::Whether frequencies with zero points will still get a reward notification during the ding.
```

### Prize

```
Prize:DeathPrizeTime:12000:::How long the prize exists that appears after killing somebody
Prize:DontShareBrick:<unset>:::Whether Brick greens don't go to the whole team.
Prize:DontShareBurst:<unset>:::Whether Burst greens don't go to the whole team.
Prize:DontShareThor:<unset>:::Whether Thor greens don't go to the whole team.
Prize:EngineShutdownTime:1200:::Time the player is affected by an 'Engine Shutdown' Prize (in ticks)
Prize:MinimumVirtual:55:::Distance from center of arena that prizes/flags/soccer-balls will spawn
Prize:MultiPrizeCount:100:::Number of random greens given with a MultiPrize
Prize:PrizeDelay:1:::How often prizes are regenerated (in ticks)
Prize:PrizeFactor:5000:::Number of prizes hidden is based on number of players in game. This number adjusts the formula, higher numbers mean more prizes. (Note: 10000 is max, 10 greens per person)
Prize:PrizeHideCount:10000:::Number of prizes that are regenerated every PrizeDelay
Prize:PrizeMaxExist:12000:::Maximum amount of time that a hidden prize will remain on screen. (actual time is random)
Prize:PrizeMinExist:12000:::Minimum amount of time that a hidden prize will remain on screen. (actual time is random)
Prize:PrizeNegativeFactor:24000:::Odds of getting a negative prize.  (1 = every prize, 32000 = extremely rare)
Prize:TakePrizeReliable:0:::Whether prize packets are sent reliably (C2S)
Prize:TeamkillPrize:<unset>:::The prize # to give for a teamkill, if Prize:UseTeamkillPrize=1.
Prize:UpgradeVirtual:0:::Amount of additional distance added to MinimumVirtual for each player that is in the game
Prize:UseDeathPrizeWeights:<unset>:::Whether to use the DPrizeWeight section for death prizes instead of the PrizeWeight section.
Prize:UseTeamkillPrize:<unset>:::Whether to use a special prize for teamkills. Prize:TeamkillPrize specifies the prize #.
```

### PrizeWeight

```
PrizeWeight:AllWeapons:7:::Likelihood of 'Super!' prize appearing
PrizeWeight:AntiWarp:20:::Likelihood of 'AntiWarp' prize appearing
PrizeWeight:Bomb:50:::Likelihood of 'Bomb Upgrade' prize appearing
PrizeWeight:BouncingBullets:20:::Likelihood of 'Bouncing Bullets' prize appearing
PrizeWeight:Brick:0:::Likelihood of 'Brick' prize appearing
PrizeWeight:Burst:15:::Likelihood of 'Burst' prize appearing
PrizeWeight:Cloak:25:::Likelihood of 'Cloak' prize appearing
PrizeWeight:Decoy:10:::Likelihood of 'Decoy' prize appearing
PrizeWeight:Energy:45:::Likelihood of 'Energy Upgrade' prize appearing
PrizeWeight:Glue:0:::Likelihood of 'Engine Shutdown' prize appearing
PrizeWeight:Gun:50:::Likelihood of 'Gun Upgrade' prize appearing
PrizeWeight:MultiFire:30:::Likelihood of 'MultiFire' prize appearing
PrizeWeight:MultiPrize:15:::Likelihood of 'Multi-Prize' prize appearing
PrizeWeight:Portal:0:::Likelihood of 'Portal' prize appearing
PrizeWeight:Proximity:0:::Likelihood of 'Proximity Bomb' prize appearing
PrizeWeight:QuickCharge:70:::Likelihood of 'Recharge' prize appearing
PrizeWeight:Recharge:100:::Likelihood of 'Full Charge' prize appearing (not 'Recharge')
PrizeWeight:Repel:40:::Likelihood of 'Repel' prize appearing
PrizeWeight:Rocket:0:::Likelihood of 'Rocket' prize appearing
PrizeWeight:Rotation:70:::Likelihood of 'Rotation' prize appearing
PrizeWeight:Shields:0:::Likelihood of 'Shields' prize appearing
PrizeWeight:Shrapnel:25:::Likelihood of 'Shrapnel Upgrade' prize appearing
PrizeWeight:Stealth:25:::Likelihood of 'Stealth' prize appearing
PrizeWeight:Thor:0:::Likelihood of 'Thor' prize appearing
PrizeWeight:Thruster:30:::Likelihood of 'Thruster' prize appearing
PrizeWeight:TopSpeed:40:::Likelihood of 'Speed' prize appearing
PrizeWeight:Warp:0:::Likelihood of 'Warp' prize appearing
PrizeWeight:XRadar:30:::Likelihood of 'XRadar' prize appearing
```

### Radar

```
Radar:MapZoomFactor:6:::A number representing how much the map is zoomed out for radar. (48 = whole map on radar, 49+ = effectively disable radar)
Radar:RadarMode:0:::Radar mode (0=normal, 1=half/half, 2=quarters, 3=half/half-see team mates, 4=quarters-see team mates)
Radar:RadarNeutralSize:100:::Size of area between blinded radar zones (in pixels)
```

### Repel

```
Repel:RepelDistance:400:::Number of pixels from the player that are affected by a repel
Repel:RepelSpeed:5000:::Speed at which players are repelled
Repel:RepelTime:60:::Time players are affected by the repel (in ticks)
```

### Rocket

```
Rocket:RocketSpeed:100000:::Speed value given while a rocket is active
Rocket:RocketThrust:10000:::Thrust value given while a rocket is active
```

### Security

```
Security:MaxDeathWithoutFiring:4:::The number of times a player can die without firing a weapon before being placed in spectator mode.
```

### Shrapnel

```
Shrapnel:InactiveShrapDamage:100:::Amount of damage shrapnel causes in it's first 1/4 second of life
Shrapnel:Random:0:::Whether shrapnel spreads in circular or random patterns
Shrapnel:ShrapnelDamagePercent:1000:::Percentage of normal damage applied to shrapnel (relative to bullets of same level) (in 0.1%)
Shrapnel:ShrapnelSpeed:3000:::Speed that shrapnel travels
```

### Soccer

```
Soccer:AllowBombs:1:::Whether the ball carrier can fire his bombs
Soccer:AllowGoalByDeath:<unset>:::Whether a goal is scored if a player dies carrying the ball on a goal tile.
Soccer:AllowGuns:1:::Whether the ball carrier can fire his guns
Soccer:BallBlankDelay:50:::Amount of time a player can receive no data from server and still pick up the soccer ball
Soccer:BallBounce:1:::Whether the ball bounces off walls
Soccer:BallCount:0:::The number of balls in this arena.
Soccer:BallLocation:1:::Whether the balls location is displayed at all times or not
Soccer:CapturePoints:-500:::If positive, these points are distributed to each goal/team. When you make a goal, the points get transferred to your goal/team. If one team gets all the points, then they win as well.  If negative, teams are given 1 point for each goal, first team to reach -CapturePoints points wins the game.
Soccer:DisableBallKilling:0:::Whether to disable ball killing in safe zones (Cont .38+)
Soccer:DisableWallPass:1:::Whether to disable ball-passing through walls (Cont .38+)
Soccer:GoalDelay:<unset>:::How long after a goal before the ball appears (in ticks).
Soccer:KillerIgnorePassDelay:<unset>:::How much 'pass delay' should be trimmed off for someone killing a ball carrier.
Soccer:MinPlayers:<unset>:::The minimum number of players who must be playing for soccer points to be awarded.
Soccer:MinTeams:<unset>:::The minimum number of teams that must exist for soccer points to be awarded.
Soccer:Mode:0:::Goal configuration ($GOAL_ALL, $GOAL_LEFTRIGHT, $GOAL_TOPBOTTOM, $GOAL_CORNERS_3_1, $GOAL_CORNERS_1_3, $GOAL_SIDES_3_1, $GOAL_SIDES_1_3)
Soccer:NewGameDelay:<unset>:::How long to wait between games. If this is negative, the actual delay is random, between zero and the absolute value. Units: ticks.
Soccer:PassDelay:10:::How long after the ball is fired before anybody can pick it up (in ticks)
Soccer:Reward:-650064:::Negative numbers equal absolute points given, positive numbers use FlagReward formula.
Soccer:SendTime:300:25:500:How often the server sends ball positions (in ticks).
Soccer:SpawnRadius:<unset>:::How far from the spawn center the ball can spawn (in tiles).
Soccer:SpawnX:<unset>:0:1023:The X coordinate that the ball spawns at (in tiles).
Soccer:SpawnX/Y/RadiusN:<unset>:::The spawn coordinates and radius for balls other than the first one. N goes from 1 to 7 (0 is take care of by the settings without a number). If there are more balls than spawns defined, the latter balls will repeat the first spawns in order. For example, with 3 spawns, the fourth ball uses the first spawn, the fifth ball uses the second. If only part of a spawn is undefined, that part will default to the first spawn's setting.
Soccer:SpawnY:<unset>:0:1023:The Y coordinate that the ball spawns at (in tiles).
Soccer:UseFlagger:1:::If player with soccer ball should use the Flag:Flagger* ship adjustments or not
Soccer:WinBy:0:::Have to beat other team by this many goals
```

### Spawn

```
Spawn:Team0-Radius:5:::How large of a circle from the center point freq 0 can start. (Cont .38+)
Spawn:Team0-X:512:::If set to a value, this is the X coordinate for the center point where freq 0 will start. (Cont .38+)
Spawn:Team0-Y:512:::If set to a value, this is the Y coordinate for the center point where freq 0 will start. (Cont .38+)
Spawn:Team1-Radius:5:::How large of a circle from the center point freq 1 can start. (Cont .38+)
Spawn:Team1-X:512:::If set to a value, this is the X coordinate for the center point where freq 1 will start. (Cont .38+)
Spawn:Team1-Y:512:::If set to a value, this is the Y coordinate for the center point where freq 1 will start. (Cont .38+)
Spawn:Team2-Radius:5:::How large of a circle from the center point freq 2 can start. NOTE: if the Team2 settings are 0, Team0 will apply to evens and Team1 will apply to odds. (Cont .38+)
Spawn:Team2-X:512:::If set to a value, this is the X coordinate for the center point where freq 2 will start. NOTE: if the Team2 settings are 0, Team0 will apply to evens and Team1 will apply to odds. (Cont .38+)
Spawn:Team2-Y:512:::If set to a value, this is the Y coordinate for the center point where freq 2 will start. NOTE: if the Team2 settings are 0, Team0 will apply to evens and Team1 will apply to odds. (Cont .38+)
Spawn:Team3-Radius:5:::How large of a circle from the center point freq 3 can start. NOTE: Repeats, freq 4 will use Team0's, freq 5 will use Team1's, etc. (Cont .38+)
Spawn:Team3-X:512:::If set to a value, this is the X coordinate for the center point where freq 3 will start. NOTE: Repeats, freq 4 will use Team0's, freq 5 will use Team1's, etc. (Cont .38+)
Spawn:Team3-Y:512:::If set to a value, this is the Y coordinate for the center point where freq 3 will start. NOTE: Repeats, freq 4 will use Team0's, freq 5 will use Team1's, etc. (Cont .38+)
```

### Spectator

```
Spectator:HideFlags:0:::Whether to show dropped flags to spectators (Cont .36+)
Spectator:NoXRadar:0:::Whether spectators are disallowed from having X radar (Cont .36+)
```

### Team

```
Team:AllowFreqOwners:<unset>:::Whether to enable the freq ownership feature in this arena.
Team:BalancedAgainstEnd:<unset>:::Freqs >= BalancedAgainstStart and < BalancedAgainstEnd will be checked for balance even when players are not changing to or from these freqs. Set End < Start to disable this check.
Team:BalancedAgainstStart:<unset>:::Freqs >= BalancedAgainstStart and < BalancedAgainstEnd will be checked for balance even when players are not changing to or from these freqs. Set End < Start to disable this check.
Team:DesiredTeams:2:::The number of teams that the freq balancer will form as players enter.
Team:DisallowTeamSpectators:<unset>:::If players are allowed to spectate outside of the spectator frequency.
Team:ForceEvenTeams:2:::Whether the default balancer will enforce even teams. Does not apply if a custom balancer module is used.
Team:IncludeSpectators:<unset>:::Whether to include spectators when enforcing maximum freq sizes.
Team:InitialSpec:<unset>:::If players entering the arena are always assigned to spectator mode.
Team:MaxFrequency:100:1:10000:One more than the highest frequency allowed. Set this below PrivFreqStart to disallow private freqs.
Team:MaxPerPrivateTeam:10:::The maximum number of players on a private freq. Zero means these teams are not accessible.
Team:MaxPerTeam:20:::The maximum number of players on a public freq. Zero means these teams are not accessible
Team:MaxTeamDifference:<unset>:::How many players difference the balancer should tolerate. Does not apply if a custom balancer module is used.
Team:PrivFreqStart:<unset>:0:9999:Freqs above this value are considered private freqs.
Team:RequiredTeams:<unset>:::The number of teams that the freq manager will keep in memory. Must be at least as high as RequiredTeams.
Team:SpectatorFrequency:7265:0:9999:The frequency that spectators are assigned to, by default.
```

### Toggle

```
Toggle:AntiWarpPixels:1200:::Distance Anti-Warp affects other players (in pixels) (note: enemy must also be on radar)
```

### TurfReward

```
TurfReward:MinFlags:<unset>:::The minimum number of flags needed to be owned by a freq for that team to be eligable to recieve points.
TurfReward:MinFlagsPercent:<unset>:::The minimum percent of flags needed to be owned by a freq for that team to be eligable to recieve points. (ex. 18532 means 18.532%)
TurfReward:MinPercent:<unset>:::The minimum percent of points needed to be owned by a freq for that team to be eligable to recieve points. (ex. 18532 means 18.532%)
TurfReward:MinPlayersArena:<unset>:::The minimum number of players needed in the arena for anyone to be eligable to recieve points.
TurfReward:MinPlayersTeam:<unset>:::The minimum number of players needed on a team for players on that team to be eligable to recieve points.
TurfReward:MinTeams:<unset>:::The minimum number of teams needed in the arena for anyone to be eligable to recieve points.
TurfReward:MinWeights:<unset>:::The minimum number of weights needed to be owned by a freq for that team to be eligable to recieve points.
TurfReward:MinWeightsPercent:<unset>:::The minimum percent of weights needed to be owned by a freq for that team to be eligable to recieve points. (ex. 18532 means 18.532%)
TurfReward:RecoverDings:<unset>:::After losing a flag, the number of dings allowed to pass before a freq loses the chance to recover.  0 means you have no chance of recovery after it dings (to recover, you must recover before any ding occurs),  1 means it is allowed to ding once and you still have a chance to recover (any ding after that you lost chance of full recovery), ...
TurfReward:RecoverMax:<unset>:::Maximum number of times a flag may be recovered. (-1 means no max)
TurfReward:RecoverTime:<unset>:::After losing a flag, the time (seconds) allowed to pass before a freq loses the chance to recover.
TurfReward:RecoveryCutoff:<unset>:::Style of recovery cutoff to be used. TR_RECOVERY_DINGS - recovery cutoff based on RecoverDings. TR_RECOVERY_TIME - recovery cutoff based on RecoverTime. TR_RECOVERY_DINGS_AND_TIME - recovery cutoff based on both RecoverDings and RecoverTime.
TurfReward:RewardModifier:<unset>:::Modifies the number of points to award.  Meaning varies based on reward algorithm being used. For $REWARD_STD: jackpot = # players * RewardModifer
TurfReward:RewardStyle:<unset>:::The reward algorithm to be used.  Built in algorithms include: TR_STYLE_DISABLED: disable scoring, TR_STYLE_PERIODIC: normal periodic scoring but with the all the extra stats, TR_STYLE_STANDARD: see souce code documenation (complex formula) + jackpot based on # players TR_STYLE_STD_BTY: standard + jackpot based on bounty exchanged TR_STYLE_FIXED_PTS: each team gets a fixed # of points based on 1st, 2nd, 3rd,... place TR_STYLE_WEIGHTS: number of points to award equals number of weights owned
TurfReward:SafeRecievePoints:<unset>:::Whether players in safe zones recieve reward points.
TurfReward:SetWeights:<unset>:::How many weights to set from cfg (16 means you want to specify Weight0 to Weight15). If set to 0, then by default one weight is set with a value of 1.
TurfReward:SpecRecievePoints:<unset>:::Whether players in spectator mode recieve reward points.
TurfReward:TimerInitial:<unset>:::Inital turf_reward ding timer period.
TurfReward:TimerInterval:<unset>:::Subsequent turf_reward ding timer period.
TurfReward:WeightCalc:<unset>:::The method weights are calculated: TR_WEIGHT_TIME means each weight stands for one minute (ex: Weight004 is the weight for a flag owned for 4 minutes). TR_WEIGHT_DINGS means each weight stands for one ding of ownership (ex: Weight004 is the weight for a flag that was owned during 4 dings).
```

### Wormhole

```
Wormhole:GravityBombs:1:::Whether a wormhole affects bombs
Wormhole:SwitchTime:1000:::How often the wormhole switches its destination
```


## Source — template.sss

The `template.sss` file was the descriptive companion to `server.set` —
fields with their min/max ranges and descriptions but no per-ship defaults.
The descriptions below were the source of [`REFERENCE.md`](./REFERENCE.md);
kept here for traceability and because some lines have explicit min/max
ranges that REFERENCE.md does not.

### Notes

```
Notes:SettingName:::Name of the Game the settings "create"
Notes:Maker:::Creator of the settings
Notes:CoMaker:::Original and/or helper of the zone
Notes:MapName:::Map name(s) used with settings
Notes:Mapper:::Map Maker's name
Notes:Note1:::Any other misc notes you wish to add
Notes:Note2:::Any other misc notes you wish to add
Notes:Note3:::Any other misc notes you wish to add
Notes:Note4:::Any other misc notes you wish to add
Notes:Note5:::Any other misc notes you wish to add
```

### Bomb

```
Bomb:BombDamageLevel:::Amount of damage a bomb causes at its center point (for all bomb levels)
Bomb:BombAliveTime:::Time bomb is alive (in hundredths of a second)
Bomb:BombExplodeDelay:::How long after the proximity sensor is triggered before bomb explodes. (note: it explodes immediately if ship moves away from it after triggering it)
Bomb:BombExplodePixels:::Blast radius in pixels for an L1 bomb (L2 bombs double this, L3 bombs triple this)
Bomb:ProximityDistance:::Radius of proximity trigger in tiles.  Each bomb level adds 1 to this amount.
Bomb:JitterTime:::How long the screen jitters from a bomb hit. (in hundredths of a second)
Bomb:BombSafety:0:1:Whether proximity bombs have a firing safety (0=no 1=yes).  If enemy ship is within proximity radius, will it allow you to fire.
Bomb:EBombShutdownTime:::Maximum time recharge is stopped on players hit with an EMP bomb.
Bomb:EBombDamagePercent:::Percentage of normal damage applied to an EMP bomb 0=0% 1000=100% 2000=200%
Bomb:BBombDamagePercent:::Percentage of normal damage applied to a bouncing bomb 0=0% 1000=100% 2000=200%
```

### Brick

```
Brick:BrickTime:::How long bricks last (in hundredths of a second)
Brick:BrickSpan:::How many tiles bricks are able to span
```

### Bullet

```
Bullet:BulletDamageLevel:::Maximum amount of damage that a L1 bullet will cause. Formula; damage = squareroot(rand# * (max damage^2 + 1))
Bullet:BulletDamageUpgrade:::Amount of extra damage each bullet level will cause
Bullet:BulletAliveTime:::How long bullets live before disappearing (in hundredths of a second)
Bullet:ExactDamage:0:1:If damage is to be random or not (1=exact, 0=random)
```

### Burst

```
Burst:BurstDamageLevel:::Maximum amount of damage caused by a single burst bullet.
```

### Cost

```
Cost:PurchaseAnytime:0:1:Where prizes can be purchased 0 = safe zone, 1 = anywhere
Cost:Recharge:::Cost (in points) to purchase this prize
Cost:Energy:::Cost (in points) to purchase this prize
Cost:Rotation:::Cost (in points) to purchase this prize
Cost:Stealth:::Cost (in points) to purchase this prize
Cost:Cloak:::Cost (in points) to purchase this prize
Cost:XRadar:::Cost (in points) to purchase this prize
Cost:Gun:::Cost (in points) to purchase this prize
Cost:Bomb:::Cost (in points) to purchase this prize
Cost:Bounce:::Cost (in points) to purchase this prize
Cost:Thrust:::Cost (in points) to purchase this prize
Cost:Speed:::Cost (in points) to purchase this prize
Cost:MultiFire:::Cost (in points) to purchase this prize
Cost:Prox:::Cost (in points) to purchase this prize
Cost:Super:::Cost (in points) to purchase this prize
Cost:Shield:::Cost (in points) to purchase this prize
Cost:Shrap:::Cost (in points) to purchase this prize
Cost:AntiWarp:::Cost (in points) to purchase this prize
Cost:Repel:::Cost (in points) to purchase this prize
Cost:Burst:::Cost (in points) to purchase this prize
Cost:Decoy:::Cost (in points) to purchase this prize
Cost:Thor:::Cost (in points) to purchase this prize
Cost:Brick:::Cost (in points) to purchase this prize
Cost:Rocket:::Cost (in points) to purchase this prize
Cost:Portal:::Cost (in points) to purchase this prize
```

### Custom

```
Custom:SaveStatsTime:100000:100000000:How often a custom arena saves its scores to the hard drive (in case something goes wrong)
```

### Door

```
Door:DoorDelay:::How often doors attempt to switch their state.
Door:DoorMode:::Door mode (-2=all doors completely random, -1=weighted random (some doors open more often than others), 0-255=fixed doors (1 bit of byte for each door specifying whether it is open or not)
```

### Flag

```
Flag:FlaggerOnRadar:::Whether the flaggers appear on radar in red 0=no 1=yes
Flag:FlaggerKillMultiplier:::Number of times more points are given to a flagger (1 = double points, 2 = triple points)
Flag:FlaggerGunUpgrade:0:1:Whether the flaggers get a gun upgrade 0=no 1=yes
Flag:FlaggerBombUpgrade:0:1:Whether the flaggers get a bomb upgrade 0=no 1=yes
Flag:FlaggerFireCostPercent:::Percentage of normal weapon firing cost for flaggers 0=Super 1000=100% 2000=200%
Flag:FlaggerDamagePercent:::Percentage of normal damage received by flaggers 0=Invincible 1000=100% 2000=200%
Flag:FlaggerSpeedAdjustment:::Amount of speed adjustment player carrying flag gets (negative numbers mean slower)
Flag:FlaggerThrustAdjustment:::Amount of thrust adjustment player carrying flag gets (negative numbers mean less thrust)
Flag:FlaggerBombFireDelay:::Delay given to flaggers for firing bombs (0=ships normal firing rate -- note: please do not set this number less than 20)
Flag:CarryFlags:0:2:Whether the flags can be picked up and carried (0=no, 1=yes, 2=yes-one at a time)
Flag:FlagDropDelay:::Time before flag is dropped by carrier (0=never)
Flag:FlagDropResetReward:::Minimum kill reward that a player must get in order to have his flag drop timer reset.
Flag:EnterGameFlaggingDelay:::Time a new player must wait before they are allowed to see flags
Flag:FlagBlankDelay:::Amount of time that a user can get no data from server before flags are hidden from view for 10 seconds.
Flag:NoDataFlagDropDelay:::Amount of time that a user can get no data from server before flags he is carrying are dropped.
Flag:FlagMode:0:2:Style of flag game (0=dropped flags are un-owned, carry all flags to win)(1=dropped flags are owned, own all flags to win)(2=Turf style flag game)
Flag:FlagResetDelay:::Amount of time before an un-won flag game is reset (in hundredths of a second)   Advisable to be over 1000000
Flag:MaxFlags:0:32:Maximum number of flags in the arena. (0=no flag game)
Flag:RandomFlags:0:1:Whether the actual number of flags is randomly picked up to MaxFlags (0=no 1=yes)
Flag:FlagReward:::Number of points given for a flag victory. Formula = (playersInGame * playersInGame * FlagReward / 1000). (0=no flag victory is EVER declared)
Flag:FlagRewardMode:0:1:How flag reward points are divided up (0 = each team member gets rewardPoints) (1 = each team member gets (rewardPoints * numberOfTeamMembers / maximumAllowedPerTeam))
Flag:FlagTerritoryRadius:::When flagger drops flags, this is how spread out they are (distance from drop-centroid in tiles).  (note: 0 = special value meaning hide flags in center area of board as normal)
Flag:FlagTerritoryRadiusCentroid:::When flagger drops flags, this is how far the drop-centroid is randomly adjusted from the actual drop location) (note: 1024 = hide anywhere on level)
Flag:FriendlyTransfer:0:1:Whether the flaggers can transfer flags to other teammates (0=no 1=yes)
```

### Kill

```
Kill:MaxBonus:::Let's ignore these for now. Or let's not. :) This is if you have flags, can add more points per a kill. Founded by MGB
Kill:MaxPenalty:::Let's ignore these for now. Or let's not. :) This is if you have flags, can take away points per a kill. Founded by MGB
Kill:RewardBase:::Let's ignore these for now. Or let's not. :) This is shown added to a person's bty, but isn't added from points for a kill. Founded by MGB
Kill:BountyIncreaseForKill:::Number of points added to players bounty each time he kills an opponent.
Kill:EnterDelay:::How long after a player dies before he can re-enter the game.
Kill:KillPointsPerFlag:::Number of bonus points given to a player based on the number of flags his team has (as is done in Turf zone now)
Kill:KillPointsMinimumBounty:::Bounty of target must be over this value to get any KillPointsPerFlag bonus points.
Kill:DebtKills:::Number of kills a player must get after dying or resetting-ship before he starts getting points for kills. (0 = Normal)
Kill:NoRewardKillDelay:::If you kill the same guy twice within this amount of time, you get no points for the second kill. (in hundredths of a second)
Kill:BountyRewardPercent:::Percentage of your own bounty added to your reward when you kill somebody else.
Kill:FixedKillReward:::Fixed number of points given for any kill (regardless of bounty) (-1 = use bounty as always)
Kill:JackpotBountyPercent:::Percentage of kill value added to Jackpot 0=No Jackpot Game 1000=100% 2000=200%
```

### King

```
King:DeathCount:::Number of deaths a player is allowed until his crown is removed
King:ExpireTime:::Initial time given to each player at beginning of 'King of the Hill' round
King:RewardFactor:::Number of points given to winner of 'King of the Hill' round (uses FlagReward formula)
King:NonCrownAdjustTime:::Amount of time added for killing a player without a crown
King:NonCrownMinimumBounty:::Minimum amount of bounty a player must have in order to receive the extra time.
King:CrownRecoverKills:::Number of crown kills a non-crown player must get in order to get their crown back.
```

### Latency

```
Latency:SendRoutePercent:300:800:Percentage of the ping time that is spent on the ClientToServer portion of the ping. (used in more accurately syncronizing clocks)
Latency:KickOutDelay:100:2000:Amount of time the server can receive no data from the player before the player is kicked.
Latency:NoFlagDelay:100:1000:Amount of time before the server can receive no data from the player before it denies them the ability to pick up flags.
Latency:NoFlagPenalty:300:32000:Amount of time user is penalized when they exceed the NoFlagDelay.
Latency:SlowPacketKickoutPercent:0:1000:Percentage of C2S slow packets before a player is kicked.
Latency:SlowPacketTime:20:200:Amount of latency C2S that constitutes a slow packet.
Latency:SlowPacketSampleSize:50:1000:Number of packets to sample C2S before checking for kickout.
Latency:ClientSlowPacketKickoutPercent:0:1000:Percentage of S2C slow packets before a player is kicked.
Latency:ClientSlowPacketTime:20:200:Amount of latency S2C that constitutes a slow packet.
Latency:ClientSlowPacketSampleSize:50:1000:Number of packets to sample S2C before checking for kickout.
Latency:MaxLatencyForWeapons:20:200:Maximum C2S latency to allow before server disables weapons.
Latency:MaxLatencyForPrizes:50:800:Maximum amount of time that can pass before a shared prize packet is ignored.
Latency:MaxLatencyForKickOut:40:200:Maximum latency that allowed before kickout.
Latency:LatencyKickOutTime:300:3000:Amount of time that MaxLatencyForKickOut must be bad before the kickout occurs.
Latency:S2CNoDataKickoutDelay:100:32000:Amount of time a user can receive no data from server before connection is terminated.
Latency:CutbackWatermark:500:32000:Amount of data the server is allowed to send the user per second before it starts trying to cutback by skipping non-critical packets.
Latency:C2SNoDataAction:::0=Use Normal Method (Above settings) 1=Display Sysop Warning, 2=Spec Player, 3=Warning&Spec, 4=Kick, 5=Warning&Kick, 6=Kick&Spec, 7=Warning&Kick&Spec
Latency:C2SNoDataTime:::If above setting (C2SNoDataAction) is set, this is the delay (in 1/100 of a second [100 = 1 second, 60000=1 minute]) that a player's ping can be maxed at during a position packet before action takes place
Latency:NegativeClientSlowPacketTime:::Packets with future timestamp farther in future than this variable are considered as slow packets (0=disabled). Feature is still experimental.
```

### Message

```
Message:MessageReliable:0:1:Whether messages are sent reliably.
Message:AllowAudioMessages:0:1:Whether players can send audio messages (0=no 1=yes)
Message:BongAllowed:0:1:Whether players can play bong sounds (0=no 1=yes)
Message:QuickMessageLimit:::Maximum number of messages that can be sent in a row before player is kicked.
Message:MessageTeamReliable:0:1:Whether team messages are sent reliably.
Message:MessageDistance:::Don't think this is used anymore....
```

### Mine

```
Mine:MineAliveTime:0:60000:Time that mines are active (in hundredths of a second)
Mine:TeamMaxMines:0:32000:Maximum number of mines allowed to be placed by an entire team
```

### Misc

```
Misc:FrequencyShipTypes:0:1:Whether ship type is based on frequency player is on or not (0=no 1=yes)
Misc:WarpPointDelay:::How long a Portal point is active.
Misc:DecoyAliveTime:::Time a decoy is alive (in hundredths of a second)
Misc:BounceFactor:::How bouncy the walls are (16=no-speed-loss)
Misc:SafetyLimit:::Amount of time that can be spent in the safe zone. (90000 = 15 mins)
Misc:TickerDelay:::Amount of time between ticker help messages.
Misc:WarpRadiusLimit:::When ships are randomly placed in the arena, this parameter will limit how far from the center of the arena they can be placed (1024=anywhere)
Misc:ActivateAppShutdownTime:::Amount of time a ship is shutdown after application is reactivated (ie. when they come back from windows mode)
Misc:NearDeathLevel:::Amount of energy that constitutes a near-death experience (ships bounty will be decreased by 1 when this occurs -- used for dueling zone)
Misc:VictoryMusic:0:1:Whether the zone plays victory music or not.
Misc:BannerPoints:::Number of points require to display a banner
Misc:MaxLossesToPlay:::Number of deaths before a player is forced into spectator mode (0=never)
Misc:SpectatorQuiet:0:1:Whether spectators can talk to active players (1=no 0=yes)
Misc:MaxPlaying:::Maximum number of players that can be playing at one time (does not count spectators, 0=limited only by maximum allowed in arena (not a user setting))
Misc:TimedGame:::Amount of time in a timed game (like speed zone) (0=not a timed game)
Misc:ResetScoreOnFrequencyChange:0:1:Whether a players score should be reset when they change frequencies (used primarily for timed games like soccer)
Misc:SendPositionDelay:0:20:Amount of time between position packets sent by client.
Misc:SlowFrameCheck:0:1:Whether to check for slow frames on the client (possible cheat technique) (flawed on some machines, do not use)
Misc:SlowFrameRate:0:35:Check whether client has too slow frame rate to play (0=disabled, 1-35=Frame Rate limit, if < than this, kicked)
Misc:AllowSavedShips:0:1:Whether saved ships are allowed (do not allow saved ship in zones where sub-arenas may have differing parameters) 1 = Savedfrom last arena/lagout, 0 = New Ship when entering arena/zone
Misc:FrequencyShift:0:10000:Amount of random frequency shift applied to sounds in the game.
Misc:ExtraPositionData:0:1:Whether regular players receive sysop data about a ship (leave this at zero)
Misc:SheepMessage:::String that Appears when a player types ?sheep
Misc:MaxPlayers:::This is max amount of players in this arena. Will override the .ini max arena setting
Misc:GreetMessage:::This message, if set to anything, will be sent to the player who just enter arena/zone
Misc:PeriodicMessage0:::Info about this will be in explained through all 0-4 of these. Read next one down... To set it blank, set it to: 0
Misc:PeriodicMessage1:::Must be set to this format: (Time for repeating) (Delay after arena is created) (Text)  *Read Next!
Misc:PeriodicMessage2:::Time for repeating will be after every X mins (60=1hour, 1440=1day), will display this message. *READ NEXT!
Misc:PeriodicMessage3:::Delay will be after first person enters arena, it will then start this many mins after they enter. Good so they don't get one instantly for no reason. *READ NEXT!
Misc:PeriodicMessage4:::Text will be the text. If text starts with a * (ie: *Hello), will act as a *zone message. If no * (ie: Hello), will act as a *arena command.
Misc:MaxXRes:::Max X res limit, 0 = no limit
Misc:MaxYRes:::Max Y res limit, 0 = no limit
Misc:ContinuumOnly:::If set to 0, anyone can play. If set to 1, only continuum uses can play in ship, while all VIE clients will be locked in spec.
Misc:LevelFiles:::List of .lvz files, seperated by commas, that will be downloaded via client and used in this arena. A + in front of .lvz file will make it optional
Misc:MinUsage:::Min total usage hours required to play in this arena.
Misc:StartInSpec:::If set to 1, all users entering arena start in spec. Otherwise enter arena as normal.
Misc:MaxTimerDrift:::Percentage how much client timer is allowed to differ from server timer.
Misc:DisableScreenshot:::If set to 0, anyone can take screenshots. If set to 1, only spectators can.
Misc:AntiWarpSettleDelay:::Time (in 1/100 of a second) after someone warps/portals/attaches that they cannot warp/portal/attach again for (like a temp antiwarp enabled for them only)
Misc:SaveSpawnScore:::If set to 1, will save spawn scores to arenaname.scr. If set to 0, or blank, will not create useless .scr files.
```

### Owner

```
Owner:UserId:::User ID number for Users name
Owner:Name:::Owners Username
```

### PacketLoss

```
PacketLoss:C2SKickOutPercent:::ClientToServer packetloss percentage before being kicked (this is percentage that make it 800 = 80% good or allow 20% packetloss)
PacketLoss:S2CKickOutPercent:::ServerToClient packetloss percentage before being kicked (this is percentage that make it 800 = 80% good or allow 20% packetloss)
PacketLoss:SpectatorPercentAdjust:::Amount of extra packetloss a spectator is allowed to have.
PacketLoss:PacketLossDisableWeapons:0:1:Whether the server disables weapons for high packetloss or not (1=yes 0=no)
```

### Periodic

```
Periodic:RewardDelay:0:720000:Time interval between each periodic reward (0=no periodic reward)
Periodic:RewardMinimumPlayers:0:255:Number of players that must be in the arena before periodic rewards will occur
Periodic:RewardPoints:-500:1000:Number of points given out to team members (per flag owned).  (Negative numbers = flagCount * playersInArena)
```

### Prize

```
Prize:MultiPrizeCount:::Number of random 'Greens' given with a 'MultiPrize'
Prize:PrizeFactor:::Number of prizes hidden is based on number of players in game.  This number adjusts the formula, higher numbers mean more prizes. (*Note: 10000 is max, 10 greens per person)
Prize:PrizeDelay:::How often prizes are regenerated (in hundredths of a second)
Prize:PrizeHideCount:::Number of prizes that are regenerated every PrizeDelay.
Prize:MinimumVirtual:::Distance from center of arena that prizes/flags/soccer-balls will generate
Prize:UpgradeVirtual:::Amount of additional distance added to MinimumVirtual for each player that is in the game.
Prize:PrizeMaxExist:::Maximum amount of time that a hidden prize will remain on screen. (actual time is random)
Prize:PrizeMinExist:::Minimum amount of time that a hidden prize will remain on screen. (actual time is random)
Prize:PrizeNegativeFactor:::Odds of getting a negative prize.  (1 = every prize, 32000 = extremely rare)
Prize:DeathPrizeTime:::How long the prize exists that appears after killing somebody.
Prize:EngineShutdownTime:::Time the player is affected by an 'Engine Shutdown' Prize (in hundredth of a second)
Prize:TakePrizeReliable:0:1:Whether prize packets are sent reliably (C2S)
Prize:S2CTakePrizeReliable:0:1:Whether prize packets are sent reliably (S2C)
```

### PrizeWeight

```
PrizeWeight:Recharge:::Likelyhood of 'Full Charge' prize appearing (NOTE! This is FULL CHARGE, not Recharge!! stupid vie)
PrizeWeight:QuickCharge:::Likelyhood of 'Recharge' prize appearing
PrizeWeight:Energy:::Likelyhood of 'Energy Upgrade' prize appearing
PrizeWeight:Rotation:::Likelyhood of 'Rotation' prize appearing
PrizeWeight:Stealth:::Likelyhood of 'Stealth' prize appearing
PrizeWeight:Cloak:::Likelyhood of 'Cloak' prize appearing
PrizeWeight:AntiWarp:::Likelyhood of 'AntiWarp' prize appearing
PrizeWeight:XRadar:::Likelyhood of 'XRadar' prize appearing
PrizeWeight:Warp:::Likelyhood of 'Warp' prize appearing
PrizeWeight:Gun:::Likelyhood of 'Gun Upgrade' prize appearing
PrizeWeight:Bomb:::Likelyhood of 'Bomb Upgrade' prize appearing
PrizeWeight:BouncingBullets:::Likelyhood of 'Bouncing Bullets' prize appearing
PrizeWeight:Thruster:::Likelyhood of 'Thruster' prize appearing
PrizeWeight:TopSpeed:::Likelyhood of 'Speed' prize appearing
PrizeWeight:MultiFire:::Likelyhood of 'MultiFire' prize appearing
PrizeWeight:Proximity:::Likelyhood of 'Proximity Bomb' prize appearing
PrizeWeight:Glue:::Likelyhood of 'Engine Shutdown' prize appearing
PrizeWeight:AllWeapons:::Likelyhood of 'Super!' prize appearing
PrizeWeight:Shields:::Likelyhood of 'Shields' prize appearing
PrizeWeight:Shrapnel:::Likelyhood of 'Shrapnel Upgrade' prize appearing
PrizeWeight:Repel:::Likelyhood of 'Repel' prize appearing
PrizeWeight:Burst:::Likelyhood of 'Burst' prize appearing
PrizeWeight:Decoy:::Likelyhood of 'Decoy' prize appearing
PrizeWeight:Thor:::Likelyhood of 'Thor' prize appearing
PrizeWeight:Portal:::Likelyhood of 'Portal' prize appearing
PrizeWeight:Brick:::Likelyhood of 'Brick' prize appearing
PrizeWeight:Rocket:::Likelyhood of 'Rocket' prize appearing
PrizeWeight:MultiPrize:::Likelyhood of 'Multi-Prize' prize appearing
```

### Radar

```
Radar:RadarMode:0:4:Radar mode (0=normal, 1=half/half, 2=quarters, 3=half/half-see team mates, 4=quarters-see team mates)
Radar:RadarNeutralSize:0:1024:Size of area between blinded radar zones (in pixels)
Radar:MapZoomFactor:8:1000:A number representing how far you can see on radar.
```

### Repel

```
Repel:RepelSpeed:::Speed at which players are repelled
Repel:RepelTime:::Time players are affected by the repel (in hundredths of a second)
Repel:RepelDistance:::Number of pixels from the player that are affected by a repel.
```

### Rocket

```
Rocket:RocketThrust:::Thrust value given while a rocket is active.
Rocket:RocketSpeed:::Speed value given while a rocket is active.
```

### Routing

```
Routing:RadarFavor:1:7:Number of packets somebody on radar receives (1 = every packet, 3 = every fourth packet, 7 = every eighth packet)
Routing:CloseEnoughBulletAdjust:0:512:Distance off edge of screen in pixels that bullet packets will always forward to player.
Routing:CloseEnoughBombAdjust:0:4096:Distance off edge of radar in pixels that bomb packets will always forward to player. (in direction bomb is heading only)
Routing:DeathDistance:1000:16384:Distance death messages are forwarded.
Routing:DoubleSendPercent:500:900:Percentage packetloss at which server starts double sending weapon packets.
Routing:WallResendCount:0:3:Number of times a create wall packet is sent unreliably (in additional to the reliable send)
Routing:QueuePositions:::Set to 1 to use following 4 settings:
Routing:PosSendRadar:::How long radar packets are queued, default 100 ms 
Routing:PosSendEdge:::How long packets on screen edge are queued, default 30 ms
Routing:PosSendClose:::How long close packets are queue, default 20 ms
Routing:ClosePosPixels:::How near are packets considered close, default 250 pixels
```

### Security

```
Security:S2CKickOutPercentWeapons:::The percent kickout for not getting weapon packets.
Security:SecurityKickOff:0:1:Whether players doing security violations get kicked off or not.
Security:SuicideLimit:::Maximum number of suicides before player is kicked (no longer used since there are no suicides???)
Security:MaxShipTypeSwitchCount:::Number of times a player can change ship type without being removed from the arena
Security:PacketModificationMax:::Maximum number of modified packets allowed before a security violation is triggered.
Security:MaxDeathWithoutFiring:::Number of times a player can die without firing before being removed from the arena.
```

### Shrapnel

```
Shrapnel:ShrapnelSpeed:::Speed that shrapnel travels
Shrapnel:InactiveShrapDamage:::Amount of damage shrapnel causes in it's first 1/4 second of life.
Shrapnel:ShrapnelDamagePercent:::Percentage of normal damage applied to shrapnel (relative to bullets of same level) 0=0% 1000=100% 2000=200%
Shrapnel:Random:0:1:Whether shrapnel spreads in circular or random patterns 0=circular 1=random
```

### Spawn

```
Spawn:Team0-X:::If set to a value, this is the center point where Freq 0 will start
Spawn:Team0-Y:::If set to a value, this is the center point where Freq 0 will start
Spawn:Team0-Radius:::How large of a circle from center point can they warp (in Tiles)
Spawn:Team1-X:::If set to a value, this is the center point where Freq 1 will start
Spawn:Team1-Y:::If set to a value, this is the center point where Freq 1 will start
Spawn:Team1-Radius:::How large of a circle from center point can they warp (in Tiles)
Spawn:Team2-X:::If set to a value, this is the center point where Freq 2 will start *NOTE: If not set, but 0 and 1 set, will loop between 0 and 1
Spawn:Team2-Y:::If set to a value, this is the center point where Freq 2 will start *NOTE: If not set, but 0 and 1 set, will loop between 0 and 1
Spawn:Team2-Radius:::How large of a circle from center point can they warp (in Tiles)
Spawn:Team3-X:::If set to a value, this is the center point where Freq 3 will start *NOTE: Repeats, Freq 4 will use Team0's, Freq 5 use Team1's, etc
Spawn:Team3-Y:::If set to a value, this is the center point where Freq 3 will start *NOTE: Repeats, Freq 4 will use Team0's, Freq 5 use Team1's, etc
Spawn:Team3-Radius:::How large of a circle from center point can they warp (in Tiles)
```

### Soccer

```
Soccer:BallBounce:0:1:Whether the ball bounces off walls (0=ball go through walls, 1=ball bounces off walls)
Soccer:AllowBombs:0:1:Whether the ball carrier can fire his bombs (0=no 1=yes)
Soccer:AllowGuns:0:1:Whether the ball carrier can fire his guns (0=no 1=yes)
Soccer:PassDelay:0:10000:How long after the ball is fired before anybody can pick it up (in hundredths of a second)
Soccer:Mode:0:6:Goal configuration (0=any goal, 1=left-half/right-half, 2=top-half/bottom-half, 3=quadrants-defend-one-goal, 4=quadrants-defend-three-goals, 5=sides-defend-one-goal, 6=sides-defend-three-goals)
Soccer:BallCount:::Number of soccer balls in the arena (0=soccer game off)
Soccer:SendTime:::How often the balls position is updated (note: set larger if you have more soccer balls to prevent too much modem traffic)
Soccer:Reward:::Negative numbers equal absolute points given, positive numbers use FlagReward formula.
Soccer:CapturePoints:::If positive, these points are distributed to each goal/team.  When you make a goal, the points get transferred to your goal/team.  In timed games, team with most points in their goal wins.  If one team gets all the points, then they win as well.  If negative, teams are given 1 point for each goal, first team to reach -CapturePoints points wins the game.
Soccer:UseFlagger:0:1:If player with soccer ball should use the Flag:Flagger* ship adjustments or not (0=no, 1=yes)
Soccer:BallLocation:0:1:Whether the balls location is displayed at all times or not (0=not, 1=yes)
Soccer:BallBlankDelay:::Amount of time a player can receive no data from server and still pick up the soccer ball.
Soccer:CatchMinimum:::Minimun goals needed to win
Soccer:CatchPoints:::Max goals needed to win
Soccer:WinBy:::Have to beat other team by this many goals
Soccer:DisableWallPass:::Set to 1 to disable passing of ball through a wall
Soccer:DisableBallKilling:::Set to 1 to disable people dieing in safety with the ball
```

### Team

```
Team:MaxFrequency:::Maximum number of frequencies allowed in arena (5 would allow frequencies 0,1,2,3,4)
Team:MaxPerTeam:::Maximum number of players on a non-private frequency
Team:MaxPerPrivateTeam:::Maximum number of players on a private frequency (0=same as MaxPerTeam)
Team:DesiredTeams:::Number of teams the server creates when adding new players before it starts adding new players to existing teams.
Team:ForceEvenTeams:::Whether people are allowed to change teams if it would make the teams uneven.  0 = no restrictions, 1-10 = allowed variance
Team:SpectatorFrequency:::Frequency reserved for spectators (does not have to be within MaxFrequency limit)
```

### Territory

```
Territory:RewardDelay:::Time interval between each territory reward (0=no territory reward)
Territory:RewardBaseFlags:::Minimum number of flags required to receive the territory reward
Territory:RewardMinimumPlayers:::Minimum number of players required in game to receive the territory reward
Territory:RewardPoints:::Amount of points given out to the players at end of each time interval (formula is complicated)
```

### Toggle

```
Toggle:AntiWarpPixels:::Distance Anti-Warp affects other players (in pixels) (note: enemy must also be on radar)
```

### Wormhole

```
Wormhole:GravityBombs:0:1:Whether a wormhole affects bombs (0=no 1=yes)
Wormhole:SwitchTime:::How often the wormhole switches its destination.
```

### All

```
All:InitialRotation:::Initial rotation rate of the ship (0 = can't rotate, 400 = full rotation in 1 second)
All:InitialThrust:::Initial thrust of ship (0 = none)
All:InitialSpeed:::Initial speed of ship (0 = can't move)
All:InitialRecharge:::Initial recharge rate, or how quickly this ship recharges its energy.
All:InitialEnergy:::Initial amount of energy that the ship can have.
All:MaximumRotation:::Maximum rotation rate of the ship (0 = can't rotate, 400 = full rotation in 1 second)
All:MaximumThrust:::Maximum thrust of ship (0 = none)
All:MaximumSpeed:::Maximum speed of ship (0 = can't move)
All:MaximumRecharge:::Maximum recharge rate, or how quickly this ship recharges its energy.
All:MaximumEnergy:::Maximum amount of energy that the ship can have.
All:UpgradeRotation:::Amount added per 'Rotation' Prize
All:UpgradeThrust:::Amount added per 'Thruster' Prize
All:UpgradeSpeed:::Amount added per 'Speed' Prize
All:UpgradeRecharge:::Amount added per 'Recharge Rate' Prize
All:UpgradeEnergy:::Amount added per 'Energy Upgrade' Prize
All:CloakStatus:0:2:Whether ships are allowed to receive 'Cloak' 0=no 1=yes 2=yes/start-with
All:StealthStatus:0:2:Whether ships are allowed to receive 'Stealth' 0=no 1=yes 2=yes/start-with
All:XRadarStatus:0:2:Whether ships are allowed to receive 'X-Radar' 0=no 1=yes 2=yes/start-with
All:AntiWarpStatus:0:2:Whether ships are allowed to receive 'Anti-Warp' 0=no 1=yes 2=yes/start-with
All:CloakEnergy:0:32000:Amount of energy required to have 'Cloak' activated (thousanths per hundredth of a second)
All:StealthEnergy:0:32000:Amount of energy required to have 'Stealth' activated (thousanths per hundredth of a second)
All:XRadarEnergy:0:32000:Amount of energy required to have 'X-Radar' activated (thousanths per hundredth of a second)
All:AntiWarpEnergy:0:32000:Amount of energy required to have 'Anti-Warp' activated (thousanths per hundredth of a second)
All:InitialRepel:::Initial number of Repels given to ships when they start
All:InitialBurst:::Initial number of Bursts given to ships when they start
All:InitialBrick:::Initial number of Bricks given to ships when they start
All:InitialRocket:::Initial number of Rockets given to ships when they start
All:InitialThor:::Initial number of Thor's Hammers given to ships when they start
All:InitialDecoy:::Initial number of Decoys given to ships when they start
All:InitialPortal:::Initial number of Portals given to ships when they start
All:InitialGuns:0:3:Initial level a ship's guns fire 0=no guns
All:InitialBombs:0:3:Initial level a ship's bombs fire 0=no bombs
All:RepelMax:::Maximum number of Repels allowed in ships
All:BurstMax:::Maximum number of Bursts allowed in ships
All:DecoyMax:::Maximum number of Decoys allowed in ships
All:RocketMax:::Maximum number of Rockets allowed in ships
All:ThorMax:::Maximum number of Thor's Hammers allowed in ships
All:BrickMax:::Maximum number of Bricks allowed in ships
All:PortalMax:::Maximum number of Portals allowed in ships
All:MaxGuns:0:3:Maximum level a ship's guns can fire 0=no guns
All:MaxBombs:0:3:Maximum level a ship's bombs can fire 0=no bombs
All:BulletFireEnergy:::Amount of energy it takes a ship to fire a single L1 bullet
All:BulletSpeed:::How fast bullets travel
All:BulletFireDelay:::delay that ship waits after a bullet is fired until another weapon may be fired (in hundredths of a second)
All:MultiFireEnergy:::Amount of energy it takes a ship to fire multifire L1 bullets
All:MultiFireDelay:::delay that ship waits after a multifire bullet is fired until another weapon may be fired (in hundredths of a second)
All:MultiFireAngle:::Angle spread between multi-fire bullets and standard forward firing bullets. (111 = 1 degree, 1000 = 1 ship-rotation-point)
All:DoubleBarrel:0:1:Whether ships fire with double barrel bullets 0=no 1=yes
All:BombFireEnergy:::Amount of energy it takes a ship to fire a single bomb
All:BombFireEnergyUpgrade:::Extra amount of energy it takes a ship to fire an upgraded bomb. ie. L2 = BombFireEnergy+BombFireEnergyUpgrade
All:BombThrust:::Amount of back-thrust you receive when firing a bomb.
All:BombBounceCount:::Number of times a ship's bombs bounce before they explode on impact
All:BombSpeed:::How fast bombs travel
All:BombFireDelay:::delay that ship waits after a bomb is fired until another weapon may be fired (in hundredths of a second)
All:EmpBomb:0:1:Whether ships fire EMP bombs 0=no 1=yes
All:SeeBombLevel:0:4:If ship can see bombs on radar (0=Disabled, 1=All, 2=L2 and up, 3=L3 and up, 4=L4 bombs only)
All:MaxMines:::Maximum number of mines allowed in ships
All:SeeMines:0:1:Whether ships see mines on radar 0=no 1=yes
All:LandmineFireEnergy:::Amount of energy it takes a ship to place a single L1 mine
All:LandmineFireEnergyUpgrade:::Extra amount of energy it takes to place an upgraded landmine.  ie. L2 = LandmineFireEnergy+LandmineFireEnergyUpgrade
All:LandmineFireDelay:::delay that ship waits after a mine is fired until another weapon may be fired (in hundredths of a second)
All:ShrapnelMax:0:31:Maximum amount of shrapnel released from a ship's bomb
All:ShrapnelRate:0:31:Amount of additional shrapnel gained by a 'Shrapnel Upgrade' prize.
All:BurstSpeed:::How fast the burst shrapnel is for this ship.
All:BurstShrapnel:::Number of bullets released when a 'Burst' is activated
All:TurretThrustPenalty:::Amount the ship's thrust is decreased with a turret riding
All:TurretSpeedPenalty:::Amount the ship's speed is decreased with a turret riding
All:TurretLimit:::Number of turrets allowed on a ship.
All:RocketTime:::How long a Rocket lasts (in hundredths of a second)
All:InitialBounty:::Number of 'Greens' given to ships when they start
All:AttachBounty:::Bounty required by ships to attach as a turret
All:AfterburnerEnergy:::Amount of energy required to have 'Afterburners' activated.
All:DisableFastShooting:0:1:If firing bullets, bombs, or thors is disabled after using afterburners (1=enabled)
All:Radius:::The ship's radius from center to outside, in pixels. Standard value is 14 pixels.
All:DamageFactor:::How likely a the ship is to take damamage (ie. lose a prize) (0=special-case-never, 1=extremely likely, 5000=almost never)
All:PrizeShareLimit:::Maximum bounty that ships receive Team Prizes
All:SuperTime:1::How long Super lasts on the ship (in hundredths of a second)
All:ShieldsTime:1::How long Shields lasts on the ship (in hundredths of a second)
All:Gravity:::Uses this formula, where R = raduis (tiles) and g = this setting; R = 1.325 * (g ^ 0.507)  IE: If set to 500, then your ship will start to get pulled in by the wormhole once you come within 31 tiles of it
All:GravityTopSpeed:::Ship are allowed to move faster than their maximum speed while effected by a wormhole.  This determines how much faster they can go (0 = no extra speed)
All:SoccerBallFriction:::Amount the friction on the soccer ball (how quickly it slows down -- higher numbers mean faster slowdown)
All:SoccerBallProximity:::How close the player must be in order to pick up ball (in pixels)
All:SoccerBallSpeed:::Initial speed given to the ball when fired by the carrier.
All:SoccerThrowTime:::Time player has to carry soccer ball (in hundredths of a second)
```

### Spectator

```
Spectator:HideFlags:0:1:If flags are to be shown to specs when they are dropped (1=can't see them)
Spectator:NoXRadar:0:1:If specs are allowed to have X (0=yes, 1=no)
```

### +Maker

```
+Maker:Maker:::Editing was done by Mine GO BOOM with the help of the letter K. Version 1.34.14   For more help, visit http://www.shanky.com/server/
```


## Source — SettingsTypes.java (Java key catalog)

Before deletion (commit `81bea3a`, shipped in v1.0.8),
`infinity/settings/SettingsTypes.java` held 372 `private static String`
constants of the form `SECTION_FIELDNAME = "Section-FieldName"`. The class was
`@SuppressWarnings("unused")` and had zero callers — kept for historical
completeness because it was the most complete Java-side enumeration of which
INI fields the codebase was designed to read.

**Format note**: this catalog uses `-` as the section/field separator. The
`.sss` / `.set` blocks above use `:`. On-disk `.ini` files use `[Section]`
headers with `Field = value` lines. All three are flat-key projections of
the same INI data.

### Notes

```
Notes-SettingName
Notes-Maker
Notes-CoMaker
Notes-MapName
Notes-Mapper
Notes-Note1
Notes-Note2
Notes-Note3
Notes-Note4
Notes-Note5
```

### Bomb

```
Bomb-BombDamageLevel
Bomb-BombAliveTime
Bomb-BombExplodeDelay
Bomb-BombExplodePixels
Bomb-ProximityDistance
Bomb-JitterTime
Bomb-BombSafety
Bomb-EBombShutdownTime
Bomb-EBombDamagePercent
Bomb-BBombDamagePercent
```

### Brick

```
Brick-BrickTime
Brick-BrickSpan
```

### Bullet

```
Bullet-BulletDamageLevel
Bullet-BulletDamageUpgrade
Bullet-BulletAliveTime
Bullet-ExactDamage
```

### Burst

```
Burst-BurstDamageLevel
```

### Cost

```
Cost-PurchaseAnytime
Cost-Recharge
Cost-Energy
Cost-Rotation
Cost-Stealth
Cost-Cloak
Cost-XRadar
Cost-Gun
Cost-Bomb
Cost-Bounce
Cost-Thrust
Cost-Speed
Cost-MultiFire
Cost-Prox
Cost-Super
Cost-Shield
Cost-Shrap
Cost-AntiWarp
Cost-Repel
Cost-Burst
Cost-Decoy
Cost-Thor
Cost-Brick
Cost-Rocket
Cost-Portal
```

### Custom

```
Custom-SaveStatsTime
```

### Door

```
Door-DoorDelay
Door-DoorMode
```

### Flag

```
Flag-FlaggerOnRadar
Flag-FlaggerKillMultiplier
Flag-FlaggerGunUpgrade
Flag-FlaggerBombUpgrade
Flag-FlaggerFireCostPercent
Flag-FlaggerDamagePercent
Flag-FlaggerSpeedAdjustment
Flag-FlaggerThrustAdjustment
Flag-FlaggerBombFireDelay
Flag-CarryFlags
Flag-FlagDropDelay
Flag-FlagDropResetReward
Flag-EnterGameFlaggingDelay
Flag-FlagBlankDelay
Flag-NoDataFlagDropDelay
Flag-FlagMode
Flag-FlagResetDelay
Flag-MaxFlags
Flag-RandomFlags
Flag-FlagReward
Flag-FlagRewardMode
Flag-FlagTerritoryRadius
Flag-FlagTerritoryRadiusCentroid
Flag-FriendlyTransfer
```

### Kill

```
Kill-MaxBonus
Kill-MaxPenalty
Kill-RewardBase
Kill-BountyIncreaseForKill
Kill-EnterDelay
Kill-KillPointsPerFlag
Kill-KillPointsMinimumBounty
Kill-DebtKills
Kill-NoRewardKillDelay
Kill-BountyRewardPercent
Kill-FixedKillReward
Kill-JackpotBountyPercent
```

### King

```
King-DeathCount
King-ExpireTime
King-RewardFactor
King-NonCrownAdjustTime
King-NonCrownMinimumBounty
King-CrownRecoverKills
```

### Latency

```
Latency-SendRoutePercent
Latency-KickOutDelay
Latency-NoFlagDelay
Latency-NoFlagPenalty
Latency-SlowPacketKickoutPercent
Latency-SlowPacketTime
Latency-SlowPacketSampleSize
Latency-ClientSlowPacketKickoutPercent
Latency-ClientSlowPacketTime
Latency-ClientSlowPacketSampleSize
Latency-MaxLatencyForWeapons
Latency-MaxLatencyForPrizes
Latency-MaxLatencyForKickOut
Latency-LatencyKickOutTime
Latency-S2CNoDataKickoutDelay
Latency-CutbackWatermark
Latency-C2SNoDataAction
Latency-C2SNoDataTime
Latency-NegativeClientSlowPacketTime
```

### Message

```
Message-MessageReliable
Message-AllowAudioMessages
Message-BongAllowed
Message-QuickMessageLimit
Message-MessageTeamReliable
Message-MessageDistance
```

### Mine

```
Mine-MineAliveTime
Mine-TeamMaxMines
```

### Misc

```
Misc-FrequencyShipTypes
Misc-WarpPointDelay
Misc-DecoyAliveTime
Misc-BounceFactor
Misc-SafetyLimit
Misc-TickerDelay
Misc-WarpRadiusLimit
Misc-ActivateAppShutdownTime
Misc-NearDeathLevel
Misc-VictoryMusic
Misc-BannerPoints
Misc-MaxLossesToPlay
Misc-SpectatorQuiet
Misc-MaxPlaying
Misc-TimedGame
Misc-ResetScoreOnFrequencyChange
Misc-SendPositionDelay
Misc-SlowFrameCheck
Misc-SlowFrameRate
Misc-AllowSavedShips
Misc-FrequencyShift
Misc-ExtraPositionData
Misc-SheepMessage
Misc-MaxPlayers
Misc-GreetMessage
Misc-PeriodicMessage0
Misc-PeriodicMessage1
Misc-PeriodicMessage2
Misc-PeriodicMessage3
Misc-PeriodicMessage4
Misc-MaxXRes
Misc-MaxYRes
Misc-ContinuumOnly
Misc-LevelFiles
Misc-MinUsage
Misc-StartInSpec
Misc-MaxTimerDrift
Misc-DisableScreenshot
Misc-AntiWarpSettleDelay
Misc-SaveSpawnScore
```

### Owner

```
Owner-UserId
Owner-Name
```

### PacketLoss

```
PacketLoss-C2SKickOutPercent
PacketLoss-S2CKickOutPercent
PacketLoss-SpectatorPercentAdjust
PacketLoss-PacketLossDisableWeapons
```

### Periodic

```
Periodic-RewardDelay
Periodic-RewardMinimumPlayers
Periodic-RewardPoints
```

### Prize

```
Prize-MultiPrizeCount
Prize-PrizeFactor
Prize-PrizeDelay
Prize-PrizeHideCount
Prize-MinimumVirtual
Prize-UpgradeVirtual
Prize-PrizeMaxExist
Prize-PrizeMinExist
Prize-PrizeNegativeFactor
Prize-DeathPrizeTime
Prize-EngineShutdownTime
Prize-TakePrizeReliable
Prize-S2CTakePrizeReliable
```

### PrizeWeight

```
PrizeWeight-Recharge
PrizeWeight-QuickCharge
PrizeWeight-Energy
PrizeWeight-Rotation
PrizeWeight-Stealth
PrizeWeight-Cloak
PrizeWeight-AntiWarp
PrizeWeight-XRadar
PrizeWeight-Warp
PrizeWeight-Gun
PrizeWeight-Bomb
PrizeWeight-BouncingBullets
PrizeWeight-Thruster
PrizeWeight-TopSpeed
PrizeWeight-MultiFire
PrizeWeight-Proximity
PrizeWeight-Glue
PrizeWeight-AllWeapons
PrizeWeight-Shields
PrizeWeight-Shrapnel
PrizeWeight-Repel
PrizeWeight-Burst
PrizeWeight-Decoy
PrizeWeight-Thor
PrizeWeight-Portal
PrizeWeight-Brick
PrizeWeight-Rocket
PrizeWeight-MultiPrize
```

### Radar

```
Radar-RadarMode
Radar-RadarNeutralSize
Radar-MapZoomFactor
```

### Repel

```
Repel-RepelSpeed
Repel-RepelTime
Repel-RepelDistance
```

### Rocket

```
Rocket-RocketThrust
Rocket-RocketSpeed
```

### Routing

```
Routing-RadarFavor
Routing-CloseEnoughBulletAdjust
Routing-CloseEnoughBombAdjust
Routing-DeathDistance
Routing-DoubleSendPercent
Routing-WallResendCount
Routing-QueuePositions
Routing-PosSendRadar
Routing-PosSendEdge
Routing-PosSendClose
Routing-ClosePosPixels
```

### Security

```
Security-S2CKickOutPercentWeapons
Security-SecurityKickOff
Security-SuicideLimit
Security-MaxShipTypeSwitchCount
Security-PacketModificationMax
Security-MaxDeathWithoutFiring
```

### Shrapnel

```
Shrapnel-ShrapnelSpeed
Shrapnel-InactiveShrapDamage
Shrapnel-ShrapnelDamagePercent
Shrapnel-Random
```

### Spawn

```
Spawn-Team0-X
Spawn-Team0-Y
Spawn-Team0-Radius
Spawn-Team1-X
Spawn-Team1-Y
Spawn-Team1-Radius
Spawn-Team2-X
Spawn-Team2-Y
Spawn-Team2-Radius
Spawn-Team3-X
Spawn-Team3-Y
Spawn-Team3-Radius
```

### Soccer

```
Soccer-BallBounce
Soccer-AllowBombs
Soccer-AllowGuns
Soccer-PassDelay
Soccer-Mode
Soccer-BallCount
Soccer-SendTime
Soccer-Reward
Soccer-CapturePoints
Soccer-UseFlagger
Soccer-BallLocation
Soccer-BallBlankDelay
Soccer-CatchMinimum
Soccer-CatchPoints
Soccer-WinBy
Soccer-DisableWallPass
Soccer-DisableBallKilling
```

### Team

```
Team-MaxFrequency
Team-MaxPerTeam
Team-MaxPerPrivateTeam
Team-DesiredTeams
Team-ForceEvenTeams
Team-SpectatorFrequency
```

### Territory

```
Territory-RewardDelay
Territory-RewardBaseFlags
Territory-RewardMinimumPlayers
Territory-RewardPoints
```

### Toggle

```
Toggle-AntiWarpPixels
```

### Wormhole

```
Wormhole-GravityBombs
Wormhole-SwitchTime
```

### All

```
All-InitialRotation
All-InitialThrust
All-InitialSpeed
All-InitialRecharge
All-InitialEnergy
All-MaximumRotation
All-MaximumThrust
All-MaximumSpeed
All-MaximumRecharge
All-MaximumEnergy
All-UpgradeRotation
All-UpgradeThrust
All-UpgradeSpeed
All-UpgradeRecharge
All-UpgradeEnergy
All-CloakStatus
All-StealthStatus
All-XRadarStatus
All-AntiWarpStatus
All-CloakEnergy
All-StealthEnergy
All-XRadarEnergy
All-AntiWarpEnergy
All-InitialRepel
All-InitialBurst
All-InitialBrick
All-InitialRocket
All-InitialThor
All-InitialDecoy
All-InitialPortal
All-InitialGuns
All-InitialBombs
All-RepelMax
All-BurstMax
All-DecoyMax
All-RocketMax
All-ThorMax
All-BrickMax
All-PortalMax
All-MaxGuns
All-MaxBombs
All-BulletFireEnergy
All-BulletSpeed
All-BulletFireDelay
All-MultiFireEnergy
All-MultiFireDelay
All-MultiFireAngle
```

### All_DoubleBarrel

```
All_DoubleBarrel
```

### All_BombFireEnergy

```
All_BombFireEnergy
```

### All_BombFireEnergyUpgrade

```
All_BombFireEnergyUpgrade
```

### All_BombThrust

```
All_BombThrust
```

### All_BombBounceCount

```
All_BombBounceCount
```

### All_BombSpeed

```
All_BombSpeed
```

### All_BombFireDelay

```
All_BombFireDelay
```

### All_EmpBomb

```
All_EmpBomb
```

### All_SeeBombLevel

```
All_SeeBombLevel
```

### All_MaxMines

```
All_MaxMines
```

### All_SeeMines

```
All_SeeMines
```

### All_LandmineFireEnergy

```
All_LandmineFireEnergy
```

### All_LandmineFireEnergyUpgrade

```
All_LandmineFireEnergyUpgrade
```

### All_LandmineFireDelay

```
All_LandmineFireDelay
```

### All_ShrapnelMax

```
All_ShrapnelMax
```

### All_ShrapnelRate

```
All_ShrapnelRate
```

### All_BurstSpeed

```
All_BurstSpeed
```

### All_BurstShrapnel

```
All_BurstShrapnel
```

### All_TurretThrustPenalty

```
All_TurretThrustPenalty
```

### All_TurretSpeedPenalty

```
All_TurretSpeedPenalty
```

### All_TurretLimit

```
All_TurretLimit
```

### All_RocketTime

```
All_RocketTime
```

### All_InitialBounty

```
All_InitialBounty
```

### All_AttachBounty

```
All_AttachBounty
```

### All_AfterburnerEnergy

```
All_AfterburnerEnergy
```

### All_DisableFastShooting

```
All_DisableFastShooting
```

### All_Radius

```
All_Radius
```

### All_DamageFactor

```
All_DamageFactor
```

### All_PrizeShareLimit

```
All_PrizeShareLimit
```

### All_SuperTime

```
All_SuperTime
```

### All_ShieldsTime

```
All_ShieldsTime
```

### All_Gravity

```
All_Gravity
```

### All_GravityTopSpeed

```
All_GravityTopSpeed
```

### All_SoccerBallFriction

```
All_SoccerBallFriction
```

### All_SoccerBallProximity

```
All_SoccerBallProximity
```

### All_SoccerBallSpeed

```
All_SoccerBallSpeed
```

### All_SoccerThrowTime

```
All_SoccerThrowTime
```

### Spectator_HideFlags

```
Spectator_HideFlags
```

### Spectator_NoXRadar

```
Spectator_NoXRadar
```

### Maker_Maker

```
Maker_Maker
```

