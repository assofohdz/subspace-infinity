// Zone-wide server config. Read once at server startup by GroovyZoneLoader.
//
//   autoLoad   — folder names under zone/arenas/ to bring up on first tick.
//                ArenaSystem reconciler loads them; ~loadArena / ~unloadMap
//                mutate the same desired-state at runtime.
//   enterSpawn — name of the loaded arena whose [Spawn] X/Z (arena-local) is
//                used as the world-space spawn for new player sessions on
//                first connect. Must be one of the arenas in autoLoad above
//                (or otherwise loaded by the time a session connects). The
//                world coord is computed by
//                ArenaSystem.getArenaSpawn(arenaName) =
//                    ArenaMap.min + (X, Z) on GAMEPLAY_Y.
//
// Per-arena [Spawn] in arena.conf is also used for in-arena respawn (ship
// change, death) — keyed off the ship's *current* ArenaId, which doesn't exist
// at connect time. Connect-time uses this single zone-level pointer to pick
// *which* arena's [Spawn] to read.

zone {
    autoLoad 'trench', 'deva', 'testarena'
    enterSpawn 'testarena'
    // scriptPollInterval 5.0   // seconds; throttle for the per-arena ships.groovy
                                // dev-mode reload watcher. 5 s is the documented
                                // default; uncomment + tune to taste.
    repelFriendlies true        // Slice S5 — when true (default), repels push
                                // every Repellable body in radius regardless of
                                // team (Subspace canon). Set false to skip
                                // same-frequency ships; bombs / mines always
                                // pushed. Infinity-specific ops knob.
}
