// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Repel effect radius in world units (already converted from Subspace pixels at the loader); stamped at fire-time from {@link infinity.config.RepelConfig}. */
public class RepelDistance implements EntityComponent {

  private final double radiusWorldUnits;

  public RepelDistance() {
    this(0.0);
  }

  public RepelDistance(final double radiusWorldUnits) {
    this.radiusWorldUnits = radiusWorldUnits;
  }

  public double getRadiusWorldUnits() {
    return radiusWorldUnits;
  }
}
