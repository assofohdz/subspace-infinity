// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena prize-spawn defaults; populated from {@code prize.groovy} via {@code PrizeAdapter}. {@code prizeNegativeFactor} substitutes DUD on the roll. See REFERENCE.md {@code ## Prize}. */
public record PrizeConfig(
    long defaultDecayMs,
    long defaultMinDecayMs,
    long deathPrizeTimeMs,
    int prizeNegativeFactor,
    int defaultMaxCount,
    int bountyValue) {

  /** Subspace-canonical baseline; death-drops and negative-prize roll default disabled. */
  public static final PrizeConfig DEFAULTS = new PrizeConfig(80_000L, 80_000L, 0L, 0, 10, 10);
}
