// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BulletLevel;

/**
 * Per-ship bullet tuning template; projected at spawn by {@code ShipSpawnSystem}.
 *
 * <p>{@code speed} is signed Subspace velocity units; consumer multiplies by
 * {@link EngineConfig#subspaceVelocityScale()} and clamps to {@link EngineConfig#maxProjectileSpeedJme()}.
 * Negative fires backward. VIE legacy presets sometimes encode backward via int16-overflow
 * ({@code BulletSpeed 64636} = signed {@code -900}); Infinity authors the negative literal directly.
 */
public record BulletStats(BulletLevel start, BulletLevel max, int cost, long fireDelayCs, int speed) {}
