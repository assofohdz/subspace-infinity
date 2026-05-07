// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

arena {
    map '04-2026-trench/pub2025.lvl'
    shipsScript '/conf/trench-04-2026/ships.groovy'
    includeFragment '/conf/trench-04-2026/spawn.groovy'
    includeFragment '/conf/trench-04-2026/prize-weights.groovy'
    includeFragment '/conf/trench-04-2026/ship-warbird.groovy'
    includeFragment '/conf/trench-04-2026/ship-javelin.groovy'
    includeFragment '/conf/trench-04-2026/ship-spider.groovy'
    includeFragment '/conf/trench-04-2026/ship-leviathan.groovy'
    includeFragment '/conf/trench-04-2026/ship-terrier.groovy'
    includeFragment '/conf/trench-04-2026/ship-weasel.groovy'
    includeFragment '/conf/trench-04-2026/ship-lancaster.groovy'
    includeFragment '/conf/trench-04-2026/ship-shark.groovy'
    includeFragment '/conf/trench-04-2026/misc.groovy'
    includeFragment '/conf/trench-04-2026/bullet.groovy'
    includeFragment '/conf/trench-04-2026/bomb.groovy'
    includeFragment '/conf/trench-04-2026/mine.groovy'
    includeFragment '/conf/trench-04-2026/burst.groovy'
    includeFragment '/conf/trench-04-2026/repel.groovy'
    includeFragment '/conf/trench-04-2026/rocket.groovy'
    includeFragment '/conf/trench-04-2026/brick.groovy'
    includeFragment '/conf/trench-04-2026/decoy.groovy'
    includeFragment '/conf/trench-04-2026/portal.groovy'
    includeFragment '/conf/trench-04-2026/prize.groovy'
    // Test override: enables all weapons + non-zero prize weights so trench
    // can be used as a sandbox for prize / weapon work. Loaded last so its
    // keys win the last-wins merge in SettingsSystem.loadFragments.
    includeFragment '/conf/trench-04-2026/ship-warbird-test.groovy'
    wallFriction 0.1
    // Slice 9a — friendly-fire policy for trench (frozen reference preset):
    //   0 = off (no same-team damage, default)
    //   1 = bomb splash only (bomb AoE damages teammates)
    //   2 = all weapons damage teammates
    // Trench stays at the safe default; testarena exercises mode 1.
    friendlyFire 0
    spawners {
        // Centre of the arena. No weights override → uses the typed
        // prize-weights.groovy via ConfigRegistry.prizeWeights().
        spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 10000
        // North-east corner — arena-local (0, 0) is NW; NE is (1024, 0). Inset
        // by the spawn radius so the whole spawn disc stays inside the arena.
        // Per-spawner override: only ever drops bombs, bullets, and rotation —
        // a "weapon-focused" corner for testing the override path.
        spawn x: 900, z: 100, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 10000,
              weights: [Bomb: 100, Gun: 100, Rotation: 50]
    }
}
