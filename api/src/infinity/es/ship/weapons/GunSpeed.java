// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship gun (bullet) launch speed, in <em>Subspace velocity units</em>
 * (matches the {@code [Ship] BulletSpeed} key range Subspace authors).
 *
 * <p>Stored raw (pre-scale) — the consumer at fire time
 * ({@code WeaponsSystem.getAttackInfo}) multiplies by
 * {@code EngineConfig.subspaceVelocityScale} and clamps to
 * {@code EngineConfig.maxProjectileSpeedJme} to land in jME world units.
 * Storing raw lets engine-tier tweaks apply on next fire without
 * re-projection of components.
 *
 * <p>Projected at spawn from {@code GunStats.speed} by
 * {@code ShipSpawnSystem.projectGuns}. See slice 10. Slice R1 will rename
 * the {@code Gun*} component family to {@code Bullet*} for Subspace-canon
 * alignment.
 */
public class GunSpeed implements EntityComponent {

  private final int speed;

  public GunSpeed() {
    this(0);
  }

  public GunSpeed(final int speed) {
    this.speed = speed;
  }

  public int getSpeed() {
    return speed;
  }
}
