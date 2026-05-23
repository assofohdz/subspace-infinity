// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.bt;

import infinity.ai.brain.Blackboard;

/**
 * Composite — ticks children in order, returns the first non-{@code FAILURE} result.
 * Empty selector returns {@code FAILURE}. See ADR-0009.
 */
public final class Selector implements Behavior {

  private final Behavior[] children;

  public Selector(final Behavior... children) {
    this.children = children.clone();
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    for (final Behavior child : this.children) {
      final Status result = child.tick(blackboard);
      if (result != Status.FAILURE) {
        return result;
      }
    }
    return Status.FAILURE;
  }
}
