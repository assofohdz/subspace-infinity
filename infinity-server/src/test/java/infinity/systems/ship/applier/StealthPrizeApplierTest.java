// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.toggles.StealthActiveChange;
import infinity.es.ship.toggles.StealthStats;
import org.junit.Test;

/**
 * Status-family applier test for Stealth — Subspace tri-state {@code StealthStatus} +
 * {@code StealthEnergy} drain (REFERENCE.md "Ship abilities"). Mirrors {@code CloakPrizeApplierTest}.
 */
public class StealthPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(StealthActiveChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_statusZero_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new StealthStats(0, 0.0));

    new StealthPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Forbidden ship must not produce any Change holders",
        0, countChangeHolders(ed));
    assertNull("Forbidden ship must not gain a StealthActive toggle",
        ed.getComponent(ship, StealthActive.class));
  }

  @Test
  public void apply_statusOne_emitsChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new StealthStats(1, 150.0));
    ed.setComponent(ship, new StealthActive(false));

    new StealthPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Acquirable ship must produce exactly one Change holder",
        1, countChangeHolders(ed));
  }

  @Test
  public void apply_statusTwoAlreadyActive_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new StealthStats(2, 150.0));
    ed.setComponent(ship, new StealthActive(true));

    new StealthPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Re-pickup on already-active ship is a no-op",
        0, countChangeHolders(ed));
    assertNotNull("StealthActive remains on",
        ed.getComponent(ship, StealthActive.class));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No StealthStats — capability not authored on this ship.

    new StealthPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
