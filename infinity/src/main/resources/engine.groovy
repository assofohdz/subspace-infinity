// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

// Game-wide developer-tunable engine knobs. Loaded once at server startup
// from the classpath. This file lives inside the jar (resources) because
// these are developer-tuned, not operator-tuned — different from
// zone/arena/preset configs which are intended to be operator-editable.
//
// Live-reload not yet wired; restart-tuned. Slice O1 (operator-editable
// conf as external assets) will revisit the location strategy if needed.

engine {
    // Subspace → Infinity velocity-unit conversion. Per-ship BulletSpeed /
    // BombSpeed / BurstSpeed values authored in ships.groovy are in
    // Subspace velocity units (canonical SVS range, e.g. 2000-5000 for a
    // standard ship). The fire-time consumer in WeaponsSystem multiplies by
    // this scale to land in jME world-units / sec.
    //
    // Default 0.01 produces SVS canonical 2000 → 20 jME and the existing
    // trench warbird's 5000 → 50 (matches today's hardcoded
    // addLocal(0,0,50) behaviour for bullets).
    subspaceVelocityScale 0.01

    // Post-translation absolute cap (jME world-units / sec). Clamps stray
    // legacy values to keep them physics-safe — e.g. trench javelin's
    // legacy BulletSpeed 64636 (likely SVS int16-overflow encoding for
    // backward firing, see Slice 10b) translates to 646 jME and gets
    // clamped to the cap before it can crash physics.
    //
    // Default 100 sits comfortably above today's typical 20-50 range
    // while staying short of the ~330 ceiling flagged as physics-breaking.
    maxProjectileSpeedJme 100

    // Slice S1-cal — ship max-speed scale. Multiplier on the ship's Speed
    // component (raw Subspace velocity units) at PlayerDriver consumer
    // time to derive the jME cap. Distinct from subspaceVelocityScale
    // because the projectile fit (5000 → 50) made ship max-speed feel
    // too fast once LinearDamping 0.99 landed in S1 (steady-state ~889
    // jME/sec on trench warbird). 0.01 maps Speed 2000 → 20 jME/sec
    // cap (steady-state ~19.75 with damping 0.99), so ships move at
    // ~40% of bullet velocity (5000 × subspaceVelocityScale 0.01 = 50).
    shipMaxSpeedScale 0.01

    // Slice S2-cal — bomb recoil scale. Multiplier on per-ship BombThrust
    // at WeaponsSystem.applyBombRecoil time. Distinct from
    // subspaceVelocityScale because the projectile fit (400 × 0.01 = 4.0)
    // felt too pushy in S2 playtest. 0.0005 maps SVS canon BombThrust
    // 400 → 0.2 jME/sec backward impulse — a subtle nudge (~1% of ship
    // max-speed) rather than a strong shove.
    bombThrustScale 0.0005

    // ---- Per-projectile / map-decoration / ship collision radii --------
    // Slice projectile-radius-pattern4 + slice s6-ship-radius lifted these
    // values out of legacy hardcoded constants. Authored in jME world units
    // (1 unit ≈ 1 tile per moss-world-grid). These are physics-engine facts
    // about how projectiles, decoration entities, and ships collide; they
    // are not Subspace-canon authored knobs and would not sensibly differ
    // between two arenas in the same build, which is why they live here in
    // engine-tier rather than per-arena *Config. Subspace's per-ship
    // `Radius` is intentionally collapsed to a single engine-tier
    // `shipRadius` for the same reason.

    bulletRadius 0.125
    bombRadius 0.5
    mineRadius 0.5
    thorRadius 0.5
    prizeRadius 0.5
    burstRadius 0.125
    repelRadius 0.125
    over1Radius 0.5
    over2Radius 1.0
    over5Radius 0.1
    flagRadius 0.5
    shipRadius 1.0
}
