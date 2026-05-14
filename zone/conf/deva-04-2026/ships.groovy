// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

// Per-arena ship config.
//
// GroovyShipLoader evaluates this file at arena load and installs the result
// as a ConfigRegistry snapshot via ConfigRegistrySystem.replace(). Stats use
// integer Subspace units (matches ship-<name> INI fragments); the ShipSpawn
// system converts to ECS-component units (e.g. rad/sec for Rotation) at spawn.
//
// Inventory blocks (B2-Migration): each block declares an inventory item the
// ship can carry. Omit the block to disallow that item entirely (= null in
// ShipConfig; per-ship *Max component not projected; prize applier no-ops on
// pickup). Per Q6 of the B2 grilled-through plan
// (.scratch/settings-pipeline-slices.md), authored Subspace `*Max 0` values
// translate to "omit block."
//
// Status-family blocks (cloak, stealth) follow the inventory-block
// convention: omit when CloakStatus / StealthStatus is 0 (= forbidden).
// Author with `status: 1` (acquirable via prize) or `status: 2` (starts
// active at spawn). Energy is the Subspace 1000ths-per-centisecond drain
// rate (REFERENCE.md "Ship abilities").
//
// All 8 ships are configured. Numeric stats mirror the per-ship `ship-<name>`
// INI fragments in this directory (the canonical deva tuning).

ship(Ship.WARBIRD) {
    rotation initial: 200,  max: 200,  upgrade: 0
    thrust   initial: 16,   max: 24,   upgrade: 0
    speed    initial: 2000, max: 6000, upgrade: 0
    recharge initial: 4000, max: 4000, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_3, max: BombLevel.BOMB_3, cost: 325, fireDelay: 175, speed: 4300, thrust: 400
    gravBombs start: 2, max: 5, fireDelay: 200
    bullets start: BulletLevel.LEVEL_2, max: BulletLevel.LEVEL_3, cost: 28,  fireDelay: 6, speed: 4100
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 460, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 2
    decoys  start: 1, max: 2
    stealth status: 1, energy: 0
    xradar  status: 2, energy: 0
}

ship(Ship.JAVELIN) {
    rotation initial: 200,  max: 200,  upgrade: 0
    thrust   initial: 13,   max: 24,   upgrade: 0
    speed    initial: 1900, max: 6000, upgrade: 0
    recharge initial: 1500, max: 1500, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_3, max: BombLevel.BOMB_3, cost: 510, fireDelay: 25, speed: 4300, thrust: 400
    gravBombs start: 3, max: 8, fireDelay: 200
    bullets start: BulletLevel.LEVEL_3, max: BulletLevel.LEVEL_3, cost: 27,  fireDelay: 6, speed: 3300
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 500, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 2
    decoys  start: 1, max: 3
    xradar  status: 1, energy: 0
}

ship(Ship.SPIDER) {
    rotation initial: 180,  max: 180,  upgrade: 0
    thrust   initial: 18,   max: 24,   upgrade: 0
    speed    initial: 1700, max: 6000, upgrade: 0
    recharge initial: 2500, max: 2500, upgrade: 0
    energy   initial: 1400, max: 1400, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_2, max: BombLevel.BOMB_3, cost: 300, fireDelay: 200, speed: 4300, thrust: 400
    gravBombs start: 1, max: 3, fireDelay: 200
    bullets start: BulletLevel.LEVEL_2, max: BulletLevel.LEVEL_3, cost: 25,  fireDelay: 8, speed: 4100
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 500, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 2
    decoys  start: 0, max: 2
    cloak   status: 1, energy: 900
    stealth status: 1, energy: 1
    xradar  status: 1, energy: 0
}

