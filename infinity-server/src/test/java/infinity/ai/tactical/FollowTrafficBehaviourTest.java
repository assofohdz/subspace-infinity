// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.brain.Blackboard;
import infinity.ai.field.TileScored;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class FollowTrafficBehaviourTest {

  private final FollowTrafficBehaviour behaviour = new FollowTrafficBehaviour();
  private Blackboard bb;

  @Before
  public void setUp() {
    bb =
        new Blackboard(
            new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0), new OrbitTarget(15, 1.0),
            new Evade(0.5, 1.0));
    bb.setSelf(new MoverState(new Vec3d(0, 0, 0), new Quatd(), new Vec3d()));
  }

  /** Context with the given (arena-relative) chokepoint pool, no dynamic fields, origin (ox,oz). */
  private static ServerBotAiArenaContext ctx(
      final int ox, final int oz, final TileScored... pool) {
    return new ServerBotAiArenaContext(
        null, null, null, ox, oz, null, null, null, null, List.of(pool), 0.5);
  }

  @Test
  public void noContextOffersNothing() {
    assertTrue(behaviour.enumerate(bb).isEmpty());
  }

  @Test
  public void noChokepointsOffersNothing() {
    bb.setArenaContext(ctx(0, 0));
    assertTrue(behaviour.enumerate(bb).isEmpty());
  }

  @Test
  public void prefersNearerHotTileOverFarHotterOne() {
    // self at relative (0,0). A: near (10,10) score 1.0; B: far (500,500) score 1.2.
    // distance-decay (scale 200) sinks B below A.
    bb.setArenaContext(ctx(0, 0, new TileScored(500, 500, 1.2), new TileScored(10, 10, 1.0)));
    final List<TacticalGoal> goals = behaviour.enumerate(bb);
    assertEquals(1, goals.size());
    final NavigateToTile goal = (NavigateToTile) goals.get(0);
    assertEquals("picks the nearer hot tile", 10, goal.cellX());
    assertEquals(10, goal.cellZ());
  }

  @Test
  public void goalIsWorldCellNotArenaRelative() {
    // Origin (100,200): a relative chokepoint at (10,10) is world cell (110,210).
    bb.setArenaContext(ctx(100, 200, new TileScored(10, 10, 1.0)));
    final NavigateToTile goal = (NavigateToTile) behaviour.enumerate(bb).get(0);
    assertEquals(110, goal.cellX());
    assertEquals(210, goal.cellZ());
  }

  @Test
  public void yieldsToTargetInView() {
    bb.setArenaContext(ctx(0, 0, new TileScored(10, 10, 1.0)));
    final double idle = behaviour.intrinsicScore(new NavigateToTile(10, 10), bb);
    bb.setTarget(new infinity.ai.NearbyShip(null, new Vec3d(5, 0, 5), new Quatd(), new Vec3d(), 1));
    final double engaged = behaviour.intrinsicScore(new NavigateToTile(10, 10), bb);
    assertTrue("fit drops when a target is in view", engaged < idle);
  }
}
