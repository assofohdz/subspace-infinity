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
import infinity.es.ship.weapons.MineChange;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;
import org.junit.Test;

/** Pins {@link MineSystem}'s {@link MineChange} drain — single delta, multi-emit summing, clamp at max, no-op. */
public class MineSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(MineSystem.class, new MineSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final BombLevel level, final MineStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new MineCurrentLevel(level));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new MineChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsLevel() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_1, new MineStats(BombLevel.BOMB_4, 50, 500L, 0));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_2, f.ed.getComponent(ship, MineCurrentLevel.class).getLevel());
      assertNull("One-shot holder destroyed", f.ed.getComponent(h, MineChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_1, new MineStats(BombLevel.BOMB_4, 50, 500L, 0));
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_3, f.ed.getComponent(ship, MineCurrentLevel.class).getLevel());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_2, new MineStats(BombLevel.BOMB_3, 50, 500L, 0));
      emit(f.ed, ship, 5);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_3, f.ed.getComponent(ship, MineCurrentLevel.class).getLevel());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShip(f.ed, BombLevel.BOMB_4, new MineStats(BombLevel.BOMB_4, 50, 500L, 0));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          BombLevel.BOMB_4, f.ed.getComponent(ship, MineCurrentLevel.class).getLevel());
      assertNull("Holder destroyed even on no-op", f.ed.getComponent(h, MineChange.class));
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
