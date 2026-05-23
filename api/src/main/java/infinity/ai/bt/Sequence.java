// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.bt;

import infinity.ai.brain.Blackboard;

/**
 * Composite — ticks children in order, fail-fast on first {@code FAILURE}. Returns
 * {@code SUCCESS} when every child succeeded. Empty sequence returns {@code SUCCESS}.
 * See ADR-0009.
 */
public final class Sequence implements Behavior {

  private final Behavior[] children;

  public Sequence(final Behavior... children) {
    this.children = children.clone();
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    for (final Behavior child : this.children) {
      final Status result = child.tick(blackboard);
      if (result != Status.SUCCESS) {
        return result;
      }
    }
    return Status.SUCCESS;
  }
}
