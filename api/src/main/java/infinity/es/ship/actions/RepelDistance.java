// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Effect radius of a Repel effect entity, in tiles / world units
 * ({@code 1 unit ≈ 1 tile}). Stamped at fire-time by
 * {@code ConsumableSystem} from per-arena
 * {@link infinity.config.RepelConfig}. Carries the value already converted
 * out of Subspace pixels at the operator boundary
 * ({@link infinity.settings.RepelAdapter}); the simulation layer does not
 * speak in pixels. Per the Pattern 4 template → spawn-projection →
 * component flow, the repel-impulse system
 * ({@link infinity.systems.ship.RepelSystem}) reads this on the spawned
 * entity rather than the {@code RepelConfig} template.
 *
 * @author Asser
 */
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
