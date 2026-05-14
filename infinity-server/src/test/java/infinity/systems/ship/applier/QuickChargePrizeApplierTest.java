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
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;
import org.junit.Test;

/**
 * Full-charge applier (UI "QuickCharge" = VIE Recharge per REFERENCE.md
 * {@code ## PrizeWeight}). Emits an {@link EnergyChange} that refills live
 * {@link Energy} to {@link EnergyStats#max()}.
 */
public class QuickChargePrizeApplierTest {

  private static int countEnergyChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(EnergyChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowMax_emitsEnergyChangeFillingToMax() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new EnergyStats(1500, 2000, 100, 100.0, 200.0, 10.0));
    ed.setComponent(ship, new Energy(400));

    new QuickChargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(EnergyChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(1500 - 400, holder.get(EnergyChange.class).delta());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atMax_noEnergyChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new EnergyStats(1500, 2000, 100, 100.0, 200.0, 10.0));
    ed.setComponent(ship, new Energy(1500));

    new QuickChargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Already-full ship must not produce a Change holder",
        0, countEnergyChangeHolders(ed));
  }

  @Test
  public void apply_missingEnergy_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new EnergyStats(1500, 2000, 100, 100.0, 200.0, 10.0));
    // No live Energy component yet — applier guards.

    new QuickChargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countEnergyChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Energy(400));

    new QuickChargePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countEnergyChangeHolders(ed));
  }
}
