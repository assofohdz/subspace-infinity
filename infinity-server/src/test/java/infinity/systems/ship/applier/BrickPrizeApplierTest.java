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
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickChange;
import infinity.es.ship.actions.BrickStats;
import org.junit.Test;

/**
 * INVENTORY-family applier test for Brick. REFERENCE.md {@code ## PrizeWeight} ({@code Brick}) +
 * per-ship {@code [Ship] InitialBrick} / {@code BrickMax}; {@code [Brick]} duration tunables are
 * fire-time, not pickup-time.
 */
public class BrickPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(BrickChange.class, ChangeTarget.class);
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
    ed.setComponent(ship, new BrickStats(5));
    ed.setComponent(ship, new Brick(2));

    new BrickPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(BrickChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(BrickChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BrickStats(5));
    ed.setComponent(ship, new Brick(5));

    new BrickPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_bricksDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BrickStats(0));

    new BrickPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_noLiveCount_emitsAgainstZeroBaseline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BrickStats(5));
    // No live Brick — writer folds against 0.

    new BrickPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(1, countChangeHolders(ed));
  }
}
