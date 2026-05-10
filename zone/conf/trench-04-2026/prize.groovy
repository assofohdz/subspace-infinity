// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Trench [Prize] tuning. Read by PrizeAdapter into PrizeConfig;
// consumed by PrizeSystem.sampleDecayMs at prize-spawn time.
//
// minExist + maxExist define a uniform random lifetime range
// (REFERENCE.md ## Prize) — each prize from a default-cadence
// spawner gets a per-prize sampled lifetime in [minExist, maxExist] cs.
// Per-spawner explicit `ttlMs` in arena.groovy's spawners block
// is a fixed override (precedence α from Slice 8a) — no randomisation.

prize {
    minExist        4000    // 40 sec — Subspace canonical pairing for SVS-style maxExist
    maxExist        12000   // 120 sec
    deathPrizeTime  1500    // 15 sec — Subspace SVS canonical death-drop lifetime (Slice 8b)
    negativeFactor  1000    // 1-in-1000 odds for a green to spawn as a Dud (Slice 8c)
}
