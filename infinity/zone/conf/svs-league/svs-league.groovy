// SVS league/prac variant — composes the leaf section files with league
// overrides on top.
//
// Source: http://forum.svssubspace.com/showthread.php?tid=14496 (and a lot of
// guesswork). League/prac rules: ships start with items and can get items from
// death prizes.

include '/conf/svs-league/ship-warbird.groovy'
include '/conf/svs-league/ship-javelin.groovy'
include '/conf/svs-league/ship-spider.groovy'
include '/conf/svs-league/ship-leviathan.groovy'
include '/conf/svs-league/ship-terrier.groovy'
include '/conf/svs-league/ship-weasel.groovy'
include '/conf/svs-league/ship-lancaster.groovy'
include '/conf/svs-league/ship-shark.groovy'
include '/conf/svs-league/misc.groovy'
include '/conf/svs-league/bullet.groovy'
include '/conf/svs-league/mine.groovy'
include '/conf/svs-league/prizeweights.groovy'

// League/prac item baseline — splatted across every ship after the per-ship
// includes above so the item overrides win on key conflict.
shipSections('Warbird', 'Javelin', 'Spider', 'Leviathan',
             'Terrier', 'Weasel', 'Lancaster', 'Shark') {
    BurstMax 0
    RepelMax 2
    DecoyMax 2
    ThorMax 1
    BrickMax 1
    RocketMax 3
    PortalMax 2
    InitialBurst 0
    InitialRepel 2
    InitialDecoy 2
    InitialThor 1
    InitialBrick 0
    InitialRocket 3
    InitialPortal 2
}

// Leviathan-only override: starts with one brick (in addition to the league
// baseline above).
shipSection('Leviathan') {
    InitialBrick 1
}

section('Prize') {
    UseDeathPrizeWeights 1
}

// Death prizes give some chance of getting items.
//
// Consider 4v4 with 3 lives each — 24 lives, max 23 death-prize pickups.
// 50% chance of getting an item:
//   1/3 Decoy, 1/3 Portal, 1/6 Rocket, 1/12 Brick, 1/24 Repel, 1/24 Thor.
// 25% full charge, 25% energy upgrade (effectively nothing).
section('DPrizeWeight') {
    NullPrize 0
    QuickCharge 0
    Energy 12
    Rotation 0
    Stealth 0
    Cloak 0
    AntiWarp 0
    XRadar 0
    Warp 0
    Gun 0
    Bomb 0
    BouncingBullets 0
    Thruster 0
    TopSpeed 0
    Recharge 12
    MultiFire 0
    Proximity 0
    Glue 0
    AllWeapons 0
    Shields 0
    Shrapnel 0
    Repel 1
    Burst 0
    Decoy 8
    Thor 1
    Portal 8
    Brick 2
    Rocket 4
    MultiPrize 0
}
