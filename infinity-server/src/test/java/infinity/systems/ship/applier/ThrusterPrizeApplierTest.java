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
import infinity.es.ship.ThrustChange;
import infinity.es.ship.ThrustStats;
import org.junit.Test;

/**
 * Movement-family cap-bump applier for Thruster. Subspace canon: per-ship
 * {@code UpgradeThrust} (REFERENCE.md "Initial / Maximum / Upgrade stats");
 * {@code ## PrizeWeight} ({@code Thruster}). ThrustSystem drains and clamps
 * at {@link ThrustStats#max()} (Subspace {@code MaximumThrust}).
 */
public class ThrusterPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(ThrustChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_positiveUpgrade_emitsThrustChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // upgrade = 2 thrust units per prize.
    ed.setComponent(ship, new ThrustStats(20, 2));

    new ThrusterPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(ThrustChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(2, holder.get(ThrustChange.class).delta());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_zeroUpgrade_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new ThrustStats(20, 0));

    new ThrusterPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new ThrusterPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
