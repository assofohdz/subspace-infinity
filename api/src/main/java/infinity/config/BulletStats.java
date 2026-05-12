// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BulletLevel;

/** Per-ship bullet tuning template; projected at spawn by {@code ShipSpawnSystem}. {@code speed} is signed Subspace velocity units, scaled via {@link EngineConfig#subspaceVelocityScale()}. */
public record BulletStats(BulletLevel start, BulletLevel max, int cost, long fireDelayCs, int speed) {}
