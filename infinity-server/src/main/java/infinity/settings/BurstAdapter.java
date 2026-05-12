// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BurstFireConfig;

/** Typed adapter for {@code burst {…}} → {@link BurstFireConfig}. REFERENCE.md §Burst. */
public final class BurstAdapter
    extends SingleClosureAdapter<BurstFireConfig, BurstAdapter.BurstBuilder> {

  public static final BurstAdapter INSTANCE = new BurstAdapter();

  private BurstAdapter() {
    super("burst", BurstFireConfig.DEFAULTS);
  }

  @Override
  protected BurstBuilder newBuilder() {
    return new BurstBuilder();
  }

  @Override
  public BurstFireConfig extract(final BurstBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code burst { ... }} block. */
  public static final class BurstBuilder {

    private int damage = BurstFireConfig.DEFAULTS.damage();

    BurstBuilder() {}

    /** {@code [Burst] BurstDamageLevel} — damage applied on burst-bullet hit. */
    public void damageLevel(final int value) {
      this.damage = value;
    }

    BurstFireConfig build() {
      return new BurstFireConfig(
          BurstFireConfig.DEFAULTS.projectileCount(),
          BurstFireConfig.DEFAULTS.decayMs(),
          damage);
    }
  }
}
