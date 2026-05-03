// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena gravity-bomb (warp-bomb / wormhole-bomb) tuning. Read at
 * projectile-creation time by {@code WeaponsSystem.createProjectileGravBomb}.
 *
 * <p><b>Damage and decay are inherited from {@link BombConfig}.</b> Subspace
 * VIE treats gravity bombs as level-3 bombs sharing the {@code [Bomb]}
 * section's tuning; this record only carries the two Infinity-specific
 * knobs ({@link #delayMs}, {@link #wormholeForce}) that govern the
 * wormhole phase. There is no canonical {@code [GravBomb]} fragment
 * section.
 *
 * @param delayMs delay before the bomb transitions into a wormhole
 *     (Infinity default {@code 1000})
 * @param wormholeForce gravity-well pull strength once the bomb opens its
 *     wormhole phase (Infinity default {@code 5000})
 */
public record GravBombConfig(long delayMs, double wormholeForce) {

  public static final GravBombConfig DEFAULTS = new GravBombConfig(1000L, 5000.0);
}
