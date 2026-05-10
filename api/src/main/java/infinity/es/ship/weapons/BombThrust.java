// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship bomb-fire recoil magnitude, in <em>Subspace velocity units</em>
 * (matches the {@code [Ship] BombThrust} key range Subspace authors —
 * typical SVS warbird value {@code 400}).
 *
 * <p>Stored raw (pre-scale) — the consumer at fire time
 * ({@code WeaponsSystem.applyBombRecoil}) multiplies by
 * {@code EngineConfig.subspaceVelocityScale} and clamps to
 * {@code EngineConfig.maxProjectileSpeedJme} via the same
 * {@code effectiveProjectileSpeed} helper used by {@code BombSpeed}, so
 * engine-tier tweaks apply on next fire without re-projection.
 *
 * <p>Projected at spawn from {@code BombStats.thrust} by
 * {@code ShipSpawnSystem.projectBombs}. Applied via sio2-mphys
 * {@code Impulse} on the firing ship opposite the ship's forward
 * direction at fire time. {@code 0} = no recoil. Shared between
 * {@code BOMB} and {@code GRAVBOMB} fires (Subspace canon: gravbombs are
 * level-3 bombs sharing per-ship knobs). See slice S2 +
 * {@code REFERENCE.md ## Bomb}.
 *
 * <p>Server-only — read by {@code WeaponsSystem} on bomb fire to compute
 * the impulse magnitude. Does not cross the wire; not registered in
 * {@code GameServer.registerSerializers()}.
 */
public class BombThrust implements EntityComponent {

  private final int thrust;

  public BombThrust() {
    this(0);
  }

  public BombThrust(final int thrust) {
    this.thrust = thrust;
  }

  public int getThrust() {
    return thrust;
  }
}
