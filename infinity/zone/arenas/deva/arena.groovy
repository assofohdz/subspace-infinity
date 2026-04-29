// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig. The included preset fragment is still
// INI today (out of scope for the zone-arena-to-groovy migration) — it is
// loaded through the existing IniLoader / #include preprocessor.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

arena {
    map '04-2026-deva/bdegb.lvl'
    shipsScript '/conf/deva-04-2026/ships.groovy'
    spawn 20, 20
    includeFragment '/conf/deva-04-2026/deva-04-2026.conf'
}
