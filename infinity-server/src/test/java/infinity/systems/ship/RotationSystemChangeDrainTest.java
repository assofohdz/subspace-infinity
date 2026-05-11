// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationChange;
import infinity.es.ship.RotationStats;
import org.junit.Test;

/**
 * Pins the ADR 0001 canonical-writer drain on {@link RotationSystem}
 * for the live {@link Rotation} value. Mirrors
 * {@code EnergySystemChangeDrainTest}'s reference shape — minimal
 * {@link GameSystemManager} wiring; tests emit
 * {@code (ChangeTarget, RotationChange)} holders, tick the system,
 * pin the post-tick {@link Rotation} value.
 *
 * <p>Covers: single delta, multi-source same-tick summing, clamp at
 * {@link RotationStats#max()}, no-op skip.
 */
public class RotationSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(RotationSystem.class, new RotationSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final double rotation, final RotationStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Rotation(rotation));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final double delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new RotationChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsLiveRotation() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 3.3, new RotationStats(4.7, 0.6));
      final EntityId h = emit(f.ed, ship, 0.6);
      f.systems.update();
      assertEquals(3.9, f.ed.getComponent(ship, Rotation.class).getRadSec(), 1e-9);
      assertNull(
          "One-shot holder destroyed", f.ed.getComponent(h, RotationChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 1.0, new RotationStats(10.0, 0.5));
      emit(f.ed, ship, 0.5);
      emit(f.ed, ship, 0.5);
      emit(f.ed, ship, 0.5);
      f.systems.update();
      assertEquals(
          "Three same-tick bumps fold to 3 × 0.5",
          2.5,
          f.ed.getComponent(ship, Rotation.class).getRadSec(),
          1e-9);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 4.5, new RotationStats(5.0, 1.0));
      emit(f.ed, ship, 10.0);
      f.systems.update();
      assertEquals(
          "Bump clamped at RotationStats.max (5.0)",
          5.0,
          f.ed.getComponent(ship, Rotation.class).getRadSec(),
          1e-9);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 5.0, new RotationStats(5.0, 1.0));
      final EntityId h = emit(f.ed, ship, 10.0);
      f.systems.update();
      assertEquals(
          "Rotation stays at max", 5.0,
          f.ed.getComponent(ship, Rotation.class).getRadSec(), 1e-9);
      assertNull(
          "Holder destroyed even on no-op", f.ed.getComponent(h, RotationChange.class));
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
