// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
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

/** Each ADR-0016 vocabulary input the foundation sources, on representative states. */
public class SituationalInputsFactoryTest {

  private static final double EPS = 1e-9;
  private static final ZoneBotAiConfig CFG = ZoneBotAiConfig.DEFAULTS; // range 20, bRef 500, R 12

  private Blackboard bb;

  @Before
  public void setUp() {
    bb =
        new Blackboard(
            new Pursue(0.5, 1.0),
            new Wander(1, 2, 0.5, 1.0),
            new OrbitTarget(15, 1.0),
            new Evade(0.5, 1.0));
    bb.setSelf(new MoverState(new Vec3d(), new Quatd(), new Vec3d()));
  }

  private NearbyShip target(final double x, final double energyPct, final int bounty) {
    return new NearbyShip(
        new EntityId(9), new Vec3d(x, 0, 0), new Quatd(), new Vec3d(), 2, energyPct, bounty);
  }

  @Test
  public void noTargetIsNeutral() {
    assertEquals(SituationalInputs.NEUTRAL, SituationalInputsFactory.compute(bb, true, CFG));
  }

  @Test
  public void rangeFitPeaksAtOptimalDistance() {
    bb.setTarget(target(20, 0.5, 0)); // dist == engagementRangeUnits
    assertEquals(1.0, SituationalInputsFactory.compute(bb, true, CFG).rangeFit(), EPS);
    bb.setTarget(target(40, 0.5, 0)); // |40-20|/20 = 1.0 → fit 0
    assertEquals(0.0, SituationalInputsFactory.compute(bb, true, CFG).rangeFit(), EPS);
  }

  @Test
  public void energyAdvFavoursHealthierSelf() {
    bb.setEnergy(90, 100); // self 0.9
    bb.setTarget(target(20, 0.1, 0)); // target 0.1 → 0.9-0.1+0.5 = 1.3 → clamp 1.0
    assertEquals(1.0, SituationalInputsFactory.compute(bb, true, CFG).energyAdv(), EPS);
    bb.setEnergy(10, 100); // self 0.1
    bb.setTarget(target(20, 0.9, 0)); // 0.1-0.9+0.5 = -0.3 → clamp 0
    assertEquals(0.0, SituationalInputsFactory.compute(bb, true, CFG).energyAdv(), EPS);
  }

  @Test
  public void rechargeRdyMirrorsWeaponReady() {
    bb.setTarget(target(20, 0.5, 0));
    assertEquals(1.0, SituationalInputsFactory.compute(bb, true, CFG).rechargeRdy(), EPS);
    assertEquals(0.0, SituationalInputsFactory.compute(bb, false, CFG).rechargeRdy(), EPS);
  }

  @Test
  public void supportCountsAlliesInRadius() {
    bb.setTarget(target(20, 0.5, 0));
    final NearbyShip nearAlly = new NearbyShip(new EntityId(1), new Vec3d(3, 0, 0), new Quatd(),
        new Vec3d(), 1, -1, 0);
    final NearbyShip farAlly = new NearbyShip(new EntityId(2), new Vec3d(100, 0, 0), new Quatd(),
        new Vec3d(), 1, -1, 0);
    bb.setPerception(new PerceptionSnapshot(List.of(), List.of(nearAlly, farAlly), List.of()));
    // one ally within R=12 → clamp(1/2) = 0.5
    assertEquals(0.5, SituationalInputsFactory.compute(bb, true, CFG).support(), EPS);
  }

  @Test
  public void bountyPullSaturatesAtReference() {
    bb.setTarget(target(20, 0.5, 500)); // == bountyReference → 1.0
    assertEquals(1.0, SituationalInputsFactory.compute(bb, true, CFG).bountyPull(), EPS);
    bb.setTarget(target(20, 0.5, 125)); // 125/500 = 0.25
    assertEquals(0.25, SituationalInputsFactory.compute(bb, true, CFG).bountyPull(), EPS);
  }

  @Test
  public void losDegradesToClearWithoutNav() {
    bb.setTarget(target(20, 0.5, 0)); // no arenaContext wired → LoS can't be tested → 1.0
    assertTrue(SituationalInputsFactory.compute(bb, true, CFG).los() > 0.0);
  }
}
