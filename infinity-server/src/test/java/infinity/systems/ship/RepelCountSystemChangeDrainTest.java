// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelChange;
import infinity.es.ship.actions.RepelStats;
import org.junit.Test;

/** Pins {@link RepelCountSystem}'s {@link RepelChange} drain — single delta, multi-emit summing, clamp at max, clamp at zero, no-op. */
public class RepelCountSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(RepelCountSystem.class, new RepelCountSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(final EntityData ed, final int count, final int max) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Repel(count));
    ed.setComponent(ship, new RepelStats(max));
    return ship;
  }

  private static void emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new RepelChange(delta));
  }

  @Test
  public void singleDelta_bumpsCount() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 10, 20);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(11, f.ed.getComponent(ship, Repel.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 10, 20);
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 2);
      f.systems.update();
      assertEquals(13, f.ed.getComponent(ship, Repel.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 18, 20);
      emit(f.ed, ship, 10);
      f.systems.update();
      assertEquals(20, f.ed.getComponent(ship, Repel.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void negativeDeltaClampsAtZero() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 2, 20);
      emit(f.ed, ship, -5);
      f.systems.update();
      assertEquals(0, f.ed.getComponent(ship, Repel.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void prizePlusConsumeSameTick_netDeltaApplied() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 5, 20);
      // Prize +1 + consume -1 in the same tick: net 0, but writer still
      // exercises both the add fold and the no-op skip.
      emit(f.ed, ship, 1);
      emit(f.ed, ship, -1);
      f.systems.update();
      assertEquals(5, f.ed.getComponent(ship, Repel.class).getCount());
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
