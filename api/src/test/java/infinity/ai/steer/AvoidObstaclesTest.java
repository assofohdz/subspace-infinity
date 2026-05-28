// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyObstacle;
import infinity.ai.PerceptionSnapshot;
import java.util.List;
import org.junit.Test;

/** Behavioural tests for {@link AvoidObstacles}'s Reynolds corridor projection. */
public class AvoidObstaclesTest {

  private static final double DELTA = 1e-6;

  // 5-unit lookahead corridor, 0.6 half-width, full forward thrust during avoidance.
  private static final AvoidObstacles AVOID = new AvoidObstacles(5.0, 0.6, 1.0);

  @Test
  public void emptyObstaclesReturnsNull() {
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), PerceptionSnapshot.EMPTY);
    assertNull("no obstacles → no opinion", intent);
  }

  @Test
  public void obstacleBehindIsIgnored() {
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(0, 0, -3), 0.5));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNull("obstacle behind mover → no avoidance", intent);
  }

  @Test
  public void obstacleBeyondLookaheadIsIgnored() {
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(0, 0, 100), 0.5));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNull("obstacle past lookahead → no avoidance", intent);
  }

  @Test
  public void obstacleFarLateralOutsideCorridorIsIgnored() {
    // 10 units to the left, dead ahead. Way outside the 0.6-wide corridor.
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(10, 0, 3), 0.5));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNull("obstacle outside corridor → no avoidance", intent);
  }

  @Test
  public void obstacleDeadAheadProducesTurnSignal() {
    // Centered ahead at 3 units, radius 0.5 — squarely in the corridor.
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(0, 0, 3), 0.5));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNotNull("obstacle in corridor → must turn", intent);
    assertTrue("turn signal non-zero", Math.abs(intent.x) > 0.0);
    assertTrue("thrust held during avoidance", intent.z > 0.0);
  }

  @Test
  public void obstacleToLeftSideProducesRightTurn() {
    // Obstacle slightly to the +X (left) side but inside the corridor — bot should
    // turn right (negative intent.x) to slide past it on the right.
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(0.4, 0, 3), 0.3));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNotNull(intent);
    assertTrue("obstacle on +X side → turn -X (right)", intent.x < 0.0);
  }

  @Test
  public void obstacleToRightSideProducesLeftTurn() {
    // Symmetric to above — obstacle on -X side, bot turns +X (left).
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(-0.4, 0, 3), 0.3));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNotNull(intent);
    assertTrue("obstacle on -X side → turn +X (left)", intent.x > 0.0);
  }

  @Test
  public void pointBlankObstacleProducesReverseThrust() {
    // Obstacle right at the bot's nose, within corridorHalfWidth — bot is stuck and
    // cannot turn fast enough to clear it while thrusting forward. Expect max-magnitude
    // turn AND reverse thrust so the bot backs out and gives itself room to rotate.
    final PerceptionSnapshot snap = withObstacles(new NearbyObstacle(new Vec3d(0, 0, 0.3), 0.3));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNotNull(intent);
    assertEquals("stuck-against-wall → max turn magnitude", 1.0, Math.abs(intent.x), DELTA);
    assertTrue("stuck-against-wall → reverse thrust (intent.z negative)", intent.z < 0.0);
  }

  @Test
  public void multipleObstaclesPicksNearest() {
    // Two obstacles in corridor — at 3 and 4 units. The nearest (3 units) drives the
    // turn signal. Place the nearer one on the +X side and the further on -X to verify
    // we picked the near one's direction.
    final PerceptionSnapshot snap =
        withObstacles(
            new NearbyObstacle(new Vec3d(0.4, 0, 3), 0.3),
            new NearbyObstacle(new Vec3d(-0.4, 0, 4), 0.3));
    final Vec3d intent = AVOID.steer(mover(0, 0, 0), snap);
    assertNotNull(intent);
    assertTrue("nearest obstacle (on +X) wins → turn -X", intent.x < 0.0);
  }

  private static MoverSnapshot mover(final double x, final double y, final double z) {
    return new MoverSnapshot(new Vec3d(x, y, z), new Quatd(), new Vec3d());
  }

  private static PerceptionSnapshot withObstacles(final NearbyObstacle... obstacles) {
    return new PerceptionSnapshot(List.of(), List.of(), List.of(obstacles));
  }
}
