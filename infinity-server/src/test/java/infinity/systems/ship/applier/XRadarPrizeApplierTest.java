// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ship.toggles.XRadar;
import infinity.es.ship.toggles.XRadarStatus;
import org.junit.Test;

/**
 * Status-family applier test for XRadar — same tri-state shape as
 * {@link CloakPrizeApplierTest}; one happy-path + one no-op-on-zero
 * case to pin the parallel implementation. AntiWarp follows the same
 * shape; symmetry is verified by inspection.
 */
public class XRadarPrizeApplierTest {

  @Test
  public void apply_statusOne_enablesXRadar() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new XRadarStatus(1));
    ed.setComponent(ship, new XRadar(false));

    new XRadarPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertTrue("Acquirable ship must have XRadar toggled on",
        ed.getComponent(ship, XRadar.class).isEnabled());
  }

  @Test
  public void apply_statusZero_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new XRadarStatus(0));

    new XRadarPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertNull("Forbidden ship must not gain an XRadar toggle",
        ed.getComponent(ship, XRadar.class));
  }
}
