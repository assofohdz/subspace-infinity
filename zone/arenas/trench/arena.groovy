// Per-arena server config. Read at arena-load time by GroovyArenaLoader,
// which produces a typed ArenaConfig.
//
// Spawn coords are arena-local: (0, 0) = NW corner, (1024, 1024) = SE.

arena {
    map '04-2026-trench/pub2025.lvl'
    shipsScript '/conf/trench-04-2026/ships.groovy'
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
    includeFragment '/conf/trench-04-2026/thor.groovy'
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

    // ADR-0008 module DSL — F4 turf-shaped composition.
    //   - teamSetup 'two-fixed-teams' — eager 2-team setup (freq 0 + 1), balanced joins.
    //   - roster 'all-ships' — global allow gate.
    //   - respawnPolicy 'instant-respawn' — fresh ship immediately after death.
    //   - spawnPlacement 'random-radius', center: [512, 512], radius: 0 — exact-point spawn at center.
    //     (Per-team centers come with multi-spawn-zone work later.)
    //   - scoring 'kill-points' + 'flag-hold-time' — layered: kills + per-second flag occupancy.
    //   - roundStructure 'timed-round', minutes: 10 — 10-minute rounds.
    //   - matchStructure 'continuous' — rounds iterate forever.
    //   - winCondition 'most-flag-occupancy' — first non-UNDECIDED decider; reads TeamFlagHoldTicks.
    //   - winCondition 'highest-score' — fallback when no team has occupancy yet.
    //
    // Flag entities still come from the map's flag tiles via MapFactory.createTurfStationaryFlag
    // (LegacyMapProjector) — no `mechanic 'static-flag'` needed because FlagSystem is an
    // always-loaded server system that handles flag-touch contacts on every arena.
    teamSetup      'two-fixed-teams'
    // Bounded team-setup → fill-up targets `fixedTeamCount(2) × capacity(2)` = 4 live ships.
    // Bots fill seats humans don't and are culled as players join; the team setup assigns
    // each bot's freq (least-full team), so the bots{} roster below is just the hull pool.
    mechanic       'fill-up-x-teams', capacity: 2
    // Turf objective (ADR-0015): exposes the map's stationary flag(s) as bot static nav goals so
    // tanky hulls hold the flag (hold-position behaviour), not just drift via traffic heat.
    mechanic       'turf'
    roster         'all-ships'
    respawnPolicy  'instant-respawn'
    // radius 0 spawned every bot on one point → they stacked + wedged in the corner and never
    // reached the flag (bot-AI smoke 2026-05-26). A small disc spreads the seats so they separate.
    spawnPlacement 'random-radius',    center: [512, 512], radius: 32
    scoring        'kill-points',      perKill: 100
    scoring        'flag-hold-time',   perSecondPerFlag: 5
    roundStructure 'timed-round',      minutes: 10
    matchStructure 'continuous'
    winCondition   'most-flag-occupancy'
    winCondition   'highest-score'

    // Bot-AI #07 trench-goal test: two-fixed-teams + the map's stationary turf flag give bots a real
    // objective. follow-traffic drifts them toward the contested flag/chokepoints; engage takes over
    // in close combat. Hull pool (cycled by spawn order — counts weight the mix): with capacity 2 ×
    // 2 teams the four seats draw warbird, warbird, spider, leviathan, so capability divergence
    // (fast Warbird vs slow Leviathan) shows in the routing across both teams.
    bots {
        ship 'warbird',   count: 2
        ship 'spider',    count: 1
        ship 'leviathan', count: 1
    }

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
