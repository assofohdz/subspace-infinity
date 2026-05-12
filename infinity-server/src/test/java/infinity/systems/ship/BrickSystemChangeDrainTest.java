// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickChange;
import infinity.es.ship.actions.BrickStats;
import org.junit.Test;

/** Pins {@link BrickSystem}'s {@link BrickChange} drain — single delta, multi-emit summing, clamp at max, clamp at zero, no-op. */
public class BrickSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(BrickSystem.class, new BrickSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(final EntityData ed, final int count, final int max) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Brick(count));
    ed.setComponent(ship, new BrickStats(max));
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new BrickChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsCount() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1, 5);
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(2, f.ed.getComponent(ship, Brick.class).getCount());
      assertNull("One-shot holder destroyed", f.ed.getComponent(h, BrickChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1, 5);
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(3, f.ed.getComponent(ship, Brick.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 3, 5);
      emit(f.ed, ship, 10);
      f.systems.update();
      assertEquals("Bump clamped at BrickStats.max (5)", 5,
          f.ed.getComponent(ship, Brick.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void negativeDeltaClampsAtZero() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1, 5);
      emit(f.ed, ship, -10);
      f.systems.update();
      assertEquals("Negative delta clamped at 0 (no negative inventory)", 0,
          f.ed.getComponent(ship, Brick.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 5, 5);
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(5, f.ed.getComponent(ship, Brick.class).getCount());
      assertNull("Holder destroyed even on no-op", f.ed.getComponent(h, BrickChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void shipNotAllowedBricks_drainNoOp() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      ed_setBrickStatsZero(f.ed, ship);
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertNull("No Brick component conjured", f.ed.getComponent(ship, Brick.class));
      assertNull("Holder still destroyed", f.ed.getComponent(h, BrickChange.class));
    } finally {
      f.shutdown();
    }
  }

  private static void ed_setBrickStatsZero(final EntityData ed, final EntityId ship) {
    ed.setComponent(ship, new BrickStats(0));
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
