// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Bot AI tuning — per-arena Behaviour Tree weights for the "Brawler" archetype.
// See ADR-0009 (architecture) and ADR-0010 (composition DSL trajectory).
// Live-reloaded via the standard ConfigRegistry fragment pipeline; edit + restart
// arena (or rely on the GroovyFileWatcher poll cadence) to see behaviour change.

botBrain {
    archetypeName         'Brawler'
    perceptionRadius      30.0    // sphere radius (world units) for body query
    engageRange           20.0    // InWeaponRange threshold — bot orbits + fires within this
    orbitRadius           15.0    // desired distance for OrbitTarget circle-strafe
    evadeEnergyFraction   0.6     // flee when current energy < 60% of max
    leadPredictionSeconds 0.5     // Reynolds Pursue/Evade lead window
    aimConeDegrees        15.0    // half-angle of firing cone — FireWeapon gate
}
