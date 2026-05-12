// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.BulletLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BulletChange;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletStats;
import org.junit.Test;

/** Pins {@link BulletSystem}'s {@link BulletChange} drain — single delta, multi-emit summing, clamp at max, no-op. */
public class BulletSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(BulletSystem.class, new BulletSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final BulletLevel level, final BulletStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BulletCurrentLevel(level));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new BulletChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsLevel() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BulletLevel.LEVEL_1, new BulletStats(BulletLevel.LEVEL_4, 10, 100L, 2000));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BulletLevel.LEVEL_2, f.ed.getComponent(ship, BulletCurrentLevel.class).getLevel());
      assertNull("One-shot holder destroyed", f.ed.getComponent(h, BulletChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BulletLevel.LEVEL_1, new BulletStats(BulletLevel.LEVEL_4, 10, 100L, 2000));
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BulletLevel.LEVEL_3, f.ed.getComponent(ship, BulletCurrentLevel.class).getLevel());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BulletLevel.LEVEL_2, new BulletStats(BulletLevel.LEVEL_3, 10, 100L, 2000));
      emit(f.ed, ship, 5);
      f.systems.update();
      assertEquals(
          BulletLevel.LEVEL_3, f.ed.getComponent(ship, BulletCurrentLevel.class).getLevel());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BulletLevel.LEVEL_4, new BulletStats(BulletLevel.LEVEL_4, 10, 100L, 2000));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BulletLevel.LEVEL_4, f.ed.getComponent(ship, BulletCurrentLevel.class).getLevel());
      assertNull("Holder destroyed even on no-op", f.ed.getComponent(h, BulletChange.class));
    } finally {
      f.shutdown();
    }
  }

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;

    private Fixture(final GameSystemManager systems, final DefaultEntityData ed) {
      this.systems = systems;
      this.ed = ed;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
