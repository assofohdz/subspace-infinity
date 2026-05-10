// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// testconf [Misc] WarpPointDelay tuning. Read by PortalAdapter into
// PortalConfig; activeTime applied at place-time via Pattern 4
// (per-arena ConfigRegistry → marker entity with Decay via
// GameEntities.createPortal).
//
// testarena uses deva's faster value (12000 cs = 120 s) instead of
// trench's 240 s — shorter cycle = faster smoke-test feedback for
// portal place / decay verification.

portal {
    activeTime  12000
}
