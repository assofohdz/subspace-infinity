// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning for {@code FirstToXWinCondition}. {@code target == 0} signals
 * "use {@link #DEFAULT_TARGET}" — lets {@code winCondition 'first-to-x'} (no
 * kwargs) bind without a Jackson default.
 */
public record FirstToXWinConditionConfig(int target) {

  public static final int DEFAULT_TARGET = 1000;

  public FirstToXWinConditionConfig {
    if (target < 0) {
      throw new IllegalArgumentException("target must be >= 0; got " + target);
    }
  }

  /** {@link #target} if {@code > 0}; otherwise {@link #DEFAULT_TARGET}. */
  public int effectiveTarget() {
    return target > 0 ? target : DEFAULT_TARGET;
  }
}
