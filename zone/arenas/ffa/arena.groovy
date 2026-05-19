// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// FFA-Deathmatch — the F2 capstone arena per .scratch/arena-modules/PRD.md.
// Composes the entire F2 module catalog into a standalone playable arena.
//
// Distinct from `test-modules`: that arena is the going-forward smoke testbed
// (includes the test-only warbird override for prize-weight tuning); this one
// is the "real" FFA composition without testbed scaffolding.
//
// Module shape:
//   - teamSetup 'ffa-private-freqs' — every player ship gets its own freq
//     (lazy per-player TeamEntity). Means every other ship is an opponent.
//   - roster 'all-ships' — every ship type is allowed.
//   - respawnPolicy 'cooldown-respawn', seconds: 3 — short ghost window after
//     death; the durable player entity rebinds CurrentShip when the new ship
//     spawns.
//   - spawnPlacement 'random-radius', center: [512, 512], radius: 50 — uniform
//     in a 50-tile disc around arena center.
//   - scoring 'kill-points', perKill: 100 — emits PlayerScoreChange each kill;
//     ScoreCoordinatorSystem drains to PlayerRound/Match/TotalScore.
//   - roundStructure 'timed-round', minutes: 10 — 10-minute rounds.
//   - matchStructure 'continuous' — rounds iterate forever (no match-end).
//   - winCondition 'first-to-x', target: 1000 — first to 10 kills wins.
//   - winCondition 'highest-score' — fallback when timer fires first.
//   - shop 'flat-shop' — placeholder shop (no buy commands yet; future slice).
//
// Smoke recipe (PRD acceptance):
//   1. ./gradlew :infinity-client:runX11
//   2. ~loadArena ffa (or walk in from another arena's bounds).
//   3. Confirm kills award +100 to PlayerRoundScore + MatchScore + TotalScore.
//   4. Either reach 1000 (first-to-x) or wait 10 minutes (highest-score) for
//      round-end. Verify ScoreReset(ROUND) zeroes PlayerRoundScore and a new
//      round starts.

arena {
    map '04-2026-trench/pub2025.lvl'
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
    wallFriction 0.1
    // FFA semantics: every player is on a unique freq via ffa-private-freqs,
    // so every other ship is an opponent regardless of friendlyFire setting.
    // Mode 2 (all weapons hit) keeps the canon "anything can damage anyone" rule.
    friendlyFire 2

    // F2 capstone — arena-modules end-to-end.
    teamSetup      'ffa-private-freqs'
    roster         'all-ships'
    respawnPolicy  'cooldown-respawn', seconds: 3
    spawnPlacement 'random-radius',   center: [512, 512], radius: 50
    scoring        'kill-points',     perKill: 100
    roundStructure 'timed-round',     minutes: 10
    matchStructure 'continuous'
    winCondition   'first-to-x',      target:  1000
    winCondition   'highest-score'
    shop           'flat-shop'
}
