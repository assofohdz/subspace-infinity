// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustChange;
import infinity.es.ship.ThrustStats;
import org.junit.Test;

/**
 * Pins the ADR 0001 canonical-writer drain on {@link ThrustSystem}.
 * Mirrors {@link SpeedSystemChangeDrainTest}'s shape — Thrust is the
 * same Continuous-half pattern as Speed.
 */
public class ThrustSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(ThrustSystem.class, new ThrustSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final int thrust, final ThrustStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Thrust(thrust));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new ThrustChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsLiveThrust() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 16, new ThrustStats(19, 2));
      final EntityId h = emit(f.ed, ship, 2);
      f.systems.update();
      assertEquals(18, f.ed.getComponent(ship, Thrust.class).getThrust());
      assertNull(f.ed.getComponent(h, ThrustChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 10, new ThrustStats(50, 2));
      emit(f.ed, ship, 2);
      emit(f.ed, ship, 2);
      emit(f.ed, ship, 2);
      f.systems.update();
      assertEquals(
          "Three same-tick bumps fold to 3 × 2",
          16,
          f.ed.getComponent(ship, Thrust.class).getThrust());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void oneShotDeltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 17, new ThrustStats(19, 2));
      emit(f.ed, ship, 10);
      f.systems.update();
      assertEquals(
          "Bump clamped at ThrustStats.max (19), not 27",
          19,
          f.ed.getComponent(ship, Thrust.class).getThrust());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void temporaryDeltaBypassesClamp_appliesAboveStatsMax() {
    final Fixture f = newFixture();
    try {
      // Rocket buff: Warbird ThrustStats.max=19, RocketThrust=100.
      // Delta = 100 - 16 = 84, applied above the cap.
      final EntityId ship = newShip(f.ed, 16, new ThrustStats(19, 2));
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new ThrustChange(84), new Decay(0L, 1_000_000L));

      f.systems.update();

      assertEquals(
          "Temporary delta bypasses clamp — Thrust raised above ThrustStats.max",
          100,
          f.ed.getComponent(ship, Thrust.class).getThrust());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void temporaryDeltaReversedOnRemove() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 16, new ThrustStats(19, 2));
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new ThrustChange(84), new Decay(0L, 1_000_000L));

      f.systems.update();
      assertEquals(100, f.ed.getComponent(ship, Thrust.class).getThrust());

      f.ed.removeEntity(h);

      f.systems.update();
      assertEquals(
          "Decay-driven removal reverses the cached delta",
          16,
          f.ed.getComponent(ship, Thrust.class).getThrust());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 19, new ThrustStats(19, 2));
      final EntityId h = emit(f.ed, ship, 10);
      f.systems.update();
      assertEquals(19, f.ed.getComponent(ship, Thrust.class).getThrust());
      assertNull(f.ed.getComponent(h, ThrustChange.class));
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
