// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;

/** Full Charge — refills live {@link Energy} to {@link EnergyStats#max()} (UI "QuickCharge" = VIE Recharge, REFERENCE.md ## PrizeWeight). */
public final class QuickChargePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Energy current = ed.getComponent(ship, Energy.class);
    final EnergyStats stats = ed.getComponent(ship, EnergyStats.class);
    if (current == null || stats == null) {
      return;
    }
    final int delta = stats.max() - current.getEnergy();
    if (delta == 0) {
      return;
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(changeId, ChangeTarget.self(ship), new EnergyChange(delta));
  }
}
