// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/** Per-ship bomb tuning template; projected at spawn by {@code ShipSpawnSystem}. {@code speed}/{@code thrust} are signed Subspace velocity units, scaled via {@link EngineConfig#subspaceVelocityScale()}. */
public record BombStats(
    BombLevel start, BombLevel max, int cost, long fireDelayCs, int speed, int thrust) {}
