// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship burst-projectile launch speed, in <em>Subspace velocity units</em>
 * (matches the {@code [Ship] BurstSpeed} key range Subspace authors).
 *
 * <p>Stored raw (pre-scale) — the consumer at fire time
 * ({@code WeaponsSystem.getAttackInfo}) multiplies by
 * {@code EngineConfig.subspaceVelocityScale} and clamps to
 * {@code EngineConfig.maxProjectileSpeedJme} to land in jME world units.
 * Storing raw lets engine-tier tweaks apply on next fire without
 * re-projection of components.
 *
 * <p>Projected at spawn from {@code BurstStats.speed} by
 * {@code ShipSpawnSystem.projectBursts}. See slice 10.
 */
public class BurstSpeed implements EntityComponent {

  private final int speed;

  public BurstSpeed() {
    this(0);
  }

  public BurstSpeed(final int speed) {
    this.speed = speed;
  }

  public int getSpeed() {
    return speed;
  }
}
