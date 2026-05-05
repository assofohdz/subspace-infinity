// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

bomb {
    damageLevel    5600
    aliveTimeCs    250    // Subspace VIE convention; ×10 → ms internally.
    explodeRadius  5      // L1 base blast radius in tiles (= SVS canon
                          // 80 px ÷ 16 px/tile). Per-level mult applied
                          // at fire time (L1=1×, L2=2×, L3=3×, L4=4×).
}
