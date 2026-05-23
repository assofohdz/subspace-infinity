// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.bt;

import static org.junit.Assert.assertEquals;

import infinity.ai.brain.Blackboard;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import org.junit.Test;

public class AlwaysSucceedTest {

  private final Blackboard blackboard =
      new Blackboard(
          new Pursue(0.5, 1.0),
          new Wander(1, 2, 0.5, 1.0),
          new OrbitTarget(15, 1.0),
          new Evade(0.5, 1.0));

  @Test
  public void returnsSuccess() {
    assertEquals(Status.SUCCESS, new AlwaysSucceed().tick(blackboard));
  }

  @Test
  public void selectorFallbackSwallowsFailureBeforeIt() {
    // Verifies the canonical usage: Selector(failingChild, AlwaysSucceed) → SUCCESS.
    final Selector sel =
        new Selector(bb -> Status.FAILURE, bb -> Status.FAILURE, new AlwaysSucceed());
    assertEquals(Status.SUCCESS, sel.tick(blackboard));
  }
}
