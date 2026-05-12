// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/** AoE-damage marker — pairs with {@link Damage}; switches contact handler to splash-scan within {@link #getRadiusWorldUnits()}. */
public final class SplashDamage implements EntityComponent {

  private final double radiusWorldUnits;

  public SplashDamage() {
    this(0.0);
  }

  public SplashDamage(final double radiusWorldUnits) {
    this.radiusWorldUnits = radiusWorldUnits;
  }

  /** Blast radius in world units (1 unit ≈ 1 tile). */
  public double getRadiusWorldUnits() {
    return radiusWorldUnits;
  }
}
