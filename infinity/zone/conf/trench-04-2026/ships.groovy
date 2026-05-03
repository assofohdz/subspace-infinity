// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

// Per-arena ship config.
//
// GroovyShipLoader evaluates this file at arena load and installs the result
// as a ConfigRegistry snapshot via ConfigRegistrySystem.replace(). Stats use
// integer Subspace units (matches ship-<name> INI fragments); the ShipSpawn
// system converts to ECS-component units (e.g. rad/sec for Rotation) at spawn.
//
// DSL:
//   ship(Ship.WARBIRD) {
//       rotation initial: <I>, max: <M>, upgrade: <U>
//       thrust   initial: <I>, max: <M>, upgrade: <U>
//       speed    initial: <I>, max: <M>, upgrade: <U>
//       recharge initial: <I>, max: <M>, upgrade: <U>
//       energy   initial: <I>, max: <M>, upgrade: <U>
//       dragFactor          <D>     // 0..1 — fraction of Thrust applied as drag while coasting
//       turnResponsiveness  <R>     // 1/sec — angular ease rate; 8.0 ≈ ~95% of target in ~0.4 sec
//       bounceRestitution   <B>     // 0..1 — wall-bounce energy retention; 1.0 = perfectly elastic
//       radarRange          <RR>    // world units — radar viewport visible radius around the ship
//       bombs   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 1100, fireDelay: 75
//       guns    start: GunLevel.LEVEL_1, max: GunLevel.LEVEL_4, cost: 300,  fireDelay: 60
//       mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 800,  fireDelay: 20
//       bursts  start: <I>, max: <M>
//       thors   start: <I>, max: <M>, fireDelay: 1000
//       repels  start: <I>, max: <M>
//       decoys  start: <I>, max: <M>
//       bricks  start: <I>, max: <M>
//       rockets start: <I>, max: <M>, activeTimeCs: <T>   // T = per-ship Subspace [Ship] RocketTime (cs)
//       portals start: <I>, max: <M>
//   }
//
// Omit a stat to leave it at ShipStat(0, 0, 0). Omit an inventory block to
// disallow that item (= null in ShipConfig; per-ship *Max component not
// projected; prize applier no-ops on pickup). Per the B2 grilled-through plan
// (.scratch/settings-pipeline-slices.md), authored Subspace `*Max 0`
// values were translated to "omit block" during the B2-Migration commit.

// Testbed override: Warbird intentionally has non-zero upgrade values so
// prize-driven progression (Rotation / Thruster / Recharge / Energy / TopSpeed)
// is observable in trench. The other 7 ships keep the no-upgrade design.
ship(Ship.WARBIRD) {
    rotation initial: 200,  max: 300,  upgrade: 20
    thrust   initial: 16,   max: 24,   upgrade: 2
    speed    initial: 2000, max: 6000, upgrade: 200
    recharge initial: 4000, max: 8000, upgrade: 200
    energy   initial: 1500, max: 3000, upgrade: 100
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    guns    start: GunLevel.LEVEL_3, max: GunLevel.LEVEL_3, cost: 450, fireDelay: 100
    thors   start: 0, max: 3, fireDelay: 1000
    decoys  start: 0, max: 1
}

ship(Ship.JAVELIN) {
    rotation initial: 200,  max: 200,  upgrade: 0
    thrust   initial: 13,   max: 24,   upgrade: 0
    speed    initial: 1900, max: 6000, upgrade: 0
    recharge initial: 1500, max: 1500, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs   start: BombLevel.BOMB_1, max: BombLevel.BOMB_1, cost: 1100, fireDelay: 75
    guns    start: GunLevel.LEVEL_1, max: GunLevel.LEVEL_1, cost: 300,  fireDelay: 60
    thors   start: 0, max: 3, fireDelay: 1000
    decoys  start: 0, max: 1
    rockets start: 1, max: 3, activeTimeCs: 400
}

