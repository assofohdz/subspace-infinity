// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Trench [Misc] WarpPointDelay tuning. Read by PortalAdapter into
// PortalConfig; activeTime applied at place-time via Pattern 4
// (per-arena ConfigRegistry → marker entity with Decay via
// GameEntities.createPortal).
//
// Migrated from trench-04-2026/misc.groovy (WarpPointDelay 24000) —
// 24000 cs = 240000 ms preserved 1:1.

portal {
    activeTime  24000
}
