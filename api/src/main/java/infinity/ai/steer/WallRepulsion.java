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

  // Only override the flow-field heading when the net repulsion is strong (a wall within ~1.4 cells).
  // Fainter pushes from more distant structure are ignored so the bot keeps following its nav heading
  // instead of being constantly yanked off it ("lots of WallRepel"). Tuned with the radius the caller
  // passes; this is the escape-of-last-resort threshold, not a soft bias.
  private static final double MIN_PUSH = 0.5;

  private final int radiusCells;
  private final double thrust;

  public WallRepulsion(final int radiusCells, final double thrust) {
    this.radiusCells = radiusCells;
    this.thrust = thrust;
  }

  /**
   * World-space away-from-walls direction (unit), or {@code null} if no wall is close enough. This is
   * the cooperative form: callers blend it into a flow heading so the hull eases off walls while still
   * following the route, instead of replacing the route. See {@code SteerToGoalTile}.
   */
  public Vec3d repulsion(
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
    if (rx * rx + rz * rz < MIN_PUSH * MIN_PUSH) {
      return null; // no close wall, or symmetric surroundings cancel — let the flow drive
    }
    final double mag = Math.hypot(rx, rz);
    return new Vec3d(rx / mag, 0.0, rz / mag);
  }

  /**
   * Escape steering {@code (yaw, _, thrust)} away from nearby walls, or {@code null} if none near. The
   * hard-override form for off-nav branches (no flow heading to blend into) — reverses out when a wall
   * is ahead. On the nav path use {@link #repulsion} blended into the heading instead.
   */
  public Vec3d steer(
      final MoverState self, final NavigationFields nav, final int originX, final int originZ) {
    final Vec3d desired = repulsion(self, nav, originX, originZ);
    if (desired == null) {
      return null;
    }
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
