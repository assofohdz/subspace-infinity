// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BombLevel;

/** Stats record for the Mine aspect: hard-cap level + drop cost + drop-delay duration + projectile speed (mines reuse {@link BombLevel}). See ADR 0001. */
public record MineStats(BombLevel max, int dropCostEnergy, long fireDelayMillis, int speed)
    implements EntityComponent {

  public MineStats() {
    this(null, 0, 0L, 0);
  }
}
