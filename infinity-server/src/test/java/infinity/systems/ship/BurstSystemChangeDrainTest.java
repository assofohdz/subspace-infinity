// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstChange;
import infinity.es.ship.actions.BurstStats;
import org.junit.Test;

/** Pins {@link BurstSystem}'s {@link BurstChange} drain — single delta, multi-emit summing, clamp at max, no-op. */
public class BurstSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(BurstSystem.class, new BurstSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final int count, final BurstStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Burst(count));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new BurstChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsCount() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1, new BurstStats(5, 3000));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(2, f.ed.getComponent(ship, Burst.class).getCount());
      assertNull("One-shot holder destroyed", f.ed.getComponent(h, BurstChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 0, new BurstStats(5, 3000));
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(3, f.ed.getComponent(ship, Burst.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 3, new BurstStats(5, 3000));
      emit(f.ed, ship, 10);
      f.systems.update();
      assertEquals(5, f.ed.getComponent(ship, Burst.class).getCount());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 5, new BurstStats(5, 3000));
      final EntityId h = emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(5, f.ed.getComponent(ship, Burst.class).getCount());
      assertNull("Holder destroyed even on no-op", f.ed.getComponent(h, BurstChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void burstStatsMaxZero_skipsApply() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 0, new BurstStats(0, 0));
      emit(f.ed, ship, 1);
      f.systems.update();
      assertEquals(
          "BurstStats.max==0 means ship not allowed bursts; no-op",
          0,
          f.ed.getComponent(ship, Burst.class).getCount());
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
