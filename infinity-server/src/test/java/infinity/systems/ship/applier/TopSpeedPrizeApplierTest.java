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
import infinity.es.ship.SpeedChange;
import infinity.es.ship.SpeedStats;
import org.junit.Test;

/**
 * Movement-family cap-bump applier for TopSpeed. Subspace canon: per-ship
 * {@code UpgradeSpeed} (REFERENCE.md "Initial / Maximum / Upgrade stats");
 * {@code ## PrizeWeight} ({@code TopSpeed}). SpeedSystem drains and clamps
 * at {@link SpeedStats#max()} (Subspace {@code MaximumSpeed}).
 */
public class TopSpeedPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(SpeedChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_positiveUpgrade_emitsSpeedChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // upgrade = 30 speed units per prize.
    ed.setComponent(ship, new SpeedStats(3000, 30));

    new TopSpeedPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(SpeedChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(30, holder.get(SpeedChange.class).delta());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_zeroUpgrade_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new SpeedStats(3000, 0));

    new TopSpeedPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new TopSpeedPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
