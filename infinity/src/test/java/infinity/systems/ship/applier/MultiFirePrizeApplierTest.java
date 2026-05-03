// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ship.toggles.Multishot;
import org.junit.Test;

/**
 * Bullet-mode applier test for MultiFire. Unlike the Status-family
 * appliers (Cloak / Stealth / XRadar / AntiWarp), MultiFire has no
 * per-ship {@code *Status} tri-state — it applies universally and
 * stamps {@link Multishot}{@code (true)} on the ship. The per-ship
 * {@code MultiFireEnergy} value gates usefulness (a future
 * firing-mode consumer in WeaponsSystem reads it), not the prize
 * applier itself.
 */
public class MultiFirePrizeApplierTest {

  @Test
  public void apply_anyShip_enablesMultishot() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new MultiFirePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertTrue("MultiFire prize must enable the Multishot toggle",
        ed.getComponent(ship, Multishot.class).isEnabled());
  }

  @Test
  public void apply_alreadyEnabled_remainsEnabled() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Multishot(true));

    new MultiFirePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertTrue("Re-pickup of MultiFire is idempotent",
        ed.getComponent(ship, Multishot.class).isEnabled());
  }
}
