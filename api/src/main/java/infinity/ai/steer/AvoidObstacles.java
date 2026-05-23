// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.NearbyObstacle;
import infinity.ai.PerceptionSnapshot;

/**
 * Reynolds 1999 obstacle avoidance via corridor projection. Examines each obstacle in
 * {@code perception.obstacles()}: if it falls inside the lookahead corridor
 * (width = {@code corridorHalfWidth}, depth = {@code lookAheadDistance}), computes the
 * easiest sideways turn-aside and returns a rate-shaped intent. Returns {@code null}
 * when no obstacle is in path. Source-agnostic — walls (ray-hit world cells) and dynamic
 * bodies (other ships, asteroids) flow through identically.
 */
public final class AvoidObstacles implements Steering {

  private final double lookAheadDistance;
  private final double corridorHalfWidth;
  private final double thrust;

  public AvoidObstacles(
      final double lookAheadDistance, final double corridorHalfWidth, final double thrust) {
    this.lookAheadDistance = lookAheadDistance;
    this.corridorHalfWidth = corridorHalfWidth;
    this.thrust = thrust;
  }

  @Override
  public Vec3d steer(final MoverState self, final PerceptionSnapshot perception) {
    if (perception.obstacles().isEmpty()) {
      return null;
    }
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    double minDist = this.lookAheadDistance;
    double bestTurn = 0.0;
    for (final NearbyObstacle obs : perception.obstacles()) {
      final Vec3d rel = obs.position().subtract(self.position());
      final double d = rel.dot(forward);
      final double s = rel.dot(left);
      if (!isInCorridor(d, s, obs.radius())) {
        continue;
      }
      final double surfDist = Math.sqrt(s * s + d * d) - obs.radius();
      if (surfDist < minDist) {
        minDist = surfDist;
        bestTurn = computeBestTurn(s - obs.radius(), s + obs.radius());
      }
    }
    if (bestTurn == 0.0) {
      return null;
    }
    // Two regimes:
    //  - "stuck against wall" (minDist < corridorHalfWidth): the ship cannot turn fast
    //    enough to clear the obstacle while still thrusting forward. Reverse + max-turn
    //    backs the ship out so it has room to rotate. PlayerDriver accepts negative
    //    intent.z as reverse thrust.
    //  - "proactive avoid" (minDist >= corridorHalfWidth): gradual turn, full forward
    //    thrust — Reynolds-style smooth steer-around.
    if (minDist < this.corridorHalfWidth) {
      return new Vec3d(Math.signum(bestTurn), 0.0, -this.thrust);
    }
    return new Vec3d(bestTurn, 0.0, this.thrust);
  }

  /**
   * Is an obstacle at forward-distance {@code d}, lateral-offset {@code s}, radius
   * {@code r} inside the lookahead corridor? Combines the forward-range filter (must be
   * ahead of us and within lookahead) and the lateral-overlap filter (obstacle's lateral
   * span must overlap the corridor's left or right edge).
   */
  private boolean isInCorridor(final double d, final double s, final double r) {
    if (d < 0.0 || d > this.lookAheadDistance) {
      return false;
    }
    final double leftEdge = s - r;
    final double rightEdge = s + r;
    return leftEdge <= this.corridorHalfWidth && rightEdge >= -this.corridorHalfWidth;
  }

  /**
   * Choose the direction with the smaller-magnitude obstacle edge — turning that way
   * costs less heading change.
   */
  private static double computeBestTurn(final double leftEdge, final double rightEdge) {
    if (Math.abs(rightEdge) < Math.abs(leftEdge)) {
      // Right edge closer to center → turn left (+) past it.
      return rightEdge < 0.0 ? 0.1 : rightEdge;
    }
    // Left edge closer to center → turn right (-) past it.
    return leftEdge > 0.0 ? -0.1 : leftEdge;
  }
}
