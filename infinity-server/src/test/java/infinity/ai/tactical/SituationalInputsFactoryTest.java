// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import infinity.ai.brain.Blackboard;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import infinity.config.ZoneBotAiConfig;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Each ADR-0016 vocabulary input the foundation + assassinate source, on representative states. */
public class SituationalInputsFactoryTest {

  private static final double EPS = 1e-9;
  // range 20, bRef 500, R 12, isoRef 30, threatRef 2
  private static final ZoneBotAiConfig CFG = ZoneBotAiConfig.DEFAULTS;
  private static final OwnBotState READY = new OwnBotState(true, false);

  private Blackboard bb;

  @Before
  public void setUp() {
    bb =
        new Blackboard(
            new Pursue(0.5, 1.0),
            new Wander(1, 2, 0.5, 1.0),
            new OrbitTarget(15, 1.0),
            new Evade(0.5, 1.0));
    bb.setSelf(new MoverSnapshot(new Vec3d(), new Quatd(), new Vec3d()));
  }

  private NearbyShip ship(final int id, final double x, final int freq, final double e, final int b) {
    return new NearbyShip(new EntityId(id), new Vec3d(x, 0, 0), new Quatd(), new Vec3d(), freq, e, b);
  }

  @Test
  public void noTargetIsNeutral() {
    assertEquals(SituationalInputs.NEUTRAL, SituationalInputsFactory.compute(bb, READY, CFG));
  }

  @Test
  public void rangeFitPeaksAtOptimalDistance() {
    bb.setTarget(ship(9, 20, 2, 0.5, 0)); // dist == engagementRangeUnits
    assertEquals(1.0, SituationalInputsFactory.compute(bb, READY, CFG).get("range_fit"), EPS);
    bb.setTarget(ship(9, 40, 2, 0.5, 0)); // |40-20|/20 = 1.0 → fit 0
    assertEquals(0.0, SituationalInputsFactory.compute(bb, READY, CFG).get("range_fit"), EPS);
  }

  @Test
  public void energyAdvFavoursHealthierSelf() {
    bb.setEnergy(90, 100); // self 0.9
    bb.setTarget(ship(9, 20, 2, 0.1, 0)); // 0.9-0.1+0.5 = 1.3 → clamp 1.0
    assertEquals(1.0, SituationalInputsFactory.compute(bb, READY, CFG).get("energy_adv"), EPS);
    bb.setEnergy(10, 100); // self 0.1
    bb.setTarget(ship(9, 20, 2, 0.9, 0)); // 0.1-0.9+0.5 = -0.3 → clamp 0
    assertEquals(0.0, SituationalInputsFactory.compute(bb, READY, CFG).get("energy_adv"), EPS);
  }

  @Test
  public void rechargeRdyMirrorsWeaponReady() {
    bb.setTarget(ship(9, 20, 2, 0.5, 0));
    assertEquals(1.0, SituationalInputsFactory.compute(bb, READY, CFG).get("recharge_rdy"), EPS);
    final OwnBotState cooling = new OwnBotState(false, false);
    assertEquals(0.0, SituationalInputsFactory.compute(bb, cooling, CFG).get("recharge_rdy"), EPS);
  }

  @Test
  public void concealmentMirrorsOwnState() {
    bb.setTarget(ship(9, 20, 2, 0.5, 0));
    assertEquals(0.0, SituationalInputsFactory.compute(bb, READY, CFG).get("concealment"), EPS);
    final OwnBotState cloaked = new OwnBotState(true, true);
    assertEquals(1.0, SituationalInputsFactory.compute(bb, cloaked, CFG).get("concealment"), EPS);
  }

  @Test
  public void supportCountsAlliesInRadius() {
    bb.setTarget(ship(9, 20, 2, 0.5, 0));
    bb.setPerception(
        new PerceptionSnapshot(
            List.of(),
            List.of(ship(1, 3, 1, -1, 0), ship(2, 100, 1, -1, 0)), // one within R=12
            List.of()));
    assertEquals(0.5, SituationalInputsFactory.compute(bb, READY, CFG).get("support"), EPS);
  }

  @Test
  public void bountyPullSaturatesAtReference() {
    bb.setTarget(ship(9, 20, 2, 0.5, 500)); // == bountyReference → 1.0
    assertEquals(1.0, SituationalInputsFactory.compute(bb, READY, CFG).get("bounty_pull"), EPS);
    bb.setTarget(ship(9, 20, 2, 0.5, 125)); // 125/500 = 0.25
    assertEquals(0.25, SituationalInputsFactory.compute(bb, READY, CFG).get("bounty_pull"), EPS);
  }

  @Test
  public void isolationHighWhenTargetAlone() {
    final NearbyShip target = ship(9, 20, 2, 0.5, 0);
    bb.setTarget(target);
    // no other threats → fully isolated
    bb.setPerception(new PerceptionSnapshot(List.of(target), List.of(), List.of()));
    assertEquals(1.0, SituationalInputsFactory.compute(bb, READY, CFG).get("isolation"), EPS);
    // a teammate 15u from the target, isoRef 30 → 0.5
    bb.setPerception(
        new PerceptionSnapshot(List.of(target, ship(3, 35, 2, 0.5, 0)), List.of(), List.of()));
    assertEquals(0.5, SituationalInputsFactory.compute(bb, READY, CFG).get("isolation"), EPS);
  }

  @Test
  public void approachSafetyDegradesToSafeWithoutThreatField() {
    bb.setTarget(ship(9, 20, 2, 0.5, 0)); // no arenaContext → no threat field → 1.0
    assertEquals(1.0, SituationalInputsFactory.compute(bb, READY, CFG).get("approach_safety"), EPS);
  }

  @Test
  public void losDegradesToClearWithoutNav() {
    bb.setTarget(ship(9, 20, 2, 0.5, 0)); // no arenaContext → LoS can't be tested → 1.0
    assertTrue(SituationalInputsFactory.compute(bb, READY, CFG).get("los") > 0.0);
  }
}
