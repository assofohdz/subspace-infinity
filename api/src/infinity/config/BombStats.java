// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.BombLevel;

/**
 * Per-ship bomb tuning template. Projected at spawn into
 * {@code BombCurrentLevel} / {@code BombMaxLevel} / {@code BombCost} /
 * {@code BombFireDelay} / {@code BombSpeed} / {@code BombThrust}
 * components by {@code ShipSpawnSystem}.
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
 * @param thrust recoil impulse magnitude on bomb fire, in <em>Subspace
 *     velocity units</em> (Subspace canonical {@code [Ship] BombThrust}
 *     key range — typical SVS warbird value {@code 400}). Applied via
 *     sio2-mphys {@code Impulse} on the firing ship opposite the ship's
 *     forward direction at fire time. Scaled the same way as
 *     {@code speed}: {@code WeaponsSystem.applyBombRecoil} multiplies by
 *     {@code EngineConfig.subspaceVelocityScale} and clamps to
 *     {@code maxProjectileSpeedJme} via the same
 *     {@code effectiveProjectileSpeed} helper. {@code 0} = no recoil.
 *     Sign-preserving: negative thrust pushes the ship forward on fire
 *     (parallels slice 10b's signed projectile speed). Shared between
 *     {@code BOMB} and {@code GRAVBOMB} fires (Subspace canon: gravbombs
 *     are level-3 bombs sharing per-ship knobs). See slice S2.
 */
public record BombStats(
    BombLevel start, BombLevel max, int cost, long fireDelayCs, int speed, int thrust) {}
