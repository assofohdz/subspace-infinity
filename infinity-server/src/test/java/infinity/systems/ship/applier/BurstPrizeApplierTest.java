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
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstChange;
import infinity.es.ship.actions.BurstStats;
import org.junit.Test;

/**
 * COUNT-family applier test for Burst. REFERENCE.md {@code ## PrizeWeight} ({@code Burst}) +
 * per-ship {@code [Ship] InitialBurst} / {@code BurstMax}.
 */
public class BurstPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(BurstChange.class, ChangeTarget.class);
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
    ed.setComponent(ship, new BurstStats(4, 100));
    ed.setComponent(ship, new Burst(1));

    new BurstPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(BurstChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(BurstChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BurstStats(4, 100));
    ed.setComponent(ship, new Burst(4));

    new BurstPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_burstsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BurstStats(0, 0));

    new BurstPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_noLiveCount_emitsAgainstZeroBaseline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BurstStats(4, 100));

    new BurstPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(1, countChangeHolders(ed));
  }
}
