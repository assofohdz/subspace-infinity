// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BulletLevel;

/**
 * Per-ship bullet tuning template. Projected at spawn into
 * {@code BulletCurrentLevel} / {@code BulletMaxLevel} / {@code BulletCost} /
 * {@code BulletFireDelay} / {@code BulletSpeed} components by
 * {@code ShipSpawnSystem}.
 *
 * @param start initial bullet level a freshly-spawned ship has equipped
 * @param max highest bullet level the ship can ever reach (cap on level-up
 *     prizes)
 * @param cost energy cost per bullet fire
 * @param fireDelayCs cooldown between bullet fires, in centiseconds
 * @param speed projectile launch speed in <em>Subspace velocity units</em>
 *     (Subspace canonical {@code [Ship] BulletSpeed} key range). The
 *     fire-time consumer ({@code WeaponsSystem.getAttackInfo}) multiplies
 *     by {@code EngineConfig.subspaceVelocityScale} and clamps to
 *     {@code EngineConfig.maxProjectileSpeedJme} to land at jME world
 *     units. See slice 10. Negative values fire backward (signed scalar;
 *     magnitude is capped at {@code maxProjectileSpeedJme}, sign
 *     preserved). Subspace VIE legacy presets sometimes encoded backward
 *     as int16-overflow ({@code BulletSpeed 64636} = signed {@code -900});
 *     Infinity authors the negative literal directly.
 */
public record BulletStats(BulletLevel start, BulletLevel max, int cost, long fireDelayCs, int speed) {}
