// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import infinity.ai.brain.Blackboard;
import infinity.ai.bt.Status;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import org.junit.Before;
import org.junit.Test;

public class IsGoalTest {

  private Blackboard bb;

  @Before
  public void setUp() {
    bb = new Blackboard(new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0),
        new OrbitTarget(15, 1.0), new Evade(0.5, 1.0));
  }

  @Test
  public void nullGoalIsFailure() {
    assertEquals(Status.FAILURE, new IsGoal(Search.class).tick(bb));
  }

  @Test
  public void exactClassMatchIsSuccess() {
    bb.setCurrentGoal(new Search());
    assertEquals(Status.SUCCESS, new IsGoal(Search.class).tick(bb));
  }

  @Test
  public void differentClassIsFailure() {
    bb.setCurrentGoal(new Engage(new EntityId(7)));
    assertEquals(Status.FAILURE, new IsGoal(Search.class).tick(bb));
  }

  @Test
  public void carriedPayloadDoesNotAffectMatch() {
    bb.setCurrentGoal(new Engage(new EntityId(42)));
    assertEquals(Status.SUCCESS, new IsGoal(Engage.class).tick(bb));
  }
}
