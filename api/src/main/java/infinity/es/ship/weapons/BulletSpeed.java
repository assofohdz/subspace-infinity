// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship bullet launch speed, in <em>Subspace velocity units</em>
 * (matches the {@code [Ship] BulletSpeed} key range Subspace authors).
 *
 * <p>Stored raw (pre-scale) — the consumer at fire time
 * ({@code WeaponsSystem.getAttackInfo}) multiplies by
 * {@code EngineConfig.subspaceVelocityScale} and clamps to
 * {@code EngineConfig.maxProjectileSpeedJme} to land in jME world units.
 * Storing raw lets engine-tier tweaks apply on next fire without
 * re-projection of components.
 *
 * <p>Projected at spawn from {@code BulletStats.speed} by
 * {@code ShipSpawnSystem.projectBullets}. See slice 10.
 */
public class BulletSpeed implements EntityComponent {

  private final int speed;

  public BulletSpeed() {
    this(0);
  }

  public BulletSpeed(final int speed) {
    this.speed = speed;
  }

  public int getSpeed() {
    return speed;
  }
}
