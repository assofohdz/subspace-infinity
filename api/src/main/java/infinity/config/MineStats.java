// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/** Per-ship mine tuning template; projected at spawn by {@code ShipSpawnSystem}. {@code speed=0} = inert drop. {@code speed} is an Infinity extension (not in canonical {@code ## Mine}). */
public record MineStats(
    BombLevel start, BombLevel max, int cost, long fireDelayCs, int speed) {}
