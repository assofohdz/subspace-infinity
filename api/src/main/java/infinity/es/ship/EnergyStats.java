// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Slowly-changing rules governing the live {@link Energy} pool; projected from {@code ShipConfig} by {@code ShipSpawnSystem}. See ADR 0001. */
public record EnergyStats(
    // Subspace canon: InitialEnergy + n*UpgradeEnergy (clamped at hardMax = MaximumEnergy).
    int max,
    int hardMax,
    int upgrade,
    // Subspace canon recharge keys are "amount per 10 seconds"; ÷10 at projection → energy/sec.
    double rechargePerSecond,
    double rechargeMax,
    double rechargeUpgrade)
    implements EntityComponent {

  public EnergyStats() {
    this(0, 0, 0, 0.0, 0.0, 0.0);
  }
}
