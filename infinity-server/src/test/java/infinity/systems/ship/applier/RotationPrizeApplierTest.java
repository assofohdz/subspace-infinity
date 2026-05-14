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
import infinity.es.ship.RotationChange;
import infinity.es.ship.RotationStats;
import org.junit.Test;

/**
 * Movement-family cap-bump applier for Rotation. Subspace canon: per-ship
 * {@code UpgradeRotation} (REFERENCE.md "Initial / Maximum / Upgrade stats");
 * {@code ## PrizeWeight} ({@code Rotation}). RotationSystem drains and clamps
 * at {@link RotationStats#max()} (Subspace {@code MaximumRotation}).
 */
public class RotationPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(RotationChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_positiveUpgrade_emitsRotationChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // upgrade = 0.5 rad/sec per prize.
    ed.setComponent(ship, new RotationStats(6.28318, 0.5));

    new RotationPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(RotationChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(0.5, holder.get(RotationChange.class).delta(), 1e-9);
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_zeroUpgrade_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RotationStats(6.28318, 0.0));

    new RotationPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new RotationPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
