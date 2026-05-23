// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;

/**
 * Reynolds 1999 lead-prediction evade — parametric counterpart to {@link Pursue}. Predicts
 * where the threat will be in {@code leadTimeSeconds} and steers directly away from that
 * point. Rate-shaped intent: {@code .x} = turn rate, {@code .z} = thrust. Returns
 * {@code null} when no threat is set. See ADR-0009.
 */
public final class Evade implements Steering {

  private final double leadTimeSeconds;
  private final double thrust;
  private NearbyShip threat;

  public Evade(final double leadTimeSeconds, final double thrust) {
    this.leadTimeSeconds = leadTimeSeconds;
    this.thrust = thrust;
  }

  public void setThreat(final NearbyShip threat) {
    this.threat = threat;
  }

  @Override
  public Vec3d steer(final MoverState self, final PerceptionSnapshot perception) {
    if (this.threat == null) {
      return null;
    }
    final Vec3d predicted =
        this.threat.position().add(this.threat.velocity().mult(this.leadTimeSeconds));
    final Vec3d delta = predicted.subtract(self.position());
    if (delta.lengthSq() < 1e-9) {
      return new Vec3d(0.0, 0.0, this.thrust);
    }
    final Vec3d targetDir = delta.normalize();
    final Vec3d awayDir = targetDir.mult(-1.0);
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final double fwd = forward.dot(awayDir);
    double yaw = left.dot(awayDir);
    if (fwd < 0.0) {
      // Threat is ahead of us — max turn to flip and run.
      yaw = (Math.abs(yaw) < 1e-3) ? 1.0 : Math.signum(yaw);
    }
    return new Vec3d(yaw, 0.0, this.thrust);
  }
}
