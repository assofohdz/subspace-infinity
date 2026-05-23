// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Condition;
import infinity.ai.bt.Status;

/**
 * {@code SUCCESS} when the bot's current energy is below {@code threshold × maxEnergy}.
 * {@code FAILURE} when energy / max isn't sampled yet (sentinel {@code -1}) — refuses to
 * fire the evade branch without data so we don't flee a phantom threat.
 */
public final class LowEnergy implements Condition {

  private final double thresholdFraction;

  public LowEnergy(final double thresholdFraction) {
    this.thresholdFraction = thresholdFraction;
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    final int current = blackboard.currentEnergy();
    final int max = blackboard.maxEnergy();
    if (current < 0 || max <= 0) {
      return Status.FAILURE;
    }
    return current < max * this.thresholdFraction ? Status.SUCCESS : Status.FAILURE;
  }
}
