// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;

/**
 * Sets the blackboard target on the bot's {@code Pursue} steering, samples a rate-shaped
 * intent, writes it to {@link Blackboard#intent()}. Returns {@code FAILURE} when the
 * steering yields no opinion (no target) — lets the parent {@code Selector} fall through.
 */
public final class SteerPursue implements Action {

  @Override
  public Status tick(final Blackboard blackboard) {
    blackboard.pursue().setTarget(blackboard.target());
    final Vec3d intent = blackboard.pursue().steer(blackboard.self(), blackboard.perception());
    if (intent == null) {
      return Status.FAILURE;
    }
    blackboard.intent().set(intent);
    blackboard.setLastBranch("Pursue");
    return Status.SUCCESS;
  }
}
