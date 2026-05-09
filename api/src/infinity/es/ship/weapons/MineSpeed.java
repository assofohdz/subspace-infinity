// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship mine launch speed, in <em>Subspace velocity units</em>.
 *
 * <p>Stored raw (pre-scale) — the consumer at fire time
 * ({@code WeaponsSystem.getAttackInfo}) multiplies by
 * {@code EngineConfig.subspaceVelocityScale} and clamps to
 * {@code EngineConfig.maxProjectileSpeedJme} to land in jME world units.
 * Storing raw lets engine-tier tweaks apply on next fire without
 * re-projection of components.
 *
 * <p>Infinity extension — see {@link infinity.config.MineStats#speed} for
 * canon-divergence rationale (Subspace canon does not author a per-ship
 * {@code MineSpeed} knob; default {@code 0} = inert drop).
 *
 * <p>Projected at spawn from {@code MineStats.speed} by
 * {@code ShipWeaponsProjector.projectMines} at ship spawn. Read at fire
 * time by {@code WeaponsSystem.applyWeaponSpeedScale}. Slice s7-mine-speed.
 */
public class MineSpeed implements EntityComponent {

  private final int speed;

  public MineSpeed() {
    this(0);
  }

  public MineSpeed(final int speed) {
    this.speed = speed;
  }

  public int getSpeed() {
    return speed;
  }
}
