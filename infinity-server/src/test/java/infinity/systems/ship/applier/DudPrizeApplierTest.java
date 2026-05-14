// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import org.junit.Test;

/**
 * INSTANT no-op pin: Dud is intentionally observable as "did nothing."
 * Infinity-specific (no REFERENCE.md counterpart) — placeholder for unimplemented prize entries.
 */
public class DudPrizeApplierTest {

  @Test
  public void apply_emitsNoChangeAndAddsNoComponents() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new DudPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    // Nothing observable on the ship.
    assertNull("Dud must not stamp any component on the ship",
        ed.getComponent(ship, ChangeTarget.class));
    // No Change holders of any kind anywhere — query the most-permissive shape.
    final EntitySet holders = ed.getEntities(ChangeTarget.class);
    try {
      assertEquals("Dud must not create any Change holder entities", 0, holders.size());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_idempotent() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new DudPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
    new DudPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
    new DudPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(ChangeTarget.class);
    try {
      assertEquals(0, holders.size());
    } finally {
      holders.release();
    }
  }
}
