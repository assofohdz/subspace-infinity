// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-ship burst tuning template. Projected at spawn into {@code Burst} /
 * {@code BurstMax} / {@code BurstSpeed} components by {@code ShipSpawnSystem}.
 *
 * <p>Burst in Infinity is a deployable that, when fired, emits a 360°
 * radial fan of projectiles around the firing ship — see
 * {@code WeaponsSystem.createProjectileBurst}. The number of projectiles
 * in the fan is arena-global ({@code BurstFireConfig.projectileCount},
 * fragment-authored from {@code burst.groovy}); the per-ship knob in this
 * record covers the launch speed of each projectile.
 *
 * <p>Subspace canon ({@code [Shrapnel] BurstSpeed}) is per-ship even
 * though it sits under the {@code [Shrapnel]} INI section header — that's
 * a section-grouping artifact, not a mechanic relationship. Burst is
 * distinct from bomb-detonation shrapnel (which is slice 11 territory).
 *
 * <p>Replaces {@code CountStats} previously used for bursts; decoys /
 * bricks / portals continue to use {@code CountStats}.
 *
 * @param start initial burst inventory count a freshly-spawned ship has
 * @param max highest burst count the ship can ever hold
 * @param speed projectile launch speed in <em>Subspace velocity units</em>
 *     (Subspace canonical {@code [Ship] BurstSpeed} key range). The
 *     fire-time consumer ({@code WeaponsSystem.getAttackInfo}) multiplies
 *     by {@code EngineConfig.subspaceVelocityScale} and clamps to
 *     {@code EngineConfig.maxProjectileSpeedJme} to land at jME world
 *     units. See slice 10. Magnitude only — burst is a radial-equidistant
 *     fan around the firing ship, so a "backward" direction is undefined.
 *     Slice 10b's signed-scalar contract on {@code bulletSpeed} /
 *     {@code bombSpeed} does not apply here; a negative value would
 *     invert the radial pattern (shrapnel fly inward), which is nonsense
 *     gameplay rather than a feature.
 */
public record BurstStats(int start, int max, int speed) {}
