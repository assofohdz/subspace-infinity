// SVS league dueling variant — composes the leaf section files with dueling
// overrides on top.
//
// Source: http://forum.svssubspace.com/showthread.php?tid=14496 (and a lot of
// guesswork). Dueling rules: no starting items, except repels which have no
// effect.

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
include '/conf/svs-league/burst.groovy'
include '/conf/svs-league/repel.groovy'
include '/conf/svs-league/prizeweights.groovy'

// Dueling item baseline — 255 repels (max possible), nothing else.
shipSections('Warbird', 'Javelin', 'Spider', 'Leviathan',
             'Terrier', 'Weasel', 'Lancaster', 'Shark') {
    BurstMax 0
    RepelMax 255
    DecoyMax 0
    ThorMax 0
    BrickMax 0
    RocketMax 0
    PortalMax 0
    InitialBurst 0
    InitialRepel 255
    InitialDecoy 0
    InitialThor 0
    InitialBrick 0
    InitialRocket 0
    InitialPortal 0
}

// Repels don't actually have any effect.
section('Repel') {
    RepelSpeed 0
    RepelTime 0
    RepelDistance 0
}

section('Prize') {
    UseDeathPrizeWeights 1
}

// Death prizes always have no prize.
section('DPrizeWeight') {
    NullPrize 1
    QuickCharge 0
    Energy 0
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
    Recharge 0
    MultiFire 0
    Proximity 0
    Glue 0
    AllWeapons 0
    Shields 0
    Shrapnel 0
    Repel 0
    Burst 0
    Decoy 0
    Thor 0
    Portal 0
    Brick 0
    Rocket 0
    MultiPrize 0
}
