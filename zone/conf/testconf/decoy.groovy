// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// testconf [Misc] DecoyAliveTime tuning. Read by DecoyAdapter into
// DecoyConfig; applied at place-time via Pattern 4 (per-arena
// ConfigRegistry → marker entity with Decay via
// GameEntities.createDecoy).
//
// testarena uses deva's faster value (4500 cs = 45 s) instead of
// trench's 100 s — shorter cycle gives smoke testers faster feedback
// when verifying the place / decay loop.

decoy {
    aliveTime  4500
}
