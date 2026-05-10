// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Trench [Misc] DecoyAliveTime tuning. Read by DecoyAdapter into
// DecoyConfig; applied at place-time via Pattern 4 (per-arena
// ConfigRegistry → marker entity with Decay via
// GameEntities.createDecoy).
//
// Migrated from trench-04-2026/misc.groovy (DecoyAliveTime 10000) — same
// 10000 cs = 100000 ms lifetime preserved.

decoy {
    aliveTime  10000
}
