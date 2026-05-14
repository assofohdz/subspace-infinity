// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship snapshot of arena {@code BombConfig.jitterTimeMs} for bomb-hit screen shake; stamped onto
 * victims via {@code WeaponsDamageLogic.stampJitter}. Projected at ship spawn per ADR-0002. {@code 0}=disabled.
 */
public record BombJitterTime(long jitterMs) implements EntityComponent {

  public BombJitterTime() {
    this(0L);
  }
}
