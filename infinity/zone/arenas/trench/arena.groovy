// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

arena {
    map '04-2026-trench/pub2025.lvl'
    shipsScript '/conf/trench-04-2026/ships.groovy'
    spawn 1000, 20
    includeFragment '/conf/trench-04-2026/prizeweights.groovy'
    includeFragment '/conf/trench-04-2026/ship-warbird.groovy'
    includeFragment '/conf/trench-04-2026/ship-javelin.groovy'
    includeFragment '/conf/trench-04-2026/ship-spider.groovy'
    includeFragment '/conf/trench-04-2026/ship-leviathan.groovy'
    includeFragment '/conf/trench-04-2026/ship-terrier.groovy'
    includeFragment '/conf/trench-04-2026/ship-weasel.groovy'
    includeFragment '/conf/trench-04-2026/ship-lancaster.groovy'
    includeFragment '/conf/trench-04-2026/ship-shark.groovy'
    includeFragment '/conf/trench-04-2026/misc.groovy'
    wallFriction 0.1
}
