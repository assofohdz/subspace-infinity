// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ship.toggles.Cloak;
import infinity.es.ship.toggles.CloakStatus;
import org.junit.Test;

/**
 * Status-family applier test for Cloak. Subspace tri-state behaviour
 * (REFERENCE.md "Ship abilities"):
 * <ul>
 *   <li>{@code CloakStatus 0} (forbidden) → applier no-ops; no
 *       {@link Cloak} toggle conjured.
 *   <li>{@code CloakStatus 1} (acquirable) → applier stamps
 *       {@code Cloak(true)}.
 *   <li>{@code CloakStatus 2} (start-active) → spawn projection sets
 *       {@code Cloak(true)} already, but a re-pickup is idempotent —
 *       applier still leaves the toggle on.
 * </ul>
 *
 * <p>StealthPrizeApplier follows the same shape; symmetry is verified
 * by inspection (one applier-class implementation; one parallel test
 * would be redundant).
 */
public class CloakPrizeApplierTest {

  @Test
  public void apply_statusZero_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakStatus(0));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertNull("Forbidden ship must not gain a Cloak toggle",
        ed.getComponent(ship, Cloak.class));
    assertEquals("CloakStatus must remain unchanged",
        0, ed.getComponent(ship, CloakStatus.class).getStatus());
  }

  @Test
  public void apply_statusOne_enablesCloak() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakStatus(1));
    ed.setComponent(ship, new Cloak(false));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertTrue("Acquirable ship must have Cloak toggled on",
        ed.getComponent(ship, Cloak.class).isEnabled());
  }

  @Test
  public void apply_statusTwo_idempotentlyKeepsCloakOn() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakStatus(2));
    ed.setComponent(ship, new Cloak(true));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertTrue("Start-active ship must remain cloaked after re-pickup",
        ed.getComponent(ship, Cloak.class).isEnabled());
  }

  @Test
  public void apply_missingStatus_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No CloakStatus component — capability not authored on this ship.

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertNull("Ship without CloakStatus must not gain a Cloak toggle",
        ed.getComponent(ship, Cloak.class));
  }

  /**
   * Mid-arena reload (e.g. operator edits ships.groovy live). The toggle
   * is preserved on a {@code resetLivePool=false} respawn so the
   * applier-driven toggle isn't clobbered by spawn projection. Sanity
   * check: applying a cloak prize when the toggle is already on stays on.
   */
  @Test
  public void apply_alreadyOn_remainsOn() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakStatus(1));
    ed.setComponent(ship, new Cloak(true));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertTrue(ed.getComponent(ship, Cloak.class).isEnabled());
    assertFalse("Sanity: applying does not flip the toggle off",
        !ed.getComponent(ship, Cloak.class).isEnabled());
  }
}
