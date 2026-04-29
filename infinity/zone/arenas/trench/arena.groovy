// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig. The included preset fragment is still
// INI today (out of scope for the zone-arena-to-groovy migration) — it is
// loaded through the existing IniLoader / #include preprocessor.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

arena {
    map '04-2026-trench/pub2025.lvl'
    shipsScript '/conf/trench-04-2026/ships.groovy'
    spawn 1000, 20
    includeFragment '/conf/trench-04-2026/trench.conf'
}
