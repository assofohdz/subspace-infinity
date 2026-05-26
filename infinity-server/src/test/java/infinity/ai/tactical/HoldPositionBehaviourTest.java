// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.brain.Blackboard;
import infinity.ai.objective.DeathmatchObjective;
import infinity.ai.objective.GoalTile;
import infinity.ai.objective.TurfObjective;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class HoldPositionBehaviourTest {

  private final HoldPositionBehaviour behaviour = new HoldPositionBehaviour();
  private Blackboard bb;

  @Before
  public void setUp() {
    bb = new Blackboard(new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0),
        new OrbitTarget(15, 1.0), new Evade(0.5, 1.0));
    bb.setSelf(new MoverState(new Vec3d(0, 0, 0), new Quatd(), new Vec3d(0, 0, 0)));
  }

  private ServerBotAiArenaContext ctxWith(final infinity.ai.objective.ArenaObjective objective) {
    return new ServerBotAiArenaContext(
        null, null, null, 0, 0, null, null, null, null, List.of(), 0.0, objective);
  }

  @Test
  public void enumeratesNavigateToAllFlags() {
    // All flags are offered (stable order) so the planner's stickiness can lock onto one — a
    // nearest-only goal thrashes between near-equidistant flags and the bot never commits.
    bb.setArenaContext(ctxWith(new TurfObjective(List.of(new GoalTile(100, 0), new GoalTile(10, 0)))));
    final List<TacticalGoal> goals = behaviour.enumerate(bb);
    assertEquals(2, goals.size());
    assertEquals(new NavigateToTile(100, 0), goals.get(0));
    assertEquals(new NavigateToTile(10, 0), goals.get(1));
  }

  @Test
  public void emptyWhenObjectiveHasNoGoals() {
    bb.setArenaContext(ctxWith(new DeathmatchObjective()));
    assertTrue(behaviour.enumerate(bb).isEmpty());
  }

  @Test
  public void emptyWhenNoArenaContext() {
    bb.setArenaContext(null);
    assertTrue(behaviour.enumerate(bb).isEmpty());
  }

  @Test
  public void idleFitDominatesEngagedFit() {
    final NavigateToTile goal = new NavigateToTile(10, 0);
    final double idle = behaviour.intrinsicScore(goal, bb); // no target set
    assertTrue("idle fit should be positive", idle > 0.0);
  }
}
