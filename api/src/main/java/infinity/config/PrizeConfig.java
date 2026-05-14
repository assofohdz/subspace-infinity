// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena prize-spawn defaults; populated from {@code prize.groovy} via {@code PrizeAdapter}. See REFERENCE.md {@code ## Prize}.
 *
 * <p>Centisecond timing fields convert ×10 at the loader: {@code PrizeMaxExist} → {@link #defaultDecayMs},
 * {@code PrizeMinExist} → {@link #defaultMinDecayMs}, {@code DeathPrizeTime} → {@link #deathPrizeTimeMs}.
 * {@code PrizeSpawnerSystem} samples uniform random in {@code [defaultMinDecayMs, defaultDecayMs]} (= no randomness
 * when min equals max).
 *
 * <p>{@code prizeNegativeFactor} is canon's 1-in-N roll; Infinity simplifies by substituting DUD instead of
 * running inverse-stat appliers — knob preserves canonical probability for a future swap. {@code 0} disables.
 */
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
