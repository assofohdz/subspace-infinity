// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.PerceptionSnapshot;

/**
 * Steers toward a desired heading (a direction, not a target position) — the flow-field
 * consumer. Output is rate-shaped {@code Vec3d(turnRate, _, thrust)} in {@code [-1, 1]};
 * returns {@code null} when no heading is set this tick. Stateful by design — the brain
 * sets the gradient-derived heading each tick via {@link #setDesiredDirection(Vec3d)}.
 * See ADR-0011.
 */
public final class SeekDirection implements Steering {

  // Minimum forward thrust while still facing the heading (fwd > 0). Keeps momentum through turns so
  // the bot ARCS toward the heading instead of spinning in place with zero thrust — momentum ships
  // can't rotate-in-place efficiently. Facing away (fwd <= 0) still gives zero (turn first).
  private static final double FORWARD_THRUST_FLOOR = 0.4;

  private final double thrust;
  private Vec3d desired;

  public SeekDirection(final double thrust) {
    this.thrust = thrust;
  }

  /** World-space XZ heading to pursue this tick; {@code null} or near-zero ⇒ no opinion. */
  public void setDesiredDirection(final Vec3d desired) {
    this.desired = desired;
  }

  @Override
  public Vec3d steer(final MoverSnapshot self, final PerceptionSnapshot perception) {
    if (this.desired == null || this.desired.lengthSq() < 1e-9) {
      return null;
    }
    final Vec3d dir = this.desired.normalize();
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final double fwd = forward.dot(dir);
    double yaw = left.dot(dir);
    if (fwd < 0.0) {
      // Heading is behind: max turn signal to swing around.
      yaw = (Math.abs(yaw) < 1e-3) ? 1.0 : Math.signum(yaw);
    }
    // Alignment-scaled thrust ("turn, then burn"): thrust as the bot faces the heading, so it tracks
    // the flow instead of thrusting full-speed off its current facing and overshooting. A floor keeps
    // momentum while turning toward the heading (arc, don't spin); facing away (fwd <= 0) ⇒ pure turn.
    final double thr = fwd <= 0.0 ? 0.0 : this.thrust * Math.max(FORWARD_THRUST_FLOOR, fwd);
    return new Vec3d(yaw, 0.0, thr);
  }
}
