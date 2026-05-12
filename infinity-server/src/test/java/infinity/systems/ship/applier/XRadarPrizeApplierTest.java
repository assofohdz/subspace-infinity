// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarActiveChange;
import infinity.es.ship.toggles.XRadarStats;
import org.junit.Test;

/**
 * Status-family applier test for XRadar — same tri-state shape as
 * {@link CloakPrizeApplierTest}. AntiWarp follows the same shape; symmetry verified by inspection.
 */
public class XRadarPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(XRadarActiveChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_statusOne_emitsChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new XRadarStats(1, 200.0));
    ed.setComponent(ship, new XRadarActive(false));

    new XRadarPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Acquirable ship must produce exactly one Change holder",
        1, countChangeHolders(ed));
  }

  @Test
  public void apply_statusZero_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new XRadarStats(0, 0.0));

    new XRadarPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Forbidden ship must not produce any Change holders",
        0, countChangeHolders(ed));
  }
}
