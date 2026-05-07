// Test override fragment for trench's Warbird. Loaded last in trench/arena.groovy
// so its keys win the last-wins merge in SettingsSystem.loadFragments.
//
// Purpose: turn trench into a prize / weapon testbed without touching the
// canonical SVS-tuned ship-warbird.groovy or the no-upgrades trench
// ships.groovy. Enables bullets, bombs, mines, bursts, decoys, thors, and bumps
// the relevant prize weights so the spawners in trench/arena.groovy
// actually drop weapon prizes the ship can use.

section('Warbird') {
    // Weapons inventory caps — was 0 in the canonical fragment, so the ship
    // could never carry these. Bump to non-zero so prize pickups land.
    MaxMines 4
}

// Bump weights for the weapon / movement prizes the testbed should produce.
// Other entries inherit from the base prizeweights.groovy via the merge.
section('PrizeWeight') {
    Recharge 50
    Energy 50
    Rotation 50
    Stealth 25
    Cloak 25
    XRadar 50
    Warp 25
    Gun 100
    Bomb 100
    BouncingBullets 25
    Thruster 50
    TopSpeed 50
    AllWeapons 25
    Shields 25
    Shrapnel 25
    AntiWarp 25
    QuickCharge 50
    MultiPrize 10
    Brick 25
    Rocket 25
    Portal 25
    Glue 25
    Decoy 25
    Thor 25
    Burst 50
    Repel 50
    MultiFire 25
    Proximity 25
}
