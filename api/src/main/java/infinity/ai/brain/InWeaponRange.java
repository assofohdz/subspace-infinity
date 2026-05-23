// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Condition;
import infinity.ai.bt.Status;

/** {@code SUCCESS} when blackboard target is within configured range; {@code FAILURE} otherwise. */
public final class InWeaponRange implements Condition {

  private final double rangeSquared;

  public InWeaponRange(final double range) {
    this.rangeSquared = range * range;
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    if (blackboard.target() == null || blackboard.self() == null) {
      return Status.FAILURE;
    }
    final double distSq =
        blackboard.target().position().distanceSq(blackboard.self().position());
    return distSq <= this.rangeSquared ? Status.SUCCESS : Status.FAILURE;
  }
}
