// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.PrizeConfig;

/** Typed adapter for {@code prize {…}} → {@link PrizeConfig}. REFERENCE.md §Prize. */
public final class PrizeAdapter
    extends SingleClosureAdapter<PrizeConfig, PrizeAdapter.PrizeBuilder> {

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

    /** {@code [Prize] PrizeMaxExist} — centiseconds (×10 → ms). */
    public void maxExist(final int centiseconds) {
      this.defaultDecayMs = Validators.centisecondsToMs("prize.maxExist", centiseconds);
    }

    /** {@code [Prize] PrizeMinExist} — centiseconds (×10 → ms); omit pins to maxExist. */
    public void minExist(final int centiseconds) {
      this.defaultMinDecayMs = Validators.centisecondsToMs("prize.minExist", centiseconds);
    }

    /** {@code [Prize] DeathPrizeTime} — centiseconds (×10 → ms); 0 disables death-drops. */
    public void deathPrizeTime(final int centiseconds) {
      this.deathPrizeTimeMs = Validators.centisecondsToMs("prize.deathPrizeTime", centiseconds);
    }

    /** {@code [Prize] PrizeNegativeFactor} — 1-in-N replace with {@code Dud}; 0 disables. */
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
