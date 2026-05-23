// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.bt;

import infinity.ai.brain.Blackboard;

/**
 * Trivial leaf returning {@link Status#SUCCESS} unconditionally. Useful inside a
 * {@code Selector} as a "swallow failure" fallback — e.g. fire-when-aimed where
 * mis-aimed should not abort the parent {@code Sequence}.
 */
public final class AlwaysSucceed implements Behavior {

  @Override
  public Status tick(final Blackboard blackboard) {
    return Status.SUCCESS;
  }
}
