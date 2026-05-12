// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import infinity.BulletLevel;

/** Stats record for the Bullet aspect: hard-cap level + fire cost + fire-delay duration + projectile speed. See ADR 0001. */
public record BulletStats(BulletLevel max, int fireCostEnergy, long fireDelayMillis, int speed)
    implements EntityComponent {

  public BulletStats() {
    this(null, 0, 0L, 0);
  }
}
