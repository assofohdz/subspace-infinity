// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.bt.Condition;
import infinity.ai.bt.Status;

/**
 * {@code SUCCESS} when the angle between the bot's forward heading and the direction to
 * {@link Blackboard#target()} is within the configured cone. Used to gate {@code FireWeapon}
 * inside a {@code Selector} fallback so the bot only fires when it's actually aimed at
 * the target — bullets fly along body-forward, so unaimed shots always miss. Compares
 * cosines to skip the {@code atan2/acos} round-trip; same semantics.
 */
public final class InAimRange implements Condition {

  private final double cosineThreshold;

  public InAimRange(final double maxAngleDegrees) {
    this.cosineThreshold = Math.cos(Math.toRadians(maxAngleDegrees));
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    if (blackboard.target() == null || blackboard.self() == null) {
      return Status.FAILURE;
    }
    final Vec3d toTarget =
        blackboard.target().position().subtract(blackboard.self().position());
    if (toTarget.lengthSq() < 1e-9) {
      return Status.SUCCESS;
    }
    final Vec3d dir = toTarget.normalize();
    final Vec3d forward = blackboard.self().orientation().mult(Vec3d.UNIT_Z);
    return forward.dot(dir) >= this.cosineThreshold ? Status.SUCCESS : Status.FAILURE;
  }
}
