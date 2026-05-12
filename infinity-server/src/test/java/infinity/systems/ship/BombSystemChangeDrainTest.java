// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.BombLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BombChange;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombStats;
import org.junit.Test;

/** Pins {@link BombSystem}'s {@link BombChange} drain — single delta, multi-emit summing, clamp at max, no-op. */
public class BombSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(BombSystem.class, new BombSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final BombLevel level, final BombStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new BombCurrentLevel(level));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new BombChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsLevel() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_1, new BombStats(BombLevel.BOMB_4, 10, 100L, 2000, 400));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_2, f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
      assertNull("One-shot holder destroyed", f.ed.getComponent(h, BombChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_1, new BombStats(BombLevel.BOMB_4, 10, 100L, 2000, 400));
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_3, f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_2, new BombStats(BombLevel.BOMB_3, 10, 100L, 2000, 400));
      emit(f.ed, ship, 5);
      f.systems.update();
      assertEquals(
          "Bump clamped at BombStats.max (BOMB_3)",
          BombLevel.BOMB_3,
          f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_4, new BombStats(BombLevel.BOMB_4, 10, 100L, 2000, 400));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_4, f.ed.getComponent(ship, BombCurrentLevel.class).getLevel());
      assertNull("Holder destroyed even on no-op", f.ed.getComponent(h, BombChange.class));
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
