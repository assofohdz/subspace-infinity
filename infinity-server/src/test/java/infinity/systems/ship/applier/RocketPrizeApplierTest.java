// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketChange;
import infinity.es.ship.actions.RocketStats;
import org.junit.Test;

/**
 * INVENTORY-family applier for Rocket — per-ship {@code RocketMax} cap, {@code +1} per pickup.
 * Subspace canon: REFERENCE.md "Inventory caps and starts" ({@code RocketMax}, {@code InitialRocket})
 * + {@code ## PrizeWeight} ({@code Rocket}). Mirrors the Repel applier shape.
 */
public class RocketPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(RocketChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowCap_emitsRocketChangePlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RocketStats(3, 5000L));
    ed.setComponent(ship, new Rocket(1));

    new RocketPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(RocketChange.class, ChangeTarget.class);
    try {
      holders.applyChanges();
      assertEquals("Applier emitted exactly one Change holder", 1, holders.size());
      final Entity holder = holders.iterator().next();
      assertEquals(1, holder.get(RocketChange.class).delta());
      assertEquals(ship, holder.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
    // Live Rocket count is unchanged at the applier boundary — RocketSystem drains.
    assertEquals(1, ed.getComponent(ship, Rocket.class).getCount());
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new RocketStats(3, 5000L));
    ed.setComponent(ship, new Rocket(3));

    new RocketPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Already-at-cap ship must not produce a Change holder",
        0, countChangeHolders(ed));
  }

  @Test
  public void apply_rocketsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // Per-ship RocketMax = 0 — ship type not allowed rockets at all.
    ed.setComponent(ship, new RocketStats(0, 0L));

    new RocketPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
    assertNull(ed.getComponent(ship, Rocket.class));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new RocketPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
