// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Stats record for the Rotation aspect: hard cap + per-prize increment (rad/sec). See ADR 0001. */
public record RotationStats(double max, double upgrade) implements EntityComponent {

  public RotationStats() {
    this(0.0, 0.0);
  }
}
