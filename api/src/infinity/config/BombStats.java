// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/**
 * Per-ship bomb tuning template. Projected at spawn into
 * {@code BombCurrentLevel} / {@code BombMaxLevel} / {@code BombCost} /
 * {@code BombFireDelay} / {@code BombSpeed} components by
 * {@code ShipSpawnSystem}.
 *
 * @param start initial bomb level a freshly-spawned ship has equipped
 * @param max highest bomb level the ship can ever reach (cap on level-up
 *     prizes)
 * @param cost energy cost per bomb fire
 * @param fireDelayCs cooldown between bomb fires, in centiseconds
 * @param speed projectile launch speed in <em>Subspace velocity units</em>
 *     (Subspace canonical {@code [Ship] BombSpeed} key range). The
 *     fire-time consumer ({@code WeaponsSystem.getAttackInfo}) multiplies
 *     by {@code EngineConfig.subspaceVelocityScale} and clamps to
 *     {@code EngineConfig.maxProjectileSpeedJme} to land at jME world
 *     units. See slice 10. Negative values fire backward (signed scalar;
 *     magnitude is capped at {@code maxProjectileSpeedJme}, sign
 *     preserved). Subspace VIE legacy presets sometimes encoded backward
 *     as int16-overflow (e.g. {@code BombSpeed 64636} = signed {@code -900});
 *     Infinity authors the negative literal directly.
 */
public record BombStats(BombLevel start, BombLevel max, int cost, long fireDelayCs, int speed) {}