ship(Ship.LEVIATHAN) {
    rotation initial: 45,   max: 800,   upgrade: 0
    thrust   initial: 5,    max: 24,    upgrade: 0
    speed    initial: 500,  max: 6000,  upgrade: 0
    recharge initial: 1100, max: 32000, upgrade: 0
    energy   initial: 1500, max: 1500,  upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_2, max: BombLevel.BOMB_3, cost: 600, fireDelay: 45, speed: 5000, thrust: 400
    gravBombs start: 5, max: 10, fireDelay: 200
    bullets start: BulletLevel.LEVEL_3, max: BulletLevel.LEVEL_3, cost: 30,  fireDelay: 6, speed: 4000
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 625, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 3
    decoys  start: 0, max: 3
    stealth status: 1, energy: 1
    xradar  status: 1, energy: 0
}

ship(Ship.TERRIER) {
    rotation initial: 300,  max: 300,  upgrade: 0
    thrust   initial: 24,   max: 28,   upgrade: 0
    speed    initial: 4000, max: 6000, upgrade: 0
    recharge initial: 1800, max: 1800, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_2, max: BombLevel.BOMB_3, cost: 600, fireDelay: 207, speed: 4300, thrust: 400
    gravBombs start: 1, max: 3, fireDelay: 200
    bullets start: BulletLevel.LEVEL_2, max: BulletLevel.LEVEL_3, cost: 30,  fireDelay: 10, speed: 3200
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 400, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 2
    decoys  start: 0, max: 2
    stealth status: 1, energy: 0
    xradar  status: 2, energy: 0
}

ship(Ship.WEASEL) {
    rotation initial: 130,  max: 130,  upgrade: 0
    thrust   initial: 18,   max: 24,   upgrade: 0
    speed    initial: 1100, max: 6000, upgrade: 0
    recharge initial: 1250, max: 1250, upgrade: 0
    energy   initial: 1020, max: 1020, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_3, max: BombLevel.BOMB_3, cost: 400, fireDelay: 175, speed: 4850, thrust: 400
    gravBombs start: 1, max: 3, fireDelay: 200
    bullets start: BulletLevel.LEVEL_2, max: BulletLevel.LEVEL_3, cost: 27,  fireDelay: 5, speed: 3600
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 250, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 1
    decoys  start: 0, max: 2
    stealth status: 1, energy: 0
    xradar  status: 1, energy: 0
}

ship(Ship.LANCASTER) {
    rotation initial: 200,  max: 200,  upgrade: 0
    thrust   initial: 16,   max: 24,   upgrade: 0
    speed    initial: 1800, max: 6000, upgrade: 0
    recharge initial: 2750, max: 2750, upgrade: 0
    energy   initial: 1500, max: 1500, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_3, max: BombLevel.BOMB_3, cost: 600, fireDelay: 25, speed: 2500, thrust: 400
    gravBombs start: 2, max: 5, fireDelay: 200
    bullets start: BulletLevel.LEVEL_3, max: BulletLevel.LEVEL_3, cost: 30,  fireDelay: 6, speed: 3600
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 200, fireDelay: 36, speed: 0
    bursts  start: 0, max: 2, speed: 8000
    repels  start: 0, max: 2
    decoys  start: 1, max: 2
    stealth status: 1, energy: 0
    xradar  status: 2, energy: 0
}

ship(Ship.SHARK) {
    rotation initial: 210,  max: 210,  upgrade: 0
    thrust   initial: 13,   max: 24,   upgrade: 0
    speed    initial: 1875, max: 6000, upgrade: 0
    recharge initial: 1500, max: 1500, upgrade: 0
    energy   initial: 1200, max: 1200, upgrade: 0
    linearDamping       0.99
    turnResponsiveness  2.0
    bounceRestitution   0.3
    radarRange          50
    bombs     start: BombLevel.BOMB_3, max: BombLevel.BOMB_3, cost: 320, fireDelay: 180, speed: 4900, thrust: 400
    gravBombs start: 2, max: 5, fireDelay: 200
    bullets start: BulletLevel.LEVEL_2, max: BulletLevel.LEVEL_3, cost: 29,  fireDelay: 8, speed: 3000
    mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 500, fireDelay: 36, speed: 0
    bursts  start: 0, max: 3, speed: 8000
    repels  start: 0, max: 2
    decoys  start: 0, max: 2
    cloak   status: 2, energy: 900
    stealth status: 1, energy: 1
    xradar  status: 1, energy: 0
}
