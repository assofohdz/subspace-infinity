// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// ffa-bots — bot-AI v2 smoke arena. FFA (every ship its own freq), with the
// fill-up-x-teams mechanic spawning one bot on each of freqs 0..7. Paired with
// the interim per-freq hull mapping in FillUpXTeams.spawnBot, this puts one of
// each of the eight hulls on the map at once so capability-derived behaviour
// divergence (slice #05) is observable with no per-bot authoring:
//   freq 0 Warbird · 1 Javelin · 2 Spider · 3 Leviathan · 4 Terrier ·
//   5 Weasel · 6 Lancaster · 7 Shark
//
// Watch BotDebug.branch per bot: high-mobility / high-sustained hulls (Warbird,
// Spider) should sit in Engage; slow hulls (Leviathan, Lancaster) flip to
// Evade/Wander more as energy drains. Cadence ~150ms (zone-bot-ai.groovy).
//
// NOTE: the per-freq hull mapping is interim scaffolding — the real per-arena
// `bots { }` authoring is bot-ai-v2 slice #01. A human who spawns a ship takes
// one freq, displacing that freq's bot; spectate to see all eight.
//
// Load at runtime:  ~loadArena ffa-bots

arena {
    map 'elim.lvl'
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

    // FFA: each ship gets its own freq + TeamEntity, so all eight bots are
    // mutual enemies and actually engage.
    teamSetup      'ffa-private-freqs'
    roster         'all-ships'
    respawnPolicy  'cooldown-respawn', seconds: 5
    // Wider radius than the kill-test arenas so eight bots spread out instead
    // of stacking at one point.
    spawnPlacement 'random-radius',    center: [830, 150], radius: 15
    mechanic       'fill-up-x-teams', teams:   8
}
