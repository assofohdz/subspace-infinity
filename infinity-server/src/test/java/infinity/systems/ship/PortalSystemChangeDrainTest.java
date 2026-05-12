// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalChange;
import infinity.es.ship.actions.PortalStats;
import org.junit.Test;

/** Pins {@link PortalSystem}'s {@link PortalChange} drain — single delta, multi-emit summing, clamp at max, clamp at zero, no-op. */
public class PortalSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(PortalSystem.class, new PortalSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(final EntityData ed, final int count, final int max) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Portal(count));
    ed.setComponent(ship, new PortalStats(max));
    return ship;
  }

  private static void emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new PortalChange(delta));
  }

  @Test
  public void singleDelta_bumpsCount() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 0, 2);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(1, f.ed.getComponent(ship, Portal.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 0, 5);
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(2, f.ed.getComponent(ship, Portal.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1, 2);
      emit(f.ed, ship, 5);
      f.systems.update();
      assertEquals(2, f.ed.getComponent(ship, Portal.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void negativeDeltaClampsAtZero() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1, 2);
      emit(f.ed, ship, -5);
      f.systems.update();
      assertEquals(0, f.ed.getComponent(ship, Portal.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 2, 2);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(2, f.ed.getComponent(ship, Portal.class).getCount());
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
