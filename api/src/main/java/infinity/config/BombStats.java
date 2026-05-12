// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/**
 * Per-ship bomb tuning template; projected at spawn by {@code ShipSpawnSystem}.
 *
 * <p>{@code speed} / {@code thrust} are signed Subspace velocity units; consumer multiplies by
 * {@link EngineConfig#subspaceVelocityScale()} and clamps to {@link EngineConfig#maxProjectileSpeedJme()}.
 * Negative {@code speed} fires backward. VIE legacy presets sometimes encode backward via int16-overflow
 * ({@code BombSpeed 64636} = signed {@code -900}); Infinity authors the negative literal directly.
 * {@code thrust} is shared between {@code BOMB} and {@code GRAVBOMB} fires (Subspace canon: gravbombs are level-3 bombs).
 */
public record BombStats(
    BombLevel start, BombLevel max, int cost, long fireDelayCs, int speed, int thrust) {}
