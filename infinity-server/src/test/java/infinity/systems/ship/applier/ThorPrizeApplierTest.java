// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.ThorChange;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorStats;
import org.junit.Test;

/**
 * INVENTORY-family applier for Thor — per-ship {@code ThorMax} cap, {@code +1} per pickup.
 * Subspace canon: REFERENCE.md "Inventory caps and starts" ({@code ThorMax}, {@code InitialThor})
 * + {@code ## PrizeWeight} ({@code Thor}). Wave-4b note: applier seeds {@link ThorFireDelay}
 * from per-ship {@link ThorStats#fireDelayMillis()} on first acquire (no spawn-projected timer).
 */
public class ThorPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(ThorChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowCap_emitsThorChangePlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new ThorStats(2, 1500L));
    ed.setComponent(ship, new ThorCurrentCount(0));

    new ThorPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(ThorChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(1, holder.get(ThorChange.class).delta());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_firstAcquire_seedsThorFireDelayFromStats() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new ThorStats(2, 1500L));
    // No ThorCurrentCount, no ThorFireDelay — first-time acquire.

    new ThorPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertNotNull("First-time acquire must seed ThorFireDelay from per-ship Stats",
        ed.getComponent(ship, ThorFireDelay.class));
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new ThorStats(2, 1500L));
    ed.setComponent(ship, new ThorCurrentCount(2));

    new ThorPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_thorsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // Per-ship ThorMax = 0 — ship type not allowed thors.
    ed.setComponent(ship, new ThorStats(0, 1500L));

    new ThorPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
    assertNull(ed.getComponent(ship, ThorCurrentCount.class));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new ThorPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
