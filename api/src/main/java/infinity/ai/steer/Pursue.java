// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;

/**
 * Reynolds 1999 lead-prediction pursuit. Target position predicted as
 * {@code target.position + target.velocity * leadTimeSeconds}; output is rate-shaped
 * intent in {@code [-1, 1]}: {@code .x} = turn rate toward predicted point, {@code .z}
 * = thrust. Returns {@code null} when no target is set (brain hasn't picked one this
 * tick). Stateful by design — brain reconfigures the same instance each tick via
 * {@link #setTarget(NearbyShip)}.
 */
public final class Pursue implements Steering {

  private final double leadTimeSeconds;
  private final double thrust;
  private NearbyShip target;

  public Pursue(final double leadTimeSeconds, final double thrust) {
    this.leadTimeSeconds = leadTimeSeconds;
    this.thrust = thrust;
  }

  public void setTarget(final NearbyShip target) {
    this.target = target;
  }

  @Override
  public Vec3d steer(final MoverSnapshot self, final PerceptionSnapshot perception) {
    if (this.target == null) {
      return null;
    }
    final Vec3d predicted =
        this.target.position().add(this.target.velocity().mult(this.leadTimeSeconds));
    final Vec3d delta = predicted.subtract(self.position());
    if (delta.lengthSq() < 1e-9) {
      return new Vec3d(0.0, 0.0, this.thrust);
    }
    final Vec3d targetDir = delta.normalize();
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final double fwd = forward.dot(targetDir);
    double yaw = left.dot(targetDir);
    if (fwd < 0.0) {
      // Target is behind: max turn signal to swing around.
      yaw = (Math.abs(yaw) < 1e-3) ? 1.0 : Math.signum(yaw);
    }
    return new Vec3d(yaw, 0.0, this.thrust);
  }
}
