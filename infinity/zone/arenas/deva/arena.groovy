// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

arena {
    map '04-2026-deva/bdegb.lvl'
    shipsScript '/conf/deva-04-2026/ships.groovy'
    includeFragment '/conf/deva-04-2026/spawn.groovy'
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
    includeFragment '/conf/deva-04-2026/prize.groovy'
}
