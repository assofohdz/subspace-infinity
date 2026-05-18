// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// test-modules — exercises the ADR-0008 arena-modules framework
// end-to-end. Starts as a thin clone of testarena (same testconf
// preset, same map, same fragment includes) plus the new module DSL
// statements being validated by each F2 sub-slice.
//
// Going-forward policy: as each F2.x slice lands its first concrete
// module, add the corresponding DSL statement here so the smoke arena
// always exercises the latest module surface end-to-end.
//
// Current modules under test:
//   F2a — scoring 'kill-points', perKill: 100
//         KillPointsScoring listens to PlayerKilledEvent, emits a
//         PlayerScoreChange transient on each in-arena kill;
//         ScoreCoordinatorSystem drains it and writes PlayerRoundScore
//         on the killer's ship entity.
//   F2b — roundStructure 'timed-round', minutes: 5
//         TimedRoundStructure ticks each frame; once 5 minutes have
//         elapsed since onRoundStart, stamps RoundEndPending on the
//         arena entity. Dispatcher then fires onRoundEnd on every
//         module + emits ScoreReset(ROUND); next tick ScoreCoordinator
//         zeros every PlayerRoundScore in the arena; dispatcher fires
//         onRoundStart(2) and the timer re-arms.
//   F2c — winCondition 'first-to-x', target: 500
//         FirstToXWinCondition (dual-role) — per-tick scans the arena;
//         first player with PlayerRoundScore >= 500 triggers
//         RoundEndPending (terminator) AND its freq is cached + later
//         returned from declareWinner (decider).
//   F2c — winCondition 'highest-score'
//         HighestScoreWinCondition (decider-only) — fallback if the
//         timed-round elapses without anyone hitting 500: dispatcher
//         polls winConditions in order; FirstToX returns UNDECIDED;
//         HighestScore returns the top scorer's freq.
//   F2d — matchStructure 'continuous'
//         ContinuousMatchStructure — degenerate; never declares
//         match-end. Match counter stays at 1; rounds iterate forever.
//         Each PlayerScoreChange writes Round + Match + Total tiers in
//         one drain pass (ScoreCoordinatorSystem).
//   F2-bots — mechanic 'fill-up-x-teams', teams: 2
//         FillUpXTeams — per tick, for each freq in 0..teams-1, if no
//         non-Dead ship in this arena holds that freq, spawns a Javelin
//         bot via AIEntities.createMobShip and stamps that freq. Auto-
//         respawns after a bot is killed + Decay-reaped. Player on freq
//         0 (default) → bot lands on freq 1 → friendly-fire irrelevant
//         → kills count → KillPointsScoring fires.
//
// Smoke recipe:
//   1. ./gradlew :infinity-client:runX11 (or runMac)
//   2. Server autoloads this arena; client connects + spawns here
//      (enterSpawn 'test-modules' in zone.groovy). A Javelin bot is
//      waiting at arena center on freq 1.
//   3. Shoot the bot. Kill = +100 to your PlayerRoundScore (+ MatchScore
//      + TotalScore).
//   4. 5 kills = 500 pts = FirstToX terminator fires → round-end log +
//      scores reset → bot respawns on freq 1. Or wait 5 min for the
//      timer to fire → HighestScore wins.
//   5. Inspect server log for module lifecycle events; PlayerRoundScore
//      and RoundNumber on the arena entity will mutate visibly.

arena {
    map '04-2026-trench/pub2025.lvl'
    shipsScript '/conf/testconf/ships.groovy'
    includeFragment '/conf/testconf/spawn.groovy'
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
    // Testbed warbird override: enables weapons + boosts prize weights so
    // every authored prize type actually drops. Loaded last per the
    // last-wins merge in SettingsSystem.loadFragments.
    includeFragment '/conf/testconf/ship-warbird-test.groovy'
    wallFriction 0.1
    // Friendly-fire mode 2 (all weapons) so single-client kill tests
    // work — bomb yourself or shoot a teammate to fire PlayerKilledEvent.
    friendlyFire 2

    // ADR-0008 arena-module DSL — exercised end-to-end as F2 sub-slices land.
    roster         'all-ships'         // F2.5 — global ship allow gate; permits every ship
    scoring        'kill-points',     perKill: 100
    roundStructure 'timed-round',     minutes: 5
    matchStructure 'continuous'        // rounds iterate; match never ends
    winCondition   'first-to-x',      target:  500
    winCondition   'highest-score'     // fallback if timer fires first
    mechanic       'fill-up-x-teams', teams:   2
}
