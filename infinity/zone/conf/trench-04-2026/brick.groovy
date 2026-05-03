// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Trench [Brick] tuning. Read by BrickAdapter into BrickConfig; applied
// at fire-time via Pattern 4 (per-arena ConfigRegistry → marker entity
// with Decay + BrickSpan via GameEntities.createBrick).
//
// span 7 is the canonical Subspace base-preset value — trench's
// pre-migration misc.groovy didn't author BrickSpan, so we fill in the
// canon default here. Operators can edit this file freely.

brick {
    span  7
    time  1000
}
