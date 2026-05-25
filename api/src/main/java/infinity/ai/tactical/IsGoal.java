// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import infinity.ai.bt.Condition;
import infinity.ai.bt.Status;

/**
 * BT leaf that gates a per-goal Execute branch — {@code SUCCESS} iff the blackboard's current goal
 * is <em>exactly</em> {@code goalClass} (no subtype match; goal variants get separate branches).
 * See ADR-0013.
 */
public final class IsGoal implements Condition {

  private final Class<? extends TacticalGoal> goalClass;

  public IsGoal(final Class<? extends TacticalGoal> goalClass) {
    this.goalClass = goalClass;
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    final TacticalGoal goal = blackboard.currentGoal();
    return goal != null && goal.getClass() == this.goalClass ? Status.SUCCESS : Status.FAILURE;
  }
}