ship(Ship.SPIDER) {
    rotation initial: 180,  max: 180,  upgrade: 0
    thrust   initial: 18,   max: 24,   upgrade: 0
    speed    initial: 1700, max: 6000, upgrade: 0
    recharge initial: 2500, max: 2500, upgrade: 0
    energy   initial: 1400, max: 1400, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    guns    start: GunLevel.LEVEL_1, max: GunLevel.LEVEL_1, cost: 225, fireDelay: 35
    thors   start: 0, max: 3, fireDelay: 1000
    decoys  start: 0, max: 1
}

ship(Ship.LEVIATHAN) {
    rotation initial: 45,   max: 800,   upgrade: 0
    thrust   initial: 5,    max: 24,    upgrade: 0
    speed    initial: 500,  max: 6000,  upgrade: 0
    recharge initial: 1100, max: 32000, upgrade: 0
    energy   initial: 1500, max: 1500,  upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs   start: BombLevel.BOMB_3, max: BombLevel.BOMB_3, cost: 1100, fireDelay: 75
    guns    start: GunLevel.LEVEL_2, max: GunLevel.LEVEL_2, cost: 400,  fireDelay: 60
    // mines uses default BombLevel start/max; per-ship MaxMines (count cap)
    // not yet represented in typed pipeline — tracked in ship-<name>.groovy.
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 800, fireDelay: 20
    repels  start: 4, max: 4
    decoys  start: 0, max: 3
    portals start: 1, max: 1
}

ship(Ship.TERRIER) {
    rotation initial: 300,  max: 300,  upgrade: 0
    thrust   initial: 24,   max: 28,   upgrade: 0
    speed    initial: 4000, max: 6000, upgrade: 0
    recharge initial: 1800, max: 1800, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    guns    start: GunLevel.LEVEL_1, max: GunLevel.LEVEL_1, cost: 400, fireDelay: 75
    bursts  start: 1, max: 1
    thors   start: 0, max: 3, fireDelay: 1000
    decoys  start: 0, max: 1
    portals start: 1, max: 1
}

ship(Ship.WEASEL) {
    rotation initial: 130,  max: 130,  upgrade: 0
    thrust   initial: 18,   max: 24,   upgrade: 0
    speed    initial: 1100, max: 6000, upgrade: 0
    recharge initial: 1250, max: 1250, upgrade: 0
    energy   initial: 1020, max: 1020, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    guns    start: GunLevel.LEVEL_3, max: GunLevel.LEVEL_3, cost: 208, fireDelay: 40
    thors   start: 0, max: 3, fireDelay: 1000
    repels  start: 0, max: 1
    decoys  start: 0, max: 1
    bricks  start: 0, max: 2
    rockets start: 0, max: 1, activeTimeCs: 80
}

ship(Ship.LANCASTER) {
    rotation initial: 200,  max: 200,  upgrade: 0
    thrust   initial: 16,   max: 24,   upgrade: 0
    speed    initial: 1800, max: 6000, upgrade: 0
    recharge initial: 2750, max: 2750, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    guns    start: GunLevel.LEVEL_2, max: GunLevel.LEVEL_2, cost: 300, fireDelay: 50
    thors   start: 0, max: 3, fireDelay: 1000
    decoys  start: 0, max: 1
}

ship(Ship.SHARK) {
    rotation initial: 210,  max: 210,  upgrade: 0
    thrust   initial: 13,   max: 24,   upgrade: 0
    speed    initial: 1875, max: 6000, upgrade: 0
    recharge initial: 1500, max: 1500, upgrade: 0
    energy   initial: 1200, max: 1200, upgrade: 0
    dragFactor          0.05
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs   start: BombLevel.BOMB_1, max: BombLevel.BOMB_1, cost: 1150, fireDelay: 45
    // shark has no guns (MaxGuns 0 in trench/ship-shark.groovy)
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 200, fireDelay: 10
    thors   start: 0, max: 3, fireDelay: 1000
    repels  start: 3, max: 3
    decoys  start: 0, max: 1
}
