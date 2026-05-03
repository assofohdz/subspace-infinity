// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena prize-spawn defaults. Read by {@code PrizeSystem} (and by
 * arena-load logic when materialising prize-spawner specs) and forwarded to
 * the {@code GameEntities} factories — those factories keep matching
 * constants ({@code PRIZE_DEFAULT_DECAY_MS} / {@code PRIZE_DEFAULT_MAX_COUNT}
 * / {@code BOUNTY_VALUE}) as last-resort fallbacks so module authors using
 * the api can call them without a config lookup, but the per-arena values
 * here are authoritative on the server hot path.
 *
 * <p>Populated from the merged Groovy fragment store at arena-load — see
 * {@code GroovyWeaponsLoader.loadPrize}. Subspace fragment keys:
 * <ul>
 *   <li>{@code [Prize] PrizeMaxExist} (centiseconds) × 10 → {@link #defaultDecayMs}
 * </ul>
 * {@link #defaultMaxCount} and {@link #bountyValue} have no canonical
 * Subspace fragment key today; they stay on their Infinity defaults until
 * a content-side decision wires them.
 *
 * @param defaultDecayMs default lifetime in ms for prizes that don't
 *     specify per-spawner ttl
 * @param defaultMaxCount default simultaneous-prize cap for spawners that
 *     don't specify their own (Infinity default {@code 10})
 * @param bountyValue greens granted to a ship picking up a prize (Infinity
 *     default {@code 10})
 */
public record PrizeConfig(long defaultDecayMs, int defaultMaxCount, int bountyValue) {

  /**
   * Subspace-canonical baseline used when no fragment provides a value.
   * Decay pulled from the {@code svs} preset's {@code [Prize] PrizeMaxExist}
   * ({@code 8000} centiseconds = 80000ms). Count and bounty keep Infinity
   * defaults — no canonical Subspace key for those.
   */
  public static final PrizeConfig DEFAULTS = new PrizeConfig(80000L, 10, 10);
}
