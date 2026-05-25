// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.field.NavigationFields;

/**
 * Omnidirectional reactive wall avoidance from the passability grid (ADR-0011 #03). Sums an
 * inverse-square repulsion away from every wall cell within {@code radiusCells} of the bot; when the
 * net push is significant it overrides the BT intent with a <em>reverse-rotate-thrust</em> escape:
 * yaw toward open space (forcing a turn when open is behind), and thrust = forward-alignment so the
 * bot <b>reverses out</b> when a wall is ahead and drives forward once it faces open. This is the
 * "rotate, then move / reverse, rotate, thrust" maneuver the forward-only ray-cast {@code AvoidObstacles}
 * can't do — it sees walls in every direction, not just dead ahead.
 *
 * <p>Symmetric surroundings (a straight corridor) cancel to ~zero net push, so corridor traversal
 * isn't blocked; one-sided walls and corners produce a clear escape vector. Returns {@code null} when
 * no wall is near (BT intent proceeds unchanged).
 */
public final class WallRepulsion {

  private static final double EPS = 1e-6;

  private final int radiusCells;
  private final double thrust;

  public WallRepulsion(final int radiusCells, final double thrust) {
    this.radiusCells = radiusCells;
    this.thrust = thrust;
  }

  /** Escape steering {@code (yaw, _, thrust)} away from nearby walls, or {@code null} if none near. */
  public Vec3d steer(
      final MoverState self, final NavigationFields nav, final int originX, final int originZ) {
    final int cx = (int) Math.floor(self.position().x) - originX;
    final int cz = (int) Math.floor(self.position().z) - originZ;
    double rx = 0.0;
    double rz = 0.0;
    for (int dz = -this.radiusCells; dz <= this.radiusCells; dz++) {
      for (int dx = -this.radiusCells; dx <= this.radiusCells; dx++) {
        if ((dx == 0 && dz == 0) || nav.passableAt(cx + dx, cz + dz)) {
          continue;
        }
        final double d2 = (double) dx * dx + (double) dz * dz; // inverse-square: nearer walls push harder
        rx -= dx / d2;
        rz -= dz / d2;
      }
    }
    if (rx * rx + rz * rz < EPS) {
      return null; // no wall near, or symmetric surroundings cancel — let the BT drive
    }
    final double mag = Math.hypot(rx, rz);
    final Vec3d desired = new Vec3d(rx / mag, 0.0, rz / mag);
    final Vec3d forward = self.orientation().mult(Vec3d.UNIT_Z);
    final Vec3d left = self.orientation().mult(Vec3d.UNIT_X);
    final double fwd = forward.dot(desired);
    double yaw = left.dot(desired);
    if (fwd < 0.0) {
      // open space is behind us — turn around (force a non-zero yaw so we don't stall facing the wall)
      yaw = Math.abs(yaw) < 1e-3 ? 1.0 : Math.signum(yaw);
    }
    yaw = Math.max(-1.0, Math.min(1.0, yaw));
    // thrust toward open: forward when we face it, reverse out when a wall is ahead.
    final double thr = Math.max(-1.0, Math.min(1.0, fwd)) * this.thrust;
    return new Vec3d(yaw, 0.0, thr);
  }
}
