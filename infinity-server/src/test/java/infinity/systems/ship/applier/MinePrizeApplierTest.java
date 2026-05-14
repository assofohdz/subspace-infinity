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
import infinity.es.ship.weapons.MineChange;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;
import org.junit.Test;

/**
 * LEVEL-family applier test for Mine. Mines reuse {@link BombLevel} (Subspace tradition).
 * Wired only as a leaf of {@code BOMB} / {@code ALLWEAPONS} composites — REFERENCE.md
 * {@code ## PrizeWeight} {@code Bomb (= "Bomb Upgrade")} bumps mine level too.
 */
public class MinePrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(MineChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowCap_emitsMineChangePlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new MineStats(BombLevel.BOMB_3, 0, 0L, 0));
    ed.setComponent(ship, new MineCurrentLevel(BombLevel.BOMB_1));

    new MinePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(MineChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(MineChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new MineStats(BombLevel.BOMB_3, 0, 0L, 0));
    ed.setComponent(ship, new MineCurrentLevel(BombLevel.BOMB_3));

    new MinePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_minesDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No MineStats / MineStats.max == null.

    new MinePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingCurrentLevel_warnsAndNoops() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new MineStats(BombLevel.BOMB_3, 0, 0L, 0));

    new MinePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
