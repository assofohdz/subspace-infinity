// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import org.junit.Test;

/**
 * Recharge-rate boost (UI "Recharge" = VIE QuickCharge per REFERENCE.md
 * {@code ## PrizeWeight}). Emits {@link EnergyStatsChange#ofRechargePerSecond}
 * scaling the per-prize {@code UpgradeRecharge} delta — see Per-ship
 * {@code [All]} "Initial / Maximum / Upgrade stats".
 */
public class RechargePrizeApplierTest {

  private static int countStatsChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(EnergyStatsChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_positiveUpgrade_emitsRechargePerSecondDelta() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // rechargeUpgrade = 5.0 energy/sec per Recharge prize (Subspace UpgradeRecharge ÷10).
    ed.setComponent(ship, new EnergyStats(1500, 2000, 100, 100.0, 200.0, 5.0));

    new RechargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(EnergyStatsChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one EnergyStatsChange holder",
          1, holders.size());
      final Entity holder = holders.iterator().next();
      final EnergyStatsChange change = holder.get(EnergyStatsChange.class);
      assertNotNull("rechargePerSecond delta is set", change.deltaRechargePerSecond());
      assertEquals(5.0, change.deltaRechargePerSecond(), 1e-9);
      // Other delta fields must be null — Recharge prize touches only rechargePerSecond.
      assertEquals(null, change.deltaMax());
      assertEquals(null, change.deltaHardMax());
      assertEquals(null, change.deltaUpgrade());
      assertEquals(null, change.deltaRechargeMax());
      assertEquals(null, change.deltaRechargeUpgrade());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_zeroUpgrade_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new EnergyStats(1500, 2000, 100, 100.0, 200.0, 0.0));

    new RechargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Zero rechargeUpgrade must not produce a Change holder",
        0, countStatsChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new RechargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countStatsChangeHolders(ed));
  }
}
