// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;

/** Shared situational-fit helpers for the baseline behaviours; full ADR-0016 fits land per catalog issue. */
final class BaselineFit {

  private BaselineFit() {}

  /** Energy as a {@code [0,1]} fraction of max, or {@code -1} when the bot has no energy data yet. */
  static double energyFraction(final Blackboard bb) {
    final int cur = bb.currentEnergy();
    final int max = bb.maxEnergy();
    if (cur < 0 || max <= 0) {
      return -1.0;
    }
    return Math.max(0.0, Math.min(1.0, (double) cur / max));
  }
}
