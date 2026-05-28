// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import infinity.config.ZoneBotAiConfig;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code search} (catalog #07): idle drift / patrol — the always-eligible fallback. Baseline fit
 * favours searching when no target is in view, yielding to combat goals when one is. Fit floors
 * sourced from {@code zone-bot-ai.groovy} ({@code searchIdleFit}/{@code searchEngagedFit}).
 * See ADR-0013.
 */
public final class SearchBehaviour implements Behaviour {

  private final Supplier<ZoneBotAiConfig> cfg;

  public SearchBehaviour(final Supplier<ZoneBotAiConfig> cfg) {
    this.cfg = cfg;
  }

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
    final ZoneBotAiConfig c = this.cfg.get();
    return bb.target() == null ? c.searchIdleFit() : c.searchEngagedFit();
  }
}
