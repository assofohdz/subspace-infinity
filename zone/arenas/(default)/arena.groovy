// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// (default) arena — the boot arena and the fallback when an arena is
// loaded by name with no folder of its own. Wired to the testconf
// preset so launching the game lands you in a fully-tuned smoke-test
// arena with every authored mechanic available end-to-end.
//
// Going-forward policy: this file MIRRORS `zone/arenas/testarena/
// arena.groovy` exactly (the canonical "everything live" showcase),
// differing only in the `map` directive. When testarena gets a new
// fragment include or spawner block, mirror the change here in the
// same commit.
//
// Read by GroovyArenaLoader.

arena {
    map '(default).lvl'
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
    // Slice 9a — exercise bomb splash friendly-fire so smoke testers can see
    // the AoE path (bombs damage teammates within blast radius; bullets,
    // burst, mines still pass through teammates safely).
    friendlyFire 1
    spawners {
        // Slice 8d C3 opt-in: Centre spawner exercises additive count +
        // radius scaling and burst regen. With 4 active players,
        // effectiveMax = 5 + 2×4 = 13 prizes; effectiveRadius = 100 + 20×4
        // = 180.
        spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 0,
              countPerPlayer: 2, radiusPerPlayer: 20, regenBatch: 3
        // Slice 8d C3 opt-in: NE corner spawner exercises hidden mode —
        // server-owned prize, client renders nothing. Pickup still works on
        // collision (server-side).
        spawn x: 900, z: 100, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 0,
              weights: [Bomb: 100, Gun: 100, Rotation: 50],
              hidden: true
    }
}
