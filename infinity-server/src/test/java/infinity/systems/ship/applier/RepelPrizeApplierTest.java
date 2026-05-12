// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelChange;
import infinity.es.ship.actions.RepelStats;
import org.junit.Test;

/**
 * Prize-pickup pillar of the spawn-projection test harness.
 *
 * <p>Wave 4b migration: the applier no longer mutates {@link Repel} directly —
 * it emits a Change-entity holder ({@link RepelChange} + {@link ChangeTarget})
 * that {@code RepelCountSystem} drains and clamps. These assertions pin the
 * emit-shape only; the drain semantics + min/max clamps live in
 * {@code RepelCountSystemChangeDrainTest}.
 */
public class RepelPrizeApplierTest {

  /**
   * Below the cap → applier emits a {@code RepelChange(+1)} Change holder
   * targeted at the ship.
   */
  @Test
  public void apply_belowCap_emitsRepelChangePlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RepelStats(20));
    ed.setComponent(ship, new Repel(10));

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    final EntitySet holders = ed.getEntities(RepelChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(1, holder.get(RepelChange.class).delta());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
    // Live Repel is unchanged at the applier boundary — the writer drains
    // the holder on its next tick.
    assertEquals(10, ed.getComponent(ship, Repel.class).getCount());
  }

  /**
   * At the cap → applier no-ops; no Change holder is emitted.
   */
  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RepelStats(20));
    ed.setComponent(ship, new Repel(20));

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    final EntitySet holders = ed.getEntities(RepelChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier did not emit (already at cap)", 0, holders.size());
    } finally {
      holders.release();
    }
  }

  /**
   * Ship without {@link RepelStats} = repels disallowed for this ship type
   * (per-ship {@code [Ship] MaxRepels=0}). Applier no-ops.
   */
  @Test
  public void apply_repelsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    assertNull(ed.getComponent(ship, Repel.class));
    assertNull(ed.getComponent(ship, RepelStats.class));
    final EntitySet holders = ed.getEntities(RepelChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier did not emit (ship not allowed repels)", 0, holders.size());
    } finally {
      holders.release();
    }
  }

  /**
   * No live {@link Repel} on the ship yet — applier still emits; the writer
   * folds the delta against a zero baseline.
   */
  @Test
  public void apply_noLiveCount_emitsChangeWriterFoldsZeroBaseline() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RepelStats(20));

    final PrizeApplierContext ctx = new PrizeApplierContext(ed, null, null);
    new RepelPrizeApplier().apply(ship, ctx);

    final EntitySet holders = ed.getEntities(RepelChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals(1, holders.size());
      assertNotNull(holders.iterator().next().get(RepelChange.class));
    } finally {
      holders.release();
    }
  }
}
