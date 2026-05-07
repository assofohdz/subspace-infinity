// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.GunLevel;

/**
 * Per-ship gun tuning template. Projected at spawn into
 * {@code GunCurrentLevel} / {@code GunMaxLevel} / {@code GunCost} /
 * {@code GunFireDelay} / {@code GunSpeed} components by
 * {@code ShipSpawnSystem}.
 *
 * <p>"Gun" is Infinity's internal name for the bullet-firing weapon family;
 * Subspace canon authors {@code [Ship] BulletFireEnergy},
 * {@code BulletFireDelay}, {@code BulletSpeed}. Slice R1 will rename the
 * internal {@code Gun*} → {@code Bullet*} for canon alignment.
 *
 * @param start initial gun level a freshly-spawned ship has equipped
 * @param max highest gun level the ship can ever reach (cap on level-up
 *     prizes)
 * @param cost energy cost per gun fire
 * @param fireDelayCs cooldown between gun fires, in centiseconds
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
public record GunStats(GunLevel start, GunLevel max, int cost, long fireDelayCs, int speed) {}
