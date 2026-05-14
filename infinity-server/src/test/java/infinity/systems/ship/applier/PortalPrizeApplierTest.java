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
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalChange;
import infinity.es.ship.actions.PortalStats;
import org.junit.Test;

/**
 * INVENTORY-family applier test for Portal. REFERENCE.md {@code ## PrizeWeight}
 * ({@code WarpPoint}) + per-ship {@code [Ship] InitialPortal} / {@code PortalMax};
 * {@code [Misc] WarpPointDelay} (REFERENCE.md line 163, "Portal point active time")
 * is consumed at fire-time, not pickup.
 */
public class PortalPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(PortalChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowCap_emitsPlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new PortalStats(2));
    ed.setComponent(ship, new Portal(0));

    new PortalPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(PortalChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(PortalChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new PortalStats(2));
    ed.setComponent(ship, new Portal(2));

    new PortalPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_portalsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new PortalStats(0));

    new PortalPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_noLiveCount_emitsAgainstZeroBaseline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new PortalStats(2));

    new PortalPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(1, countChangeHolders(ed));
  }
}
