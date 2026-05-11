// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
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
import infinity.es.ship.actions.EnergyCapBump;
import infinity.es.ship.actions.Intent;
import infinity.es.ship.actions.RechargeCapBump;
import infinity.es.ship.actions.RocketBuffIntent;
import infinity.es.ship.actions.RotationCapBump;
import infinity.es.ship.actions.SpeedCapBump;
import infinity.es.ship.actions.ThrustCapBump;
import infinity.settings.ConfigRegistrySystem;
import org.junit.Test;

/**
 * Pins the cap-bump intent drains owned by {@link ShipSpawnSystem}
 * (BACKLOG C2a ship-body group — closes the prize-applier multi-writer
 * violations on {@link Energy} / {@link Recharge} / {@link Rotation} /
 * {@link Thrust} / {@link Speed}).
 *
 * <p>Each upgrade-prize applier ({@code EnergyPrizeApplier},
 * {@code RechargePrizeApplier}, {@code RotationPrizeApplier},
 * {@code ThrusterPrizeApplier}, {@code TopSpeedPrizeApplier}) emits
 * {@code Intent.of(new *CapBump(...))} using the universal {@link Intent}
 * wrapper; {@code ShipSpawnSystem} runs a {@code FieldFilter}-narrowed
 * EntitySet per payload type, folds same-tick deltas additively, clamps
 * at the relevant {@code *Max}, and skips no-op writes per RaM rule #6.
 *
 * <p>This test boots a minimal {@link GameSystemManager} with just
 * {@link EntityData}, {@link ConfigRegistrySystem}, and
 * {@link ShipSpawnSystem} so the drains can be exercised in isolation.
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

      emit(ed, new EnergyCapBump(ship, 100));
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

      emit(ed, new EnergyCapBump(ship, 100));
      emit(ed, new EnergyCapBump(ship, 100));
      emit(ed, new EnergyCapBump(ship, 100));
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

      emit(ed, new EnergyCapBump(ship, 100));
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

      final EntityId intentId = emit(ed, new EnergyCapBump(ship, 100));
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

  // ---- Cross-type independence: Energy + Speed bumps land separately ---
  //
  // Also exercises the FieldFilter narrowing — the Energy drain must see
  // only EnergyCapBump intents and ignore SpeedCapBump intents on the
  // same Intent.class wire, and vice versa. If FieldFilter narrowing is
  // broken (e.g. wrong field name), one drain would catch the other
  // type's intent and the cast would ClassCastException.

  @Test
  public void crossTypeIntents_independentlyUpdateBothFields() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed);
    try {
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Energy(500));
      ed.setComponent(ship, new EnergyMax(1700));
      ed.setComponent(ship, new Speed(2000));
      ed.setComponent(ship, new SpeedMax(3250));

      emit(ed, new EnergyCapBump(ship, 100));
      emit(ed, new SpeedCapBump(ship, 250));
      systems.update();

      assertEquals(
          "Energy bumped independently", 600, ed.getComponent(ship, Energy.class).getEnergy());
      assertEquals(
          "Speed bumped independently", 2250, ed.getComponent(ship, Speed.class).getSpeed());
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

      emit(ed, new RechargeCapBump(ship, 16.6));
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

      emit(ed, new RotationCapBump(ship, 0.6));
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

      emit(ed, new ThrustCapBump(ship, 2));
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
   * Javadoc + {@link ThrustCapBump} Javadoc).
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
      emit(ed, new ThrustCapBump(ship, 2));

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
   * mirrors the {@code prize-applier} call shape after the C2a
   * migration ({@code ed.setComponent(intent, Intent.of(payload))}).
   */
  private static EntityId emit(final EntityData ed, final EntityComponent payload) {
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(payload));
    return intentId;
  }
}
