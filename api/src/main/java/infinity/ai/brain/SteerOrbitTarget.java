// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;

/**
 * Sets the blackboard target on the bot's {@code OrbitTarget} steering, samples a rate-
 * shaped intent, writes it to {@link Blackboard#intent()}. Returns {@code FAILURE} if no
 * target (the parent {@code Sequence} should also gate on {@code HasTarget} +
 * {@code InWeaponRange}; this is a defensive guard).
 */
public final class SteerOrbitTarget implements Action {

  @Override
  public Status tick(final Blackboard blackboard) {
    blackboard.orbit().setTarget(blackboard.target());
    final Vec3d intent = blackboard.orbit().steer(blackboard.self(), blackboard.perception());
    if (intent == null) {
      return Status.FAILURE;
    }
    blackboard.intent().set(intent);
    blackboard.setLastBranch("Engage");
    return Status.SUCCESS;
  }
}
