// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Partial-record delta to {@link EnergyStats}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code EnergyStatsSystem}. See ADR 0001. */
public record EnergyStatsChange(
    Integer deltaMax,
    Integer deltaHardMax,
    Integer deltaUpgrade,
    Double deltaRechargePerSecond,
    Double deltaRechargeMax,
    Double deltaRechargeUpgrade)
    implements EntityComponent {

  public EnergyStatsChange() {
    this(null, null, null, null, null, null);
  }

  public static EnergyStatsChange ofMax(final int delta) {
    return new EnergyStatsChange(delta, null, null, null, null, null);
  }

  public static EnergyStatsChange ofHardMax(final int delta) {
    return new EnergyStatsChange(null, delta, null, null, null, null);
  }

  public static EnergyStatsChange ofUpgrade(final int delta) {
    return new EnergyStatsChange(null, null, delta, null, null, null);
  }

  public static EnergyStatsChange ofRechargePerSecond(final double delta) {
    return new EnergyStatsChange(null, null, null, delta, null, null);
  }

  public static EnergyStatsChange ofRechargeMax(final double delta) {
    return new EnergyStatsChange(null, null, null, null, delta, null);
  }

  public static EnergyStatsChange ofRechargeUpgrade(final double delta) {
    return new EnergyStatsChange(null, null, null, null, null, delta);
  }
}
