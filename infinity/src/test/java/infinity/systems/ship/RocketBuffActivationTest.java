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
import infinity.es.ship.actions.RocketSnapshot;
import org.junit.Test;

/**
 * Pins the rocket-buff lifecycle for Slice 2: when a buff entity (with
 * {@link RocketBuff} + {@link Parent} + {@link RocketSnapshot} +
 * {@link Decay}) appears in the world, {@link RocketBuffSystem} stamps
 * {@link RocketActive} on the parent ship; when that buff entity is
 * removed (the canonical decay reaper deletes it at deadline), the
 * system reverts the ship's {@link Thrust} / {@link Speed} from the
 * snapshot and strips {@link RocketActive}.
 *
 * <p>This test simulates the decay reaper by calling
 * {@code ed.removeEntity(buff)} directly — exercising the same
 * {@code getRemovedEntities} seam {@link RocketBuffSystem} listens on,
 * without needing to register the actual decay system or advance
 * SimTime past a deadline.
 */
public class RocketBuffActivationTest {

  @Test
  public void buffEntity_addedThenRemoved_swapsThenRevertsShip() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(RocketBuffSystem.class, new RocketBuffSystem());
    systems.initialize();
    systems.start();

    try {
      // Pre-buff ship state: caller (ConsumableSystem) has already swapped
      // Thrust/Speed to the rocket-active values; we just stamp those
      // overrides as the visible component values.
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Thrust(/* rocket-active override */ 100));
      ed.setComponent(ship, new Speed(/* rocket-active override */ 3000));

      // Create the buff entity (mirror of GameEntities.createRocketBuff). The
      // snapshot carries the ship's PRE-buff Thrust/Speed for revert.
      final long createdNs = 0L;
      final long deadlineNs = 100_000_000L; // 100 ms from epoch — irrelevant
                                            // since we delete the entity by
                                            // hand below.
      final int originalThrust = 16;
      final int originalSpeed = 2000;
      final EntityId buff = ed.createEntity();
      ed.setComponents(
          buff,
          new RocketBuff(),
          new Parent(ship),
          new RocketSnapshot(originalThrust, originalSpeed),
          new Decay(createdNs, deadlineNs));

      // Tick 1: RocketBuffSystem.added → installs RocketActive on ship.
      systems.update();
      final RocketActive active = ed.getComponent(ship, RocketActive.class);
      assertNotNull("RocketActive installed on ship after buff creation", active);
      // Ship's Thrust/Speed remain at the rocket-active overrides set by
      // ConsumableSystem (this test stamped them up front).
      assertEquals(
          "Ship Thrust still at rocket-active override",
          100,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Ship Speed still at rocket-active override",
          3000,
          ed.getComponent(ship, Speed.class).getSpeed());

      // Simulate decay-reaper deleting the buff entity at deadline.
      ed.removeEntity(buff);

      // Tick 2: RocketBuffSystem.removed → reverts Thrust/Speed from
      // snapshot + strips RocketActive.
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
