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
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyChange;
import infinity.es.ship.actions.DecoyStats;
import org.junit.Test;

/**
 * INVENTORY-family applier test for Decoy. REFERENCE.md {@code ## PrizeWeight} ({@code Decoy}) +
 * per-ship {@code [Ship] InitialDecoy} / {@code DecoyMax}; {@code [Misc] DecoyAliveTime}
 * (REFERENCE.md line 164) drives the dropped-decoy {@code Decay} at fire-time, not pickup.
 */
public class DecoyPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(DecoyChange.class, ChangeTarget.class);
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
    ed.setComponent(ship, new DecoyStats(3));
    ed.setComponent(ship, new Decoy(1));

    new DecoyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(DecoyChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(DecoyChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new DecoyStats(3));
    ed.setComponent(ship, new Decoy(3));

    new DecoyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_decoysDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new DecoyStats(0));

    new DecoyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_noLiveCount_emitsAgainstZeroBaseline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new DecoyStats(3));

    new DecoyPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(1, countChangeHolders(ed));
  }
}
