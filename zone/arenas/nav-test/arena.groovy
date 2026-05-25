// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// nav-test — flow-field navigation smoke arena (bot-AI v2 slice #03). baseelim.lvl has good
// corridors, so a bot whose target sits behind a wall should round the wall via the flow-field
// gradient (HUD: nav:flow, branch Approach) instead of pressing straight into it. Open line-of-
// sight falls back to straight-line Pursue (nav:reactive). A spread spawn drops the hulls into
// different corridor segments so they actually have to path to reach each other.
//
// Load at runtime:  ~loadArena nav-test
//
// Note: bots still over-steer (momentum vs turn-rate damping, OVERSTEER_THRUST_FLOOR) — that's an
// orthogonal locomotion issue, not the navigation heading; watch the route, not the wobble.

arena {
    map 'baseelim.lvl'
    shipsScript '/conf/testconf/ships.groovy'
    includeFragment '/conf/testconf/prize-weights.groovy'
    includeFragment '/conf/testconf/ship-warbird.groovy'
    includeFragment '/conf/testconf/ship-javelin.groovy'
    includeFragment '/conf/testconf/ship-spider.groovy'
    includeFragment '/conf/testconf/ship-leviathan.groovy'
    includeFragment '/conf/testconf/ship-terrier.groovy'
    includeFragment '/conf/testconf/ship-weasel.groovy'
    includeFragment '/conf/testconf/ship-lancaster.groovy'
    includeFragment '/conf/testconf/ship-shark.groovy'
    includeFragment '/conf/testconf/misc.groovy'
    includeFragment '/conf/testconf/bullet.groovy'
    includeFragment '/conf/testconf/bomb.groovy'
    includeFragment '/conf/testconf/mine.groovy'
    includeFragment '/conf/testconf/burst.groovy'
    includeFragment '/conf/testconf/repel.groovy'
    includeFragment '/conf/testconf/rocket.groovy'
    includeFragment '/conf/testconf/brick.groovy'
    includeFragment '/conf/testconf/decoy.groovy'
    includeFragment '/conf/testconf/portal.groovy'
    includeFragment '/conf/testconf/prize.groovy'
    includeFragment '/conf/testconf/ship-warbird-test.groovy'
    wallFriction 0.1
    friendlyFire 2

    teamSetup      'ffa-private-freqs'
    roster         'all-ships'
    respawnPolicy  'cooldown-respawn', seconds: 5
    // Spread the bots across corridor segments around (512, 115) so targets land behind walls.
    spawnPlacement 'random-radius',    center: [512, 115], radius: 40

    // Small mixed roster — fast (Warbird/Spider) vs slow (Leviathan) so capability divergence is
    // visible alongside the pathing. 4 bots keeps each one easy to watch.
    mechanic       'fill-up-x-teams', teams: 4
    bots {
        ship 'warbird', count: 2
        ship 'spider',  count: 1
        ship 'leviathan', count: 1
    }
}
