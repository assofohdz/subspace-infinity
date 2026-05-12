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
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakActiveChange;
import infinity.es.ship.toggles.CloakStats;
import org.junit.Test;

/**
 * Status-family applier test for Cloak. Subspace tri-state behaviour
 * (REFERENCE.md "Ship abilities"):
 * <ul>
 *   <li>{@code statusTier 0} (forbidden) → applier no-ops; no Change emitted.
 *   <li>{@code statusTier 1} (acquirable) → applier emits {@link CloakActiveChange}{@code (true)}.
 *   <li>{@code statusTier 2} (start-active) → if {@link CloakActive} is already on (spawn projection
 *       set it), applier short-circuits without emitting.
 * </ul>
 *
 * <p>Stealth / XRadar / AntiWarp appliers follow the same shape; symmetry verified by inspection.
 */
public class CloakPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(CloakActiveChange.class, ChangeTarget.class);
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
    ed.setComponent(ship, new CloakStats(0, 0.0));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Forbidden ship must not produce any Change holders",
        0, countChangeHolders(ed));
    assertNull("Forbidden ship must not gain a CloakActive toggle",
        ed.getComponent(ship, CloakActive.class));
  }

  @Test
  public void apply_statusOne_emitsChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakStats(1, 125.0));
    ed.setComponent(ship, new CloakActive(false));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Acquirable ship must produce exactly one Change holder",
        1, countChangeHolders(ed));
  }

  @Test
  public void apply_statusTwoAlreadyActive_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new CloakStats(2, 125.0));
    ed.setComponent(ship, new CloakActive(true));

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Re-pickup on already-active ship is a no-op (no Change emitted)",
        0, countChangeHolders(ed));
    assertNotNull("CloakActive remains on",
        ed.getComponent(ship, CloakActive.class));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No CloakStats — capability not authored on this ship.

    new CloakPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Ship without CloakStats must not produce a Change holder",
        0, countChangeHolders(ed));
  }
}
