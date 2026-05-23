// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;

/**
 * Sets the blackboard target as the threat on the bot's {@code Evade} steering, samples
 * a rate-shaped intent, writes it to {@link Blackboard#intent()}. Returns {@code FAILURE}
 * if no target (parent {@code Sequence} should also gate on {@code HasTarget}).
 */
public final class SteerEvade implements Action {

  @Override
  public Status tick(final Blackboard blackboard) {
    blackboard.evade().setThreat(blackboard.target());
    final Vec3d intent = blackboard.evade().steer(blackboard.self(), blackboard.perception());
    if (intent == null) {
      return Status.FAILURE;
    }
    blackboard.intent().set(intent);
    blackboard.setLastBranch("Evade");
    return Status.SUCCESS;
  }
}
