// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;

/**
 * Reynolds-style circle-strafe — yaws toward a tangent of the orbit circle around the
 * target, blended with an inward / outward radial correction proportional to distance
 * error. Rate-shaped intent: {@code .x} = turn rate, {@code .z} = forward thrust.
 * Stateful by design — brain action reconfigures the target each tick via
 * {@link #setTarget(NearbyShip)}. See ADR-0009.
 */
public final class OrbitTarget implements Steering {

  private final double orbitRadius;
  private final double thrust;
  private NearbyShip target;

  public OrbitTarget(final double orbitRadius, final double thrust) {
    this.orbitRadius = orbitRadius;
    this.thrust = thrust;
  }

  public void setTarget(final NearbyShip target) {
    this.target = target;
  }

  @Override
  public Vec3d steer(final MoverState self, final PerceptionSnapshot perception) {
    if (this.target == null) {
      return null;
    }
    final Vec3d toTarget = this.target.position().subtract(self.position());
    final double distToTarget = toTarget.length();
    if (distToTarget < 1e-6) {
      return new Vec3d(0.0, 0.0, this.thrust);
    }
    final Vec3d radial = toTarget.mult(1.0 / distToTarget);
    // Counter-clockwise tangent in the X/Z plane — perpendicular to radial.
    final Vec3d tangent = new Vec3d(-radial.z, 0.0, radial.x);
    // Saturating radial correction: positive = pull toward target, negative = push away.
    final double radialError = distToTarget - this.orbitRadius;
    final double correction = Math.tanh(radialError / this.orbitRadius);
    Vec3d desired = tangent.add(radial.mult(correction));
    if (desired.lengthSq() < 1e-9) {
      desired = tangent;
    } else {
      desired.normalizeLocal();
    }
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final double yaw = left.dot(desired);
    return new Vec3d(yaw, 0.0, this.thrust);
  }
}
