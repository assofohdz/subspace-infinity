// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// KOTH — the F5 third consumer per .scratch/arena-modules/PRD.md.
// Validates the two-role winCondition design (LastCrownStandingWinCondition is
// both terminator + decider) and the layered-scoring chain (kill-points +
// crown-kill-bonus).
//
// Module shape:
//   - teamSetup 'ffa-private-freqs' — every player gets a unique freq.
//   - roster 'all-ships' — every ship type allowed.
//   - respawnPolicy 'lockout-no-crown-respawn' — pending respawns gated until
//     at least one CrownHolder exists in the arena; round-end force-drains.
//   - spawnPlacement 'random-radius', center: [512, 512], radius: 50.
//   - mechanic 'crowns' — distributes 1 crown per player at onRoundStart;
//     transfers on attributed kill; despawns at onRoundEnd.
//   - scoring 'kill-points', perKill: 100 — baseline kill points.
//   - scoring 'crown-kill-bonus', perCrownKill: 50 — extra perCrownKill × N
//     when killer holds N crowns. Layered ScoringModule chain.
//   - roundStructure 'crown-reset', minutes: 5 — timer terminator with KOTH-
//     themed chat ("N minutes until coronation"). Pairs with the
//     LastCrownStanding winCondition; round ends whichever fires first.
//   - matchStructure 'continuous' — rounds iterate forever.
//   - winCondition 'last-crown-standing' — two-role: terminator when crown
//     count drops to 1; decider returns the survivor's freq.
//   - winCondition 'most-crowns' — decider fallback if the timer fires first
//     (more than one player still holds a crown).
//   - shop 'flat-shop' — placeholder shop.
//
// Smoke recipe (PRD F5 acceptance):
//   1. zone.groovy autoLoads koth + sets enterSpawn 'koth'.
//   2. Connect → spawn in koth → receive 1 crown at round-start (server log:
//      "Crowns: distributed N crowns at round 1 for arena koth").
//   3. Kill another player → their crown transfers → you now have 2.
//   4. Get killed → lockout-no-crown gate: if any crowns remain, you respawn;
//      if none, you stay ghost until round-end.
//   5a. Last-crown-standing: when only one holder remains → round ends,
//      that freq wins.
//   5b. Timer fallback: 5 minutes elapse with >1 holders → most-crowns wins
//      whichever freq has the highest total.

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
    includeFragment '/conf/testconf/bot-tuning.groovy'
    wallFriction 0.0
    // Every player on own freq via ffa-private-freqs — friendlyFire 2 keeps
    // canon "anything damages anyone" so single-arena testing works.
    friendlyFire 2

    // F5 composition — KOTH end-to-end.
    //
    // mechanic 'fill-up-x-teams', teams: N keeps freqs 0..N-1 populated with one
    // Javelin bot each. ffa-private-freqs claims lowest-free freq, so the player
    // joins above the bot range (teams:1 → bot on freq 0, player on freq 1; teams:2
    // → bots on 0+1, player on freq 2; etc.). Bots inherit the round's crown
    // distribution (Crowns mechanic filters on ShipType, not PlayerShip) so the
    // player has someone to kill for crown transfer + last-crown-standing can converge.
    //
    // teams:1 chosen for bot-AI iteration — exactly one opponent bot. Bump back
    // up when KOTH testing wants a busier arena.
    //
    // Round at 1 minute (rather than the default 5) keeps the smoke cycle
    // fast — wait ~60s for round-end → onRoundStart redistributes crowns to
    // all ships present at that moment (player + bots).
    teamSetup      'ffa-private-freqs'
    roster         'all-ships'
    respawnPolicy  'lockout-no-crown-respawn'
    spawnPlacement 'random-radius',    center: [830, 130], radius: 5
    mechanic       'crowns'
    mechanic       'fill-up-x-teams',  teams: 2
    scoring        'kill-points',      perKill: 100
    scoring        'crown-kill-bonus', perCrownKill: 50
    roundStructure 'crown-reset',      minutes: 1
    matchStructure 'continuous'
    winCondition   'last-crown-standing'
    winCondition   'most-crowns'
    shop           'flat-shop'

    // Per-arena bot roster (ADR-0010/0014): 2 Warbirds + 1 Shark + 1 Leviathan instead of the
    // old uniform fill. Roster size (4) drives the spawn count; the teams: 2 above is overridden.
    // Behaviour weights are capability-derived; the leviathan nudges hold-position for the king tile.
    bots {
        ship 'warbird',   count: 2
        ship 'shark',     count: 1
        ship 'leviathan', count: 1, tweak: [['hold-position', '*', 1.3]]
    }
}
