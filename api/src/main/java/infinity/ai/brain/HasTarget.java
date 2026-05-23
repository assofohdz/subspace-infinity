// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Condition;
import infinity.ai.bt.Status;

/** {@code SUCCESS} when blackboard target is non-null; {@code FAILURE} otherwise. */
public final class HasTarget implements Condition {

  @Override
  public Status tick(final Blackboard blackboard) {
    return blackboard.target() != null ? Status.SUCCESS : Status.FAILURE;
  }
}
