// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import static org.junit.Assert.assertEquals;

import infinity.ai.bt.Status;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import org.junit.Before;
import org.junit.Test;

public class LowEnergyTest {

  private static final double THRESHOLD = 0.6;
  private Blackboard blackboard;
  private LowEnergy condition;

  @Before
  public void setUp() {
    blackboard =
        new Blackboard(
            new Pursue(0.5, 1.0),
            new Wander(1, 2, 0.5, 1.0),
            new OrbitTarget(15, 1.0),
            new Evade(0.5, 1.0));
    condition = new LowEnergy(THRESHOLD);
  }

  @Test
  public void missingEnergyReturnsFailure() {
    // Defaults: currentEnergy = -1, maxEnergy = -1 → no data, can't decide.
    assertEquals(Status.FAILURE, condition.tick(blackboard));
  }

  @Test
  public void energyAboveThresholdReturnsFailure() {
    blackboard.setEnergy(80, 100); // 80% > 60%
    assertEquals(Status.FAILURE, condition.tick(blackboard));
  }

  @Test
  public void energyBelowThresholdReturnsSuccess() {
    blackboard.setEnergy(50, 100); // 50% < 60%
    assertEquals(Status.SUCCESS, condition.tick(blackboard));
  }

  @Test
  public void energyExactlyAtThresholdReturnsFailure() {
    // Strict-less-than: 60 = threshold is NOT low.
    blackboard.setEnergy(60, 100);
    assertEquals(Status.FAILURE, condition.tick(blackboard));
  }

  @Test
  public void zeroMaxReturnsFailure() {
    // Max=0 is a degenerate "no energy stats" — refuse to fire.
    blackboard.setEnergy(0, 0);
    assertEquals(Status.FAILURE, condition.tick(blackboard));
  }
}
