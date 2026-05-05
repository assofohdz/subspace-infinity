// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

bomb {
    damageLevel       2650
    aliveTimeCs       6000   // Subspace VIE convention; ×10 → ms internally.
    explodeRadius     5      // L1 base blast radius in tiles (= SVS canon
                             // 80 px ÷ 16 px/tile). Per-level mult applied
                             // at fire time (L1=1×, L2=2×, L3=3×, L4=4×).
    proximityDistance 3      // Slice 9b — L1 proximity-arm radius in tiles
                             // (canon SVS). Per-level additive (+1 per level
                             // above L1) at fire time. 0 to disable.
    explodeDelayCs    10     // Slice 9b — fuse delay after arming, in
                             // centiseconds (×10 → ms = 100ms). 0 to disable.
}
