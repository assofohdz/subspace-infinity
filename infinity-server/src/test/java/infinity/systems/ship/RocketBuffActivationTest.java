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
import infinity.es.ChangeTarget;
import infinity.es.Parent;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedChange;
import infinity.es.ship.SpeedStats;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustChange;
import infinity.es.ship.ThrustStats;
import infinity.es.ship.actions.RocketActive;
import infinity.es.ship.actions.RocketBuff;
import infinity.es.ship.actions.RocketSnapshot;
import org.junit.Test;

/**
 * Pins the end-to-end rocket-buff lifecycle under the ADR 0001
 * Change-entity model:
 *
 * <ol>
 *   <li>{@code ConsumableSystem.createRocketBuff} (simulated here by
 *       emitting the two temporary Change holders + the buff entity
 *       directly) raises the ship's {@link Thrust} / {@link Speed} via
 *       Decay-bound {@link ThrustChange} / {@link SpeedChange} holders.
 *   <li>{@link RocketBuffSystem} stamps {@link RocketActive} on the
 *       parent ship when the buff entity appears.
 *   <li>{@link ThrustSystem} / {@link SpeedSystem} drain the activate
 *       Change holders on the same tick, applying the deltas (clamp
 *       bypassed for temporary deltas so rocket overrides can exceed
 *       the ship's hard cap).
 *   <li>When the buff entity + its sibling Change holders are removed
 *       (decay reaper at deadline, simulated here by
 *       {@code ed.removeEntity}), {@code ThrustSystem} /
 *       {@code SpeedSystem} reverse the cached deltas → ship returns
 *       to pre-buff values. {@code RocketBuffSystem} strips
 *       {@link RocketActive}.
 * </ol>
 *
 * <p>Supersedes the pre-ADR {@code RocketBuffIntent} value-replacement
 * shape. The "absolute override" semantic translates to "additive
 * delta computed at fire time"; observable behaviour is identical.
 */
public class RocketBuffActivationTest {

  @Test
  public void buffEntity_addedThenRemoved_swapsThenRevertsShip() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    // RocketBuffSystem manages the RocketActive marker; ThrustSystem +
    // SpeedSystem drain the Change holders. ShipSpawnSystem is NOT
    // registered (we don't need projection here — we seed Thrust/Speed
    // + Stats manually).
    systems.register(RocketBuffSystem.class, new RocketBuffSystem());
    systems.register(ThrustSystem.class, new ThrustSystem());
    systems.register(SpeedSystem.class, new SpeedSystem());
    systems.initialize();
    systems.start();

    try {
      // Pre-buff ship state — original Thrust/Speed values + Stats.
      // Stats.max is intentionally lower than the rocket override so
      // we can pin "temporary deltas bypass the cap clamp."
      final int originalThrust = 16;
      final int originalSpeed = 2000;
      final int rocketActiveThrust = 100;
      final int rocketActiveSpeed = 3000;
      final EntityId ship = ed.createEntity();
      ed.setComponent(ship, new Thrust(originalThrust));
      ed.setComponent(ship, new Speed(originalSpeed));
      // Stats with max=19 / 3250 (Warbird-like) — overrides above
      // would no-op without the temporary-bypass-clamp branch.
      ed.setComponent(ship, new ThrustStats(19, 2));
      ed.setComponent(ship, new SpeedStats(3250, 250));

      // Simulate ConsumableSystem.createRocketBuff (post-ADR-0001 shape):
      // compute additive deltas, emit Decay-bound Change holders, then
      // create the buff entity that drives RocketActive.
      final int deltaThrust = rocketActiveThrust - originalThrust;
      final int deltaSpeed = rocketActiveSpeed - originalSpeed;
      final long createdNs = 0L;
      final long deadlineNs = 100_000_000L; // 100 ms — irrelevant since we
                                            // delete the entities manually.

      final EntityId thrustHolder = ed.createEntity();
      ed.setComponents(
          thrustHolder,
          ChangeTarget.self(ship),
          new ThrustChange(deltaThrust),
          new Decay(createdNs, deadlineNs));

      final EntityId speedHolder = ed.createEntity();
      ed.setComponents(
          speedHolder,
          ChangeTarget.self(ship),
          new SpeedChange(deltaSpeed),
          new Decay(createdNs, deadlineNs));

      // Buff entity (RocketBuff + Parent + RocketSnapshot + Decay) —
      // drives the RocketActive marker via RocketBuffSystem.
      final EntityId buff = ed.createEntity();
      ed.setComponents(
          buff,
          new RocketBuff(),
          new Parent(ship),
          new RocketSnapshot(originalThrust, originalSpeed),
          new Decay(createdNs, deadlineNs));

      // Tick 1: RocketBuffSystem stamps RocketActive; ThrustSystem +
      // SpeedSystem drain the activate Change holders and apply the
      // deltas (clamp bypassed because Decay is present).
      systems.update();

      assertNotNull(
          "RocketActive installed on ship after buff creation",
          ed.getComponent(ship, RocketActive.class));
      assertEquals(
          "Thrust raised to rocket override after activate ThrustChange drains "
              + "(clamp bypassed for temporary delta)",
          rocketActiveThrust,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Speed raised to rocket override after activate SpeedChange drains",
          rocketActiveSpeed,
          ed.getComponent(ship, Speed.class).getSpeed());

      // Simulate decay-reaper deleting the buff entity + Change holders
      // at deadline. In production this is the DecaySystem fired by
      // the central reaper; here we hand-trigger the same seam.
      ed.removeEntity(buff);
      ed.removeEntity(thrustHolder);
      ed.removeEntity(speedHolder);

      // Tick 2: RocketBuffSystem strips RocketActive (buff entity gone);
      // ThrustSystem + SpeedSystem reverse the cached deltas (Change
      // holders gone, TrackedApply cache lookups fire on remove).
      systems.update();

      assertEquals(
          "Ship Thrust reverted to pre-buff value (cached delta reversed)",
          originalThrust,
          ed.getComponent(ship, Thrust.class).getThrust());
      assertEquals(
          "Ship Speed reverted to pre-buff value (cached delta reversed)",
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
