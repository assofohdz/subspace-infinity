// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Slowly-changing rules governing the live {@link Energy} pool; projected from {@code ShipConfig} by {@code ShipSpawnSystem}. See ADR 0001. */
public record EnergyStats(
    int max,
    int hardMax,
    int upgrade,
    double rechargePerSecond,
    double rechargeMax,
    double rechargeUpgrade)
    implements EntityComponent {

  public EnergyStats() {
    this(0, 0, 0, 0.0, 0.0, 0.0);
  }
}
