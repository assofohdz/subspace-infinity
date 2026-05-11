// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.actions.CapBump;
import infinity.es.ship.actions.CapField;
import infinity.es.ship.actions.Intent;
import infinity.es.ship.actions.RocketBuffIntent;
import infinity.settings.ConfigRegistrySystem;
import org.junit.Test;

/**
 * Pins the cap-bump intent drain owned by {@link ShipSpawnSystem}
 * (BACKLOG C2a ship-body group — closes the prize-applier multi-writer
 * violations on {@link Energy} / {@link Recharge} / {@link Rotation} /
 * {@link Thrust} / {@link Speed}).
 *
 * <p>Each upgrade-prize applier ({@code EnergyPrizeApplier},
 * {@code RechargePrizeApplier}, {@code RotationPrizeApplier},
 * {@code ThrusterPrizeApplier}, {@code TopSpeedPrizeApplier}) emits
 * {@code Intent.of(ship, new CapBump(CapField.X, delta))} using the
 * universal {@link Intent} wrapper with target on the wrapper and the
 * unified {@link CapBump} payload. {@code ShipSpawnSystem} runs one
 * {@code FieldFilter}-narrowed EntitySet on {@code Intent.kind ==
 * CapBump.class}, folds same-tick deltas additively per
 * {@code (target, CapField)}, and defers per-field read/clamp/skip-no-
 * op/write to {@link CapField#apply}.
 *
 * <p>This test boots a minimal {@link GameSystemManager} with just
 * {@link EntityData}, {@link ConfigRegistrySystem}, and
 * {@link ShipSpawnSystem} so the drain can be exercised in isolation.
 * It does NOT register {@code PrizeSystem} / the appliers — emitting
 * an {@link Intent}-wrapped payload directly is sufficient to exercise
 * the drain.
 */
public class CapBumpIntentDrainTest {

  // ---- Single bump → cap reaches currentBefore + upgrade ---------------

