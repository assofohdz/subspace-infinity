// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Deva [Misc] WarpPointDelay tuning. Read by PortalAdapter into
// PortalConfig; activeTime applied at place-time via Pattern 4
// (per-arena ConfigRegistry → marker entity with Decay via
// GameEntities.createPortal).
//
// Migrated from deva-04-2026/misc.groovy (WarpPointDelay 12000) —
// 12000 cs = 120000 ms preserved 1:1.

portal {
    activeTime  12000
}
