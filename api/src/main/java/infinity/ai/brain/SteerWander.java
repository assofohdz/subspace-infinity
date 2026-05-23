// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;

/** Samples the bot's {@code Wander} steering and writes intent. Always {@code SUCCESS} — fallback leaf. */
public final class SteerWander implements Action {

  @Override
  public Status tick(final Blackboard blackboard) {
    final Vec3d intent = blackboard.wander().steer(blackboard.self(), blackboard.perception());
    if (intent != null) {
      blackboard.intent().set(intent);
    }
    blackboard.setLastBranch("Wander");
    return Status.SUCCESS;
  }
}
