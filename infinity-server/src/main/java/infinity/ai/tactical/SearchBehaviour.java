// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import java.util.List;

/**
 * {@code search} (catalog #07): idle drift / patrol — the always-eligible fallback. Baseline fit
 * favours searching when no target is in view, yielding to combat goals when one is. See ADR-0013.
 */
public final class SearchBehaviour implements Behaviour {

  // Fit floors: dominant when idle (no target), negligible when a combat goal is available.
  private static final double IDLE_FIT = 0.6;
  private static final double ENGAGED_FIT = 0.1;

  @Override
  public String name() {
    return "search";
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    return List.of(new Search());
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    return bb.target() == null ? IDLE_FIT : ENGAGED_FIT;
  }
}
