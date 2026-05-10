// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// testconf [Prize] tuning. Read by PrizeAdapter into PrizeConfig;
// consumed by PrizeSystem.sampleDecayMs at prize-spawn time.
//
// minExist + maxExist define a uniform random lifetime range
// (REFERENCE.md ## Prize) — each prize from a default-cadence
// spawner gets a per-prize sampled lifetime in [minExist, maxExist] cs.
// Per-spawner explicit `ttlMs` in arena.groovy's spawners block
// is a fixed override (precedence α from Slice 8a) — no randomisation.
//
// testarena uses deva's faster lifetime range (20–50 s) instead of
// trench's 40–120 s — shorter prize cycle = faster smoke-test
// feedback when verifying the spawn / decay loop, which is exactly
// where Slice 8d (player scaling, regen batch, hidden mode) lives.

prize {
    minExist        2000    // 20 sec
    maxExist        5000    // 50 sec
    deathPrizeTime  1500    // 15 sec — Subspace SVS canonical (Slice 8b)
    negativeFactor  1000    // 1-in-1000 odds for a green to spawn as a Dud (Slice 8c)
}
