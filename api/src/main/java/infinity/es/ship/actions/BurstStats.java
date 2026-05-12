// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Stats record for the Burst aspect: hard-cap count + projectile speed. See ADR 0001. */
public record BurstStats(int max, int speed) implements EntityComponent {

  public BurstStats() {
    this(0, 0);
  }
}
