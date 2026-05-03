// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Deva [Misc] DecoyAliveTime tuning. Read by DecoyAdapter into
// DecoyConfig; applied at place-time via Pattern 4 (per-arena
// ConfigRegistry → marker entity with Decay via
// GameEntities.createDecoy).
//
// Migrated from deva-04-2026/misc.groovy (DecoyAliveTime 4500) — same
// 4500 cs = 45000 ms lifetime preserved.

decoy {
    aliveTime  4500
}
