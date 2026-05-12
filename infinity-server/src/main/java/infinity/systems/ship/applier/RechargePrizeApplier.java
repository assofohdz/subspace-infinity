// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recharge-rate boost (UI "Recharge" = VIE QuickCharge, REFERENCE.md ## PrizeWeight). Emits {@link EnergyStatsChange#ofRechargePerSecond}. */
public final class RechargePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RechargePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final EnergyStats stats = ed.getComponent(ship, EnergyStats.class);
    if (stats == null) {
      return;
    }
    final double delta = stats.rechargeUpgrade();
    if (Double.compare(delta, 0.0) == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} recharge upgrade: emitting EnergyStatsChange delta={}", ship, delta);
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(
        changeId,
        ChangeTarget.self(ship),
        EnergyStatsChange.ofRechargePerSecond(delta));
  }
}
