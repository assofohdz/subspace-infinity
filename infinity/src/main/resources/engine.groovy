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
}
