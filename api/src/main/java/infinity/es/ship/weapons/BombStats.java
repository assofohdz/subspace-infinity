// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import infinity.BombLevel;

/** Stats record for the Bomb aspect: hard-cap level + fire cost + fire-delay duration + projectile speed + recoil thrust. See ADR 0001. */
public record BombStats(
    BombLevel max, int fireCostEnergy, long fireDelayMillis, int speed, int thrust)
    implements EnergyCost {

  public BombStats() {
    this(null, 0, 0L, 0, 0);
  }

  @Override
  public int energyCost() {
    return fireCostEnergy;
  }
}
