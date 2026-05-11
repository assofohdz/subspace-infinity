// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Stats record for the Thrust aspect: hard cap + per-prize increment (Subspace accel units). See ADR 0001. */
public record ThrustStats(int max, int upgrade) implements EntityComponent {

  public ThrustStats() {
    this(0, 0);
  }
}
