// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;

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
 * CAPABILITY-family applier test for Energy upgrade. REFERENCE.md {@code ## PrizeWeight}
 * ({@code Energy (= "Energy Upgrade")}); per-ship {@code [Ship] UpgradeEnergy} bumps
 * {@code MaximumEnergy} (clamped at hardMax by the writer, not here).
 */
public class EnergyPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(EnergyStatsChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_emitsEnergyStatsChangeWithUpgradeDelta() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // max=1000, hardMax=2000, upgrade=+100 per pickup.
    ed.setComponent(ship, new EnergyStats(1000, 2000, 100, 5.0, 8.0, 0.5));

    new EnergyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(EnergyStatsChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      final EnergyStatsChange change = h.get(EnergyStatsChange.class);
      assertEquals("Only deltaMax is set; other partial-record fields are null",
          Integer.valueOf(100), change.deltaMax());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_zeroUpgrade_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // upgrade=0 → applier early-returns (no point emitting a no-op delta).
    ed.setComponent(ship, new EnergyStats(1000, 2000, 0, 5.0, 8.0, 0.5));

    new EnergyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No EnergyStats — capability not authored.

    new EnergyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
