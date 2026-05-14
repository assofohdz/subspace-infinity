// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.BombLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BombChange;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombStats;
import org.junit.Test;

/**
 * LEVEL-family applier test for Bomb. REFERENCE.md {@code ## PrizeWeight} ({@code Bomb}) +
 * per-ship {@code [Ship] InitialBombs} / {@code MaxBombs}.
 */
public class BombPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(BombChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowCap_emitsBombChangePlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BombStats(BombLevel.BOMB_3, 0, 0L, 0, 0));
    ed.setComponent(ship, new BombCurrentLevel(BombLevel.BOMB_1));

    new BombPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(BombChange.class, ChangeTarget.class);
    try {
      assertEquals("Applier emits exactly one Change holder", 1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(BombChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BombStats(BombLevel.BOMB_3, 0, 0L, 0, 0));
    ed.setComponent(ship, new BombCurrentLevel(BombLevel.BOMB_3));

    new BombPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Already at cap → no holder", 0, countChangeHolders(ed));
  }

  @Test
  public void apply_bombsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No BombStats / BombStats.max == null → ship not allowed bombs.

    new BombPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Ship not allowed bombs → no holder", 0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingCurrentLevel_warnsAndNoops() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // BombStats with a max but no BombCurrentLevel: spawn-projection invariant broken.
    ed.setComponent(ship, new BombStats(BombLevel.BOMB_3, 0, 0L, 0, 0));

    new BombPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals("Missing BombCurrentLevel → applier logs warn + no-ops",
        0, countChangeHolders(ed));
  }
}
