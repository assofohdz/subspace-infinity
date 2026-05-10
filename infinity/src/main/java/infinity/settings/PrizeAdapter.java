// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.PrizeConfig;

/**
 * Typed Groovy adapter for {@code prize.groovy} fragments. Parses a
 * {@code prize { … }} block into a {@link PrizeConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadPrize}.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * prize {
 *     minExist        4000    // [Prize] PrizeMinExist       (cs → ms ×10); optional
 *     maxExist        8000    // [Prize] PrizeMaxExist       (cs → ms ×10)
 *     deathPrizeTime  1500    // [Prize] DeathPrizeTime      (cs → ms ×10); optional, 0=disabled
 *     negativeFactor  1000    // [Prize] PrizeNegativeFactor (1-in-N);     optional, 0=disabled
 * }
 * }</pre>
 *
 * <p>{@code minExist} + {@code maxExist} define a uniform random lifetime
 * range that {@code PrizeSystem} samples per-prize at spawn time. Omitting
 * {@code minExist} pins it equal to {@code maxExist} (= no randomness;
 * preserves 1:1 behaviour for un-migrated arenas). {@code minExist >
 * maxExist} throws at parse time — explicit author error rather than a
 * silent clamp/swap.
 *
 * <p>{@code deathPrizeTime} controls the lifetime of prizes dropped at a
 * ship's death point. Omitting it leaves {@code deathPrizeTimeMs == 0}
 * (= death-drops disabled), preserving 1:1 behaviour for un-migrated
 * arenas.
 *
 * <p>{@code negativeFactor} is Subspace's 1-in-N odds for a spawning
 * prize to be replaced by {@code Dud}. Omitting it leaves
 * {@code prizeNegativeFactor == 0} (= no negative-prize roll).
 *
 * <p>Other Subspace {@code [Prize]} keys (PrizeFactor, PrizeDelay,
 * MultiPrizeCount, PrizeHideCount, EngineShutdownTime, etc.) aren't in
 * {@link PrizeConfig} today — they're sub-slice 8d (deferred) / polish-
 * bag work per the slice queue. The adapter only exposes fields that
 * have a typed config + active consumer.
 */
public final class PrizeAdapter
    extends SingleClosureAdapter<PrizeConfig, PrizeAdapter.PrizeBuilder> {

  /** Stateless; safe to share across calls. */
  public static final PrizeAdapter INSTANCE = new PrizeAdapter();

  private PrizeAdapter() {
    super("prize", PrizeConfig.DEFAULTS);
  }

  @Override
  protected PrizeBuilder newBuilder() {
    return new PrizeBuilder();
  }

  @Override
  public PrizeConfig extract(final PrizeBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code prize { ... }} block. */
  public static final class PrizeBuilder {

    private long defaultDecayMs = PrizeConfig.DEFAULTS.defaultDecayMs();
    private Long defaultMinDecayMs = null; // null = "not authored; pin to defaultDecayMs at build()"
    private long deathPrizeTimeMs = PrizeConfig.DEFAULTS.deathPrizeTimeMs(); // 0 = disabled
    private int prizeNegativeFactor = PrizeConfig.DEFAULTS.prizeNegativeFactor(); // 0 = disabled

    PrizeBuilder() {}

    /**
     * {@code [Prize] PrizeMaxExist} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void maxExist(final int centiseconds) {
      this.defaultDecayMs = Validators.centisecondsToMs("prize.maxExist", centiseconds);
    }

    /**
     * {@code [Prize] PrizeMinExist} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds. Optional — omit to pin
     * equal to {@code maxExist} (no random-lifetime variation).
     */
    public void minExist(final int centiseconds) {
      this.defaultMinDecayMs = Validators.centisecondsToMs("prize.minExist", centiseconds);
    }

    /**
     * {@code [Prize] DeathPrizeTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds. Optional — omit to leave at
     * {@code 0} (death-drops disabled).
     */
    public void deathPrizeTime(final int centiseconds) {
      this.deathPrizeTimeMs = Validators.centisecondsToMs("prize.deathPrizeTime", centiseconds);
    }

    /**
     * {@code [Prize] PrizeNegativeFactor} — 1-in-N odds for a spawning
     * prize to be replaced by {@code Dud}. Optional — omit to leave at
     * {@code 0} (no negative-prize roll).
     */
    public void negativeFactor(final int oneInN) {
      this.prizeNegativeFactor = oneInN;
    }

    PrizeConfig build() {
      final long min =
          defaultMinDecayMs != null ? defaultMinDecayMs.longValue() : defaultDecayMs;
      if (min > defaultDecayMs) {
        throw new IllegalArgumentException(
            "prize.minExist ("
                + (min / 10)
                + " cs) must be <= maxExist ("
                + (defaultDecayMs / 10)
                + " cs)");
      }
      return new PrizeConfig(
          defaultDecayMs,
          min,
          deathPrizeTimeMs,
          prizeNegativeFactor,
          PrizeConfig.DEFAULTS.defaultMaxCount(),
          PrizeConfig.DEFAULTS.bountyValue());
    }
  }
}
