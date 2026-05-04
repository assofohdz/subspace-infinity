// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// testconf [Rocket] tuning. Read by RocketAdapter into RocketConfig;
// applied at fire-time via Pattern 4 (per-arena ConfigRegistry +
// per-ship RocketTime → swapped Thrust/Speed for the buff lifetime).
//
// testarena keeps trench's thrust (100) but pulls in deva's speed
// (4000) — snappier rocket dash for smoke testing.

rocket {
    thrust  100
    speed   4000
}
