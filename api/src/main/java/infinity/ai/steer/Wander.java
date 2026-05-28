// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.PerceptionSnapshot;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Reynolds 1999 wander — projects a circle in front of the agent, drifts a target
 * around that circle by a bounded random angle each tick. Output is rate-shaped intent
 * yawing toward the drifting target with full forward thrust. Stateful by design.
 */
public final class Wander implements Steering {

  private final double wanderRadius;
  private final double wanderDistance;
  private final double wanderJitterRadians;
  private final double thrust;
  private final RandomGenerator random;
  private double currentAngleRadians;

  public Wander(
      final double wanderRadius,
      final double wanderDistance,
      final double wanderJitterRadians,
      final double thrust) {
    this(wanderRadius, wanderDistance, wanderJitterRadians, thrust, ThreadLocalRandom.current());
  }

  /** Package-private ctor for deterministic tests. */
  Wander(
      final double wanderRadius,
      final double wanderDistance,
      final double wanderJitterRadians,
      final double thrust,
      final RandomGenerator random) {
    this.wanderRadius = wanderRadius;
    this.wanderDistance = wanderDistance;
    this.wanderJitterRadians = wanderJitterRadians;
    this.thrust = thrust;
    this.random = random;
    this.currentAngleRadians = 0.0;
  }

  /** Test hook — read the drifting target angle. */
  double currentAngleRadians() {
    return this.currentAngleRadians;
  }

  @Override
  public Vec3d steer(final MoverSnapshot self, final PerceptionSnapshot perception) {
    this.currentAngleRadians +=
        (this.random.nextDouble() * 2.0 - 1.0) * this.wanderJitterRadians;
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final Vec3d circleCenter = self.position().add(forward.mult(this.wanderDistance));
    final Vec3d offset =
        forward.mult(Math.cos(this.currentAngleRadians) * this.wanderRadius)
            .add(left.mult(Math.sin(this.currentAngleRadians) * this.wanderRadius));
    final Vec3d wanderTarget = circleCenter.add(offset);
    final Vec3d delta = wanderTarget.subtract(self.position());
    if (delta.lengthSq() < 1e-9) {
      return new Vec3d(0.0, 0.0, this.thrust);
    }
    final Vec3d targetDir = delta.normalize();
    final double yaw = left.dot(targetDir);
    return new Vec3d(yaw, 0.0, this.thrust);
  }
}
