// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Stats record for the Rocket aspect: hard-cap inventory count + per-ship buff lifetime (Subspace {@code [Ship] RocketTime}). Replaces the previous standalone {@code RocketTime} component. See ADR 0001. */
public record RocketStats(int max, long buffDurationMillis) implements EntityComponent {

  public RocketStats() {
    this(0, 0L);
  }
}
