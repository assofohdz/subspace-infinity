// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import infinity.BulletLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BulletChange;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletStats;
import org.junit.Test;

/**
 * LEVEL-family applier test for Gun (Subspace canon prize name; Infinity renamed → Bullet
 * post-slice-R1). REFERENCE.md {@code ## PrizeWeight} ({@code Gun (= "Gun Upgrade")}) +
 * per-ship {@code [Ship] InitialGuns} / {@code MaxGuns}.
 */
public class GunPrizeApplierTest {

  private static int countChangeHolders(final EntityData ed) {
    final EntitySet set = ed.getEntities(BulletChange.class, ChangeTarget.class);
    try {
      return set.size();
    } finally {
      set.release();
    }
  }

  @Test
  public void apply_belowCap_emitsBulletChangePlusOne() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_3, 0, 0L, 0));
    ed.setComponent(ship, new BulletCurrentLevel(BulletLevel.LEVEL_1));

    new GunPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    final EntitySet holders = ed.getEntities(BulletChange.class, ChangeTarget.class);
    try {
      assertEquals(1, holders.size());
      final Entity h = holders.iterator().next();
      assertEquals(1, h.get(BulletChange.class).delta());
      assertEquals(ship, h.get(ChangeTarget.class).target());
    } finally {
      holders.release();
    }
  }

  @Test
  public void apply_atCap_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_3, 0, 0L, 0));
    ed.setComponent(ship, new BulletCurrentLevel(BulletLevel.LEVEL_3));

    new GunPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_gunsDisallowed_noChangeEmitted() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    // No BulletStats / BulletStats.max == null → ship not allowed bullets.

    new GunPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }

  @Test
  public void apply_missingCurrentLevel_warnsAndNoops() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_3, 0, 0L, 0));

    new GunPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    assertEquals(0, countChangeHolders(ed));
  }
}
