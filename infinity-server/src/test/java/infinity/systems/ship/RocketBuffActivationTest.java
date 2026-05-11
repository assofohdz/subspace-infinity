// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.GameSystemManager;
import infinity.es.Parent;
import infinity.es.ship.Speed;
import infinity.es.ship.Thrust;
import infinity.es.ship.actions.RocketActive;
import infinity.es.ship.actions.RocketBuff;
import infinity.es.ship.actions.RocketBuffIntent;
import infinity.es.ship.actions.RocketSnapshot;
import infinity.settings.ConfigRegistrySystem;
import org.junit.Test;

/**
 * Pins the end-to-end rocket-buff lifecycle post-BACKLOG-C1: when a
 * buff entity (with {@link RocketBuff} + {@link Parent} +
 * {@link RocketSnapshot} + {@link Decay}) appears in the world,
 * {@link RocketBuffSystem} stamps {@link RocketActive} on the parent
 * ship and {@code ConsumableSystem} (simulated here by emitting the
 * activate intent directly) has the {@link ShipSpawnSystem}-owned
 * canonical drain write {@link Thrust} / {@link Speed} to the
 * rocket-active values. When the buff entity is removed (the canonical
 * decay reaper deletes it at deadline), {@code RocketBuffSystem.onBuffRemoved}
 * emits a revert {@link RocketBuffIntent} which the same canonical
 * drain applies — restoring the pre-buff snapshot values and stripping
 * {@link RocketActive}.
 *
 * <p>This test simulates the decay reaper by calling
 * {@code ed.removeEntity(buff)} directly — exercising the same
 * {@code getRemovedEntities} seam {@link RocketBuffSystem} listens on,
 * without needing to register the actual decay system or advance
 * SimTime past a deadline.
 *
 * <p>Replacement-as-Mutation (BACKLOG C1): {@link ShipSpawnSystem} is
 * the canonical writer for {@link Thrust} / {@link Speed} on
 * rocket-buff transitions; {@link RocketBuffSystem} now emits
 * {@link RocketBuffIntent} entities rather than calling
 * {@code setComponent} directly.
 */
public class RocketBuffActivationTest {

  @Test
  public void buffEntity_addedThenRemoved_swapsThenRevertsShip() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    // ConfigRegistrySystem is a hard requireSystem dependency of
    // ShipSpawnSystem.initialize — register even though no arena
    // config is loaded (the drain runs irrespective of arena
    // membership).
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(RocketBuffSystem.class, new RocketBuffSystem());
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.initialize();
    systems.start();

    try {
      // Pre-buff ship state — original Thrust/Speed values.
      final int originalThrust = 16;
      final int originalSpeed = 2000;
      final int rocketActiveThrust = 100;
      final int rocketActiveSpeed = 3000;
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Thrust(originalThrust));
      ed.setComponent(ship, new Speed(originalSpeed));

      // Simulate ConsumableSystem.createRocketBuff (post-C1 shape):
      // emit an activate RocketBuffIntent BEFORE creating the buff
      // entity (mirrors the source order in the production code).
      final EntityId activateIntent = ed.createEntity();
      ed.setComponent(
          activateIntent,
          new RocketBuffIntent(ship, rocketActiveThrust, rocketActiveSpeed));

      // Create the buff entity (mirror of ShipFactory.createRocketBuff).
      // The snapshot carries the ship's PRE-buff Thrust/Speed for revert.
      final long createdNs = 0L;
      final long deadlineNs = 100_000_000L; // 100 ms — irrelevant since we
                                            // delete the entity manually.
      final EntityId buff = ed.createEntity();
      ed.setComponents(
          buff,
          new RocketBuff(),
          new Parent(ship),
          new RocketSnapshot(originalThrust, originalSpeed),
          new Decay(createdNs, deadlineNs));

      // Tick 1: RocketBuffSystem stamps RocketActive on ship;
      // ShipSpawnSystem drains the activate intent and writes
      // Thrust/Speed = rocket-active overrides.
      systems.update();

      assertNotNull(
          "RocketActive installed on ship after buff creation",
          ed.getComponent(ship, RocketActive.class));
      assertEquals(
          "Ship Thrust swapped to rocket-active override after activate intent drains",
          rocketActiveThrust,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Ship Speed swapped to rocket-active override after activate intent drains",
          rocketActiveSpeed,
          ed.getComponent(ship, Speed.class).getSpeed());

      // Simulate decay-reaper deleting the buff entity at deadline.
      ed.removeEntity(buff);

      // Tick 2: RocketBuffSystem.onBuffRemoved emits a revert
      // RocketBuffIntent and strips RocketActive; ShipSpawnSystem
      // drains and writes Thrust/Speed = pre-buff snapshot values.
      systems.update();

      assertEquals(
          "Ship Thrust reverted to pre-buff snapshot",
          originalThrust,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Ship Speed reverted to pre-buff snapshot",
          originalSpeed,
          ed.getComponent(ship, Speed.class).getSpeed());
      assertNull(
          "RocketActive removed from ship after buff expiry",
          ed.getComponent(ship, RocketActive.class));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
