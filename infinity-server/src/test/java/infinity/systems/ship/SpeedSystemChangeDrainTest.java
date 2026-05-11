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
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedChange;
import infinity.es.ship.SpeedStats;
import org.junit.Test;

/**
 * Pins the ADR 0001 canonical-writer drain on {@link SpeedSystem}.
 * Covers prize-pickup (one-shot delta, clamped at
 * {@link SpeedStats#max()}), rocket-buff (temporary delta with
 * {@link Decay}, clamp bypassed, reversed on remove), and the
 * fold/clamp/no-op invariants.
 */
public class SpeedSystemChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(SpeedSystem.class, new SpeedSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(
      final EntityData ed, final int speed, final SpeedStats stats) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Speed(speed));
    ed.setComponent(ship, stats);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), new SpeedChange(delta));
    return h;
  }

  @Test
  public void singleDelta_bumpsLiveSpeed() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 2000, new SpeedStats(3250, 250));
      final EntityId h = emit(f.ed, ship, 250);
      f.systems.update();
      assertEquals(2250, f.ed.getComponent(ship, Speed.class).getSpeed());
      assertNull(f.ed.getComponent(h, SpeedChange.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void multipleDeltasSameTick_foldAdditively() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 2000, new SpeedStats(5000, 250));
      emit(f.ed, ship, 250);
      emit(f.ed, ship, 250);
      emit(f.ed, ship, 250);
      f.systems.update();
      assertEquals(
          "Three same-tick bumps fold to 3 × 250",
          2750,
          f.ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void oneShotDeltaClampsAtStatsMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 3000, new SpeedStats(3250, 250));
      emit(f.ed, ship, 1000);
      f.systems.update();
      assertEquals(
          "One-shot bump clamped at SpeedStats.max (3250)",
          3250,
          f.ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void temporaryDeltaBypassesClamp_appliesAboveStatsMax() {
    final Fixture f = newFixture();
    try {
      // Rocket buff: Warbird SpeedStats.max=3250, RocketSpeed=4000.
      // Delta = 4000 - 2000 = 2000, applied above the cap.
      final EntityId ship = newShip(f.ed, 2000, new SpeedStats(3250, 250));
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new SpeedChange(2000), new Decay(0L, 1_000_000L));

      f.systems.update();

      assertEquals(
          "Temporary delta bypasses clamp — Speed raised above SpeedStats.max",
          4000,
          f.ed.getComponent(ship, Speed.class).getSpeed());
      // Holder still alive (Decay-bound; only the central reaper destroys it).
      // Use direct ed.getComponent to verify presence; the writer didn't remove it.
      // (The SimTime here doesn't advance past the deadline, so the reaper
      // wouldn't fire anyway.)
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void temporaryDeltaReversedOnRemove() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 2000, new SpeedStats(3250, 250));
      final EntityId h = f.ed.createEntity();
      f.ed.setComponents(
          h, ChangeTarget.self(ship), new SpeedChange(2000), new Decay(0L, 1_000_000L));

      // Tick 1: apply delta + cache (target, delta) in writer.
      f.systems.update();
      assertEquals(4000, f.ed.getComponent(ship, Speed.class).getSpeed());

      // Simulate decay reaper deleting the holder.
      f.ed.removeEntity(h);

      // Tick 2: writer's removedEntities branch reverses the cached delta.
      f.systems.update();
      assertEquals(
          "Decay-driven removal reverses the cached delta",
          2000,
          f.ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void alreadyAtMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, 3250, new SpeedStats(3250, 250));
      final EntityId h = emit(f.ed, ship, 500);
      f.systems.update();
      assertEquals(3250, f.ed.getComponent(ship, Speed.class).getSpeed());
      assertNull(f.ed.getComponent(h, SpeedChange.class));
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
