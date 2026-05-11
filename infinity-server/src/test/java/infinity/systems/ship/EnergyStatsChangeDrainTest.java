// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import org.junit.Test;

/**
 * Pins the ADR 0001 canonical-writer drain on
 * {@link EnergyStatsSystem} for the bundled {@link EnergyStats}
 * record. Follows the {@code CanonicalWriterDrainTest} reference
 * shape: minimal {@link GameSystemManager} wiring {@link EntityData}
 * + {@link EnergyStatsSystem}; tests emit one or more
 * {@code (ChangeTarget, EnergyStatsChange)} holders, tick the system,
 * and pin the post-tick {@link EnergyStats} value.
 *
 * <p>Covers:
 * <ul>
 *   <li>Single-field bump (ENERGY-style cap upgrade).
 *   <li>Multi-prize same-tick summing on the same field.
 *   <li>Cross-field independence (cap + recharge in one tick).
 *   <li>hardMax clamp on the {@code max} field.
 *   <li>rechargeMax clamp on the {@code rechargePerSecond} field.
 *   <li>No-op skip when post-fold equals current.
 * </ul>
 */
public class EnergyStatsChangeDrainTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergyStatsSystem.class, new EnergyStatsSystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShipWithStats(
      final EntityData ed, final EnergyStats initial) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, initial);
    return ship;
  }

  private static EntityId emit(
      final EntityData ed, final EntityId target, final EnergyStatsChange payload) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(target), payload);
    return h;
  }

  /** Single-field bump: ENERGY prize raises {@code EnergyStats.max}. */
  @Test
  public void energyPrizeBump_raisesMaxField() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShipWithStats(f.ed, new EnergyStats(1000, 1700, 100, 40.0, 115.0, 16.6));
      final EntityId h = emit(f.ed, ship, EnergyStatsChange.ofMax(100));

      f.systems.update();

      final EnergyStats stats = f.ed.getComponent(ship, EnergyStats.class);
      assertEquals("max bumped by delta", 1100, stats.max());
      assertEquals("hardMax untouched", 1700, stats.hardMax());
      assertEquals("upgrade untouched", 100, stats.upgrade());
      assertEquals("rechargePerSecond untouched", 40.0, stats.rechargePerSecond(), 1e-9);
      assertNull(
          "Holder destroyed by canonical writer",
          f.ed.getComponent(h, EnergyStatsChange.class));
    } finally {
      f.shutdown();
    }
  }

  /** Multi-prize same-tick — same field accumulates additively. */
  @Test
  public void multipleSameFieldBumps_sameTickAccumulate() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShipWithStats(f.ed, new EnergyStats(1000, 1700, 100, 0.0, 200.0, 0.0));
      emit(f.ed, ship, EnergyStatsChange.ofMax(100));
      emit(f.ed, ship, EnergyStatsChange.ofMax(100));
      emit(f.ed, ship, EnergyStatsChange.ofMax(100));

      f.systems.update();

      assertEquals(
          "Three same-field bumps accumulate to +300",
          1300,
          f.ed.getComponent(ship, EnergyStats.class).max());
    } finally {
      f.shutdown();
    }
  }

  /** Cross-field bumps land on the right fields independently. */
  @Test
  public void crossFieldBumps_landOnRespectiveFields() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShipWithStats(f.ed, new EnergyStats(1000, 1700, 100, 40.0, 115.0, 16.6));
      emit(f.ed, ship, EnergyStatsChange.ofMax(50));
      emit(f.ed, ship, EnergyStatsChange.ofRechargePerSecond(10.0));

      f.systems.update();

      final EnergyStats stats = f.ed.getComponent(ship, EnergyStats.class);
      assertEquals("max bumped", 1050, stats.max());
      assertEquals(
          "rechargePerSecond bumped independently",
          50.0,
          stats.rechargePerSecond(),
          1e-9);
    } finally {
      f.shutdown();
    }
  }

  /** {@code max} clamps at {@code hardMax}. */
  @Test
  public void maxBumpClampsAtHardMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShipWithStats(f.ed, new EnergyStats(1650, 1700, 100, 0.0, 200.0, 0.0));
      emit(f.ed, ship, EnergyStatsChange.ofMax(100));

      f.systems.update();

      assertEquals(
          "Bump clamped at hardMax (1700), not 1750",
          1700,
          f.ed.getComponent(ship, EnergyStats.class).max());
    } finally {
      f.shutdown();
    }
  }

  /** {@code rechargePerSecond} clamps at {@code rechargeMax}. */
  @Test
  public void rechargeBumpClampsAtRechargeMax() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShipWithStats(f.ed, new EnergyStats(1000, 1700, 100, 110.0, 115.0, 16.6));
      emit(f.ed, ship, EnergyStatsChange.ofRechargePerSecond(10.0));

      f.systems.update();

      assertEquals(
          "rechargePerSecond clamped at rechargeMax (115.0)",
          115.0,
          f.ed.getComponent(ship, EnergyStats.class).rechargePerSecond(),
          1e-9);
    } finally {
      f.shutdown();
    }
  }

  /** No-op skip — at cap, no value change but holder still reaped. */
  @Test
  public void alreadyAtHardMax_noOpSkip() {
    final Fixture f = newFixture();
    try {
      final EntityId ship =
          newShipWithStats(f.ed, new EnergyStats(1700, 1700, 100, 0.0, 200.0, 0.0));
      final EntityId h = emit(f.ed, ship, EnergyStatsChange.ofMax(100));

      f.systems.update();

      assertEquals(
          "max stays at hardMax when already at cap",
          1700,
          f.ed.getComponent(ship, EnergyStats.class).max());
      assertNull(
          "Holder destroyed even on no-op",
          f.ed.getComponent(h, EnergyStatsChange.class));
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
