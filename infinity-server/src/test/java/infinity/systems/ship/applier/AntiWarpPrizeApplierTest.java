// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpActiveChange;
import infinity.es.ship.toggles.AntiwarpStats;
import org.junit.Test;

/**
 * Status-family applier test for AntiWarp — same tri-state shape as {@link CloakPrizeApplierTest}.
 * Subspace canon: REFERENCE.md {@code ## PrizeWeight} ({@code AntiWarp}); per-ship status tri-state
 * mirrors {@code CloakStatus} (REFERENCE.md "Ship abilities").
 */
public class AntiWarpPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(AntiwarpActiveChange.class, ChangeTarget.class);
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
    ed.setComponent(ship, new AntiwarpStats(0, 0.0));

    new AntiWarpPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Forbidden ship must not produce any Change holders",
        0, countChangeHolders(ed));
  }

  @Test
  public void apply_statusOne_emitsChange() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new AntiwarpStats(1, 250.0));
    ed.setComponent(ship, new AntiwarpActive(false));

    new AntiWarpPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Acquirable ship must produce exactly one Change holder",
        1, countChangeHolders(ed));
  }

  @Test
  public void apply_statusTwoAlreadyActive_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new AntiwarpStats(2, 250.0));
    ed.setComponent(ship, new AntiwarpActive(true));

    new AntiWarpPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Re-pickup on already-active ship is a no-op",
        0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingStats_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No AntiwarpStats — capability not authored on this ship.

    new AntiWarpPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Ship without AntiwarpStats must not produce a Change holder",
        0, countChangeHolders(ed));
  }
}
