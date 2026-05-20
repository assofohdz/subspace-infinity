// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Deva — Devastation pub-style deathmatch on bdegb.lvl. Composed via the
// ADR-0008 module DSL: every player on their own freq (ffa-private-freqs),
// kill-points scoring, timed 15-minute rounds, highest-score wins at the
// timer. No bots, no team mechanics — pure free-for-all.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.
// bdegb.lvl's [Spawn] region centers around (20, 20).

arena {
    map '04-2026-deva/bdegb.lvl'
    shipsScript '/conf/deva-04-2026/ships.groovy'
    includeFragment '/conf/deva-04-2026/prize-weights.groovy'
    includeFragment '/conf/deva-04-2026/ship-warbird.groovy'
    includeFragment '/conf/deva-04-2026/ship-javelin.groovy'
    includeFragment '/conf/deva-04-2026/ship-spider.groovy'
    includeFragment '/conf/deva-04-2026/ship-leviathan.groovy'
    includeFragment '/conf/deva-04-2026/ship-terrier.groovy'
    includeFragment '/conf/deva-04-2026/ship-weasel.groovy'
    includeFragment '/conf/deva-04-2026/ship-lancaster.groovy'
    includeFragment '/conf/deva-04-2026/ship-shark.groovy'
    includeFragment '/conf/deva-04-2026/misc.groovy'
    includeFragment '/conf/deva-04-2026/bullet.groovy'
    includeFragment '/conf/deva-04-2026/bomb.groovy'
    includeFragment '/conf/deva-04-2026/mine.groovy'
    includeFragment '/conf/deva-04-2026/burst.groovy'
    includeFragment '/conf/deva-04-2026/repel.groovy'
    includeFragment '/conf/deva-04-2026/rocket.groovy'
    includeFragment '/conf/deva-04-2026/brick.groovy'
    includeFragment '/conf/deva-04-2026/decoy.groovy'
    includeFragment '/conf/deva-04-2026/portal.groovy'
    includeFragment '/conf/deva-04-2026/thor.groovy'
    includeFragment '/conf/deva-04-2026/prize.groovy'
    // Slice 9a — friendly-fire policy: 0=off (default), 1=bomb splash only,
    // 2=all weapons. Deva keeps the safe default.
    friendlyFire 0

    // F2 capstone composition — same shape as ffa but with deva's tuning + map.
    teamSetup      'ffa-private-freqs'
    roster         'all-ships'
    respawnPolicy  'cooldown-respawn', seconds: 3
    spawnPlacement 'random-radius',    center: [20, 20], radius: 0
    scoring        'kill-points',      perKill: 100
    roundStructure 'timed-round',      minutes: 15
    matchStructure 'continuous'
    winCondition   'highest-score'
    shop           'flat-shop'
}
