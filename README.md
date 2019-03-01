# Archived

Repository is archived. Moved to Azure DevOps because I wanted Wiki to be included in main repository and not as a seperate repository.

Go here if you want to be kept up to date: https://dev.azure.com/assofohdz/Subspace-Infinity

# Build Stats:

[![Build Status](https://travis-ci.org/assofohdz/Subspace-Infinity.svg?branch=master)](https://travis-ci.org/assofohdz/Subspace-Infinity)

# Subspace Infinity
A java port of Subspace Continuum, built on JME, Lemur, Zay-ES, Zay-ES-Net, SpiderMonkey, and the SimEthereal real-time
object synching library.  This modifies the sim-eth-basic to be ES based using the Zay-ES library.

# Community

## Discord

Join Discord here: https://discord.gg/tfyWxbK

# To contribute

## Setup foundation

With Chocolatey:
- Run: "choco install git.install"
- Run: "choco install gradle" (version 5.0 required at the moment of writing this)

## Check out the project

- Clone the repository (git clone)
- Run: "gradle build"

# To run:

## From command line
- Run: "gradle run"

## From itch.io
Game is pushed to Itch.io on updates, so client in Itch.io will keep up to date.

# Splash
![Splash screen](https://github.com/assofohdz/Subspace-Infinity/blob/master/screenshots/Splash.PNG?=150x)

# Game
![In game](https://github.com/assofohdz/Subspace-Infinity/blob/master/screenshots/InGame1.PNG?=150x)

# Subspace features

Alpha
- Game world with top down view and very basic controls of the ship
- Physics
- External modules 
- EventBus
- Console command interface for external modules
- Subspace features ported:
  - Choosing ship
  - Defense and offense with discrete and continuous usages
    - Guns
    - Bombs
  - Legacy map loading 

Beta
- Subspace features ported (per ship settings):
  - Defense and offense with discrete and continuous usages
    - Thor
      - Initial count
      - Max count
    - Mines
      - energy cost
      - energy cost when upgrading
      - fire delay
      - max active mines count
      - can see mines
    - Burst
      - Max count
      - Initial count
      - Count of shrapnel
      - Speed
    - Repel
      - Initial count
      - Max count
    - Warp
    - Rocket
      - Initial count
      - Max count
      - Rocket time
    - Brick
      - Initial count
      - Max count
    - Portal
      - Initial count
      - Max count
    - Decoy
      - Initial count
      - Max count
      - Max active decoys
    - Thruster
    - Guns
      - fire delay
      - fire energy cost
      - speed
      - energy cost when upgrading
      - max level
      - initial level
      - double barrel
    - Bombs
      - fire delay
      - fire energy cost
      - energy cost when upgrading
      - speed
      - max level
      - bounce counts
      - initial level
      - Max shrapnel
      - Shrapnel increment
      - initial damage
      - damage per upgrade
    - Multifire:
      - fire delay
      - fire energy cost
      - speed
      - angle
  - Ship utilities to be turned on/off
    - X-Radar
      - energy cost per time unit
    - Stealth
      - energy cost per time unit
    - Bouncing bombs/bullets
    - Proximity bombs
    - Stealth
      - energy cost per time unit
    - Cloak
      - energy cost per time unit
    - AntiWarp
      - energy cost per time unit
    - MultiFire
    - Shrapnel
      - Count increase when upgrading
      - Damage
  - Ship movement
    - Rotation
      - Initial speed
      - Max speed
      - Upgrades
    - Speed
      - Initial speed
      - Max speed
      - Upgrades
    - Recharge
      - Initial recharge
      - Max recharge
      - Upgrades
    - Energy
      - Initial energy
      - Max energy
      - Upgrades
    - Thrust
      - Initial thrust
      - Max thrust
      - Upgrades
      - Afterburner energy cost (thrust?) per time unit
  - Turrets
    - Thrust penalty
    - Speed penalty
    - Limit
  - Bounty and rewards (perhaps rewards are based on bounty tiers)
    - Initial bounty
    - Attach bounty
  - Soccer ball
    - Friction
    - Proximity
    - Throw time
    - Ball speed
  - Unknown
    - SuperTime=6000
    - ShieldsTime=4000
    - Gravity=1500
    - GravityTopSpeed=100
    - BombThrust=400
    - CloakStatus=0
    - StealthStatus=1
    - XRadarStatus=1
    - AntiWarpStatus=1
    - DamageFactor=30
    - PrizeShareLimit=100
    - EmpBomb=0
    
- Subspace features ported (global settings):
  - Bounties and costs for purchasing
    - Quick charge
    - Energy
    - Rotation
    - Stealth
    - Cloak
    - AntiWarp
    - XRadar
    - Warp
    - Gun level
    - Bomb level
    - BouncingBullets
    - Thruster
    - TopSpeed
    - Recharge
    - MultiFire
    - Proximity
    - Glue
    - AllWeapons
    - Shields
    - Shrapnel
    - Repel
    - Burst
    - Decoy
    - Thor
    - Portal
    - Brick
    - Rocket
    - MultiPrize
  - Bullets
    - damage
    - damage per upgrade
    - Alive time
  - Bombs
    - damage
    - damage per upgrade
    - Alive time
    - ProximityDistance
    - JitterTime (screen shake?)
    - Close Bomb safety (if fired upon very close distance)
    - EMP upgrade
      - Shut down time
      - E Damage percent
      - B Damage percent  
  - Mines
    - Alive time
    - Team max mines
  - Shrapnel
    - Speed
    - Inactive Shrap Damage
    - Damage percent 
    - Random angle
  - Burst
    - Damage
  - AntiWarp
    - Range
  - Prize
    - Multi prize count
    - Prize Factor
    - Prize delay
    - Prize hide count
    - Minimum virtual
    - Upgrade virtual
    - Max exist
    - Min exist
    - Negative factor
    - Death prize
    - Engine shut down time
  - Flag
    - Flagger on radar
    - Flagger kill multiplier
    - Flagger gun upgrade
    - Flagger bomb upgrade
    - Flagger fire cost percentage
    - Flagger damage percent
    - Flagger bomb fire delay
    - Flagger speed adjustment
    - Flagger thrust adjustment
    - Carry Flags count
    - Flag Drop Delay
    - Flag Drop Reset Reward
    - Enter Game Flagging Delay
    - Flag Blank Delay
    - No Data Flag Drop Delay
    - Flag Mode
    - Flag Reset Delay
    - Max flags
    - Random flags
    - Flag reward
    - Flag reward mode
    - Flag territory radius
    - Flag territory radius centroid
    - Friendly transfer
  - Soccer
    - Ball bounce
    - Allow bombs
    - Allow guns
    - Pass delay
    - Mode
    - Ball blank delay
    - Use flagger
    - Ball location
    - Ball count
    - Send time
    - Reward
    - Capture points
  - Radar
    - Radar mode
    - Radar netrual size
    - Map zoom factor
  - Team
    - Max frequencies
    - Max per team
    - Max per private team
    - Force even teams
    - Desired teams
    - Spectator frequency
  - Kill
    - Max bonus
    - Max pentalty
    - Reward base
    - Bounty increase per kill
    - Enter delay
    - Kill points per flag
    - Kill points minimum bounty
    - Debt kills
    - No reward kill delay
    - Bounty reward percent
    - Fixed kill reward
    - Jackpot bounty percent
  - Repel (should now be physics based)
    - Repel speed
    - Repel time
    - Repel distance
  - Wormhole
    - Gravity bombs
    - Switch time
  - Brick
    - Alive time
    - Brick size
  - Rocket
    - Thrust
    - Speed
  - Door
    - Delay
    - Mode
  - Misc
    - WarpPointDelay=6000
    - DecoyAliveTime=3000
    - BounceFactor=22
    - SendPositionDelay=10
    - SlowFrameCheck=0
    - AllowSavedShips=1
    - SafetyLimit=90000
    - FrequencyShift=900
    - TickerDelay=1000
    - ExtraPositionData=0
    - WarpRadiusLimit=1024
    - ActivateAppShutdownTime=1500
    - NearDeathLevel=0
    - VictoryMusic=1
    - FrequencyShipTypes=0
    - BannerPoints=5000
    - MaxLossesToPlay=0
    - SpectatorQuiet=0
    - TimedGame=0
    - SheepMessage=Sheep successfully cloned -- hello Dolly
    - ResetScoreOnFrequencyChange=0
    - MaxPlaying=0

## Authors

* **Asser Fahrenholz** - *Initial work and Subspace port* - [AssoFohdz](https://github.com/AssoFohdz)
* **Henning Sonne** - *AI and TD game logic* - 

See also the list of [contributors](https://github.com/assofohdz/subspace-infinity/contributors) who participated in this project.

## License

[TODO]

## Acknowledgments

Big thanks to the JME community and Paul Speed who has laid the foundation with the awesome Zay-ES framework and his game example.

Thanks to the Subspace/Continuum developers who keeps the game alive. If not for them, this java port would not be here.