  @Test
  public void singleEnergyIntent_bumpsCapByDelta() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(500));
      ed.setComponent(ship, new EnergyMax(1700));

      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      systems.update();

      assertEquals(
          "Energy bumped by delta", 600, ed.getComponent(ship, Energy.class).getEnergy());
    } finally {
      stop(systems);
    }
  }

  // ---- Multi-prize same-tick → deltas accumulate additively ------------

  @Test
  public void multipleEnergyIntents_sameTickAccumulate() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(500));
      ed.setComponent(ship, new EnergyMax(1700));

      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      systems.update();

      assertEquals(
          "Three same-tick bumps accumulate to 3× delta",
          800,
          ed.getComponent(ship, Energy.class).getEnergy());
    } finally {
      stop(systems);
    }
  }

  // ---- Max-cap clamp: bump clamped at EnergyMax ------------------------

  @Test
  public void energyBumpClampsAtMax() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(1650));
      ed.setComponent(ship, new EnergyMax(1700));

      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      systems.update();

      assertEquals(
          "Bump clamped at EnergyMax (1700), not 1750",
          1700,
          ed.getComponent(ship, Energy.class).getEnergy());
    } finally {
      stop(systems);
    }
  }

  // ---- No-op skip: already at cap → intent drained but no value change -

  @Test
  public void energyAlreadyAtMax_noOpSkip() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(1700));
      ed.setComponent(ship, new EnergyMax(1700));

      final EntityId intentId = emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      systems.update();

      // RaM rule #6: skip no-op replacements (proposed == current).
      // We can't directly observe "did the change event fire" from this
      // test layer, but we can observe that the value stayed at the cap
      // and the intent entity was still consumed (drain ran).
      assertEquals(
          "Energy stays at cap when already at max",
          1700,
          ed.getComponent(ship, Energy.class).getEnergy());
      assertNull(
          "Intent entity removed even when fold was a no-op",
          ed.getComponent(intentId, Intent.class));
    } finally {
      stop(systems);
    }
  }

  // ---- Cross-field independence: Energy + Speed bumps land separately --
  //
  // Pins that the single CapBump drain dispatches per CapField — an Energy
  // bump must not corrupt Speed and vice versa. With the unified payload
  // there is no per-type FieldFilter narrowing to verify; instead, this
  // test pins that the (target, CapField) fold key keeps cross-field
  // bumps independent inside the single drain loop.

  @Test
  public void crossFieldIntents_independentlyUpdateBothFields() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(500));
      ed.setComponent(ship, new EnergyMax(1700));
      ed.setComponent(ship, new Speed(2000));
      ed.setComponent(ship, new SpeedMax(3250));

      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      emit(ed, ship, new CapBump(CapField.SPEED, 250));
      systems.update();

      assertEquals(
          "Energy bumped independently", 600, ed.getComponent(ship, Energy.class).getEnergy());
      assertEquals(
          "Speed bumped independently", 2250, ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      stop(systems);
    }
  }

  // ---- (target, CapField) fold key — per-field accumulation per target -
  //
  // Pins the fold key precisely: Energy+100 and Energy+50 against the same
  // ship accumulate to +150 (folded within the ENERGY field), while a
  // same-tick Speed+200 against the same ship is folded independently (no
  // cross-field bleed). The fresh test the redesign asks for; the prior
  // crossField test only proved "two fields don't collide" at 1 bump each.

  @Test
  public void capBumpAccumulation_perFieldPerTarget() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(500));
      ed.setComponent(ship, new EnergyMax(1700));
      ed.setComponent(ship, new Speed(2000));
      ed.setComponent(ship, new SpeedMax(3250));

      emit(ed, ship, new CapBump(CapField.ENERGY, 100));
      emit(ed, ship, new CapBump(CapField.ENERGY, 50));
      emit(ed, ship, new CapBump(CapField.SPEED, 200));
      systems.update();

      assertEquals(
          "Energy folded across two same-tick same-field bumps (100 + 50 = 150)",
          650,
          ed.getComponent(ship, Energy.class).getEnergy());
      assertEquals(
          "Speed folded independently of Energy (200, not 350)",
          2200,
          ed.getComponent(ship, Speed.class).getSpeed());
    } finally {
      stop(systems);
    }
  }

  // ---- Recharge (double) cross-type smoke -----------------------------

  @Test
  public void singleRechargeIntent_bumpsCapByDelta() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Recharge(40.0));
      ed.setComponent(ship, new RechargeMax(115.0));

      emit(ed, ship, new CapBump(CapField.RECHARGE, 16.6));
      systems.update();

      assertEquals(
          "Recharge bumped by delta",
          56.6,
          ed.getComponent(ship, Recharge.class).getRechargePerSecond(),
          1e-9);
    } finally {
      stop(systems);
    }
  }

  // ---- Rotation (double) cross-type smoke ------------------------------

  @Test
  public void singleRotationIntent_bumpsCapByDelta() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Rotation(3.3));
      ed.setComponent(ship, new RotationMax(4.7));

      emit(ed, ship, new CapBump(CapField.ROTATION, 0.6));
      systems.update();

      assertEquals(
          "Rotation bumped by delta",
          3.9,
          ed.getComponent(ship, Rotation.class).getRadSec(),
          1e-9);
    } finally {
      stop(systems);
    }
  }

  // ---- Thrust cross-type smoke -----------------------------------------

  @Test
  public void singleThrustIntent_bumpsCapByDelta() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Thrust(16));
      ed.setComponent(ship, new ThrustMax(19));

      emit(ed, ship, new CapBump(CapField.THRUST, 2));
      systems.update();

      assertEquals(
          "Thrust bumped by delta", 18, ed.getComponent(ship, Thrust.class).getThrust());
    } finally {
      stop(systems);
    }
  }

  // ---- Rocket buff + cap-bump same-tick: documents preserved limitation -

  /**
   * Pin the pre-C2a rocket-buff interaction: when a thruster prize is
   * picked up during an active rocket buff, the cap-bump intent lands on
   * the *buffed* {@link Thrust} value (because the cap-bump drain runs
   * AFTER the rocket-buff drain). On revert, the snapshot restores the
   * pre-buff value and the prize bump is lost.
   *
   * <p>This test pins the drain-order behaviour for one tick: same-tick
   * activate + thrust-cap-bump → final Thrust is the activate-buffed
   * value + cap-bump delta. The "lost on revert" half of the limitation
   * is enforced by {@code RocketBuffSystem} which is not registered
   * here; the cross-tick race would need a fuller harness to pin (out
   * of scope — limitation is documented in {@code RocketSnapshot}
   * Javadoc).
   */
  @Test
  public void rocketBuffActivate_thenThrustBump_landsOnBuffedValue() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Thrust(16));
      ed.setComponent(ship, new ThrustMax(200));

      // Activate intent → rocket-buff drain sets Thrust=100.
      final EntityId activate = ed.createEntity();
      ed.setComponent(activate, new RocketBuffIntent(ship, 100, 3000));

      // Same-tick thruster prize pickup → cap-bump drain runs AFTER
      // the rocket-buff drain, so delta adds to the buffed value.
      emit(ed, ship, new CapBump(CapField.THRUST, 2));

      systems.update();

      assertEquals(
          "Cap-bump drains AFTER rocket-buff drain — bump lands on buffed Thrust",
          102,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertNotNull(
          "Ship still has the cap-bump-applied Thrust component",
          ed.getComponent(ship, Thrust.class));
    } finally {
      stop(systems);
    }
  }

  // ---- Helpers --------------------------------------------------------

  private static void registerSystems(
      final GameSystemManager systems, final DefaultEntityData ed) {
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();
  }

  private static void stop(final GameSystemManager systems) {
    systems.stop();
    systems.terminate();
  }

  /**
   * Emit an {@link Intent}-wrapped payload on a fresh intent entity —
   * mirrors the prize-applier call shape after the Intent refactor
   * ({@code ed.setComponent(intent, Intent.of(target, payload))}).
   */
  private static EntityId emit(
      final EntityData ed, final EntityId target, final EntityComponent payload) {
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(target, payload));
    return intentId;
  }
}
