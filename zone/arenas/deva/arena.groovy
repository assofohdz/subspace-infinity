// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

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

    // ADR-0008 module DSL — minimum surface so AvatarSystem can gate ship-change
    // and arena can resolve spawn positions. Coords lifted 1:1 from the deleted
    // /conf/deva-04-2026/spawn.groovy fragment.
    roster         'all-ships'
    spawnPlacement 'random-radius', center: [20, 20], radius: 0
}
