// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Stats record for the Speed aspect: hard cap + per-prize increment (Subspace velocity units). See ADR 0001. */
public record SpeedStats(int max, int upgrade) implements EntityComponent {

  public SpeedStats() {
    this(0, 0);
  }
}
