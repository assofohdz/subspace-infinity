// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.MineConfig;

/** Typed adapter for {@code mine {…}} → {@link MineConfig}. REFERENCE.md §Mine. Damage today uses per-ship {@code MineCost}. */
public final class MineAdapter extends SingleClosureAdapter<MineConfig, MineAdapter.MineBuilder> {

  public static final MineAdapter INSTANCE = new MineAdapter();

  private MineAdapter() {
    super("mine", MineConfig.DEFAULTS);
  }

  @Override
  protected MineBuilder newBuilder() {
    return new MineBuilder();
  }

  @Override
  public MineConfig extract(final MineBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code mine { ... }} block. */
  public static final class MineBuilder {

    private long decayMs = MineConfig.DEFAULTS.decayMs();

    MineBuilder() {}

    /** {@code [Mine] MineAliveTime} — centiseconds (×10 → ms). */
    public void aliveTime(final int centiseconds) {
      this.decayMs = Validators.centisecondsToMs("mine.aliveTime", centiseconds);
    }

    MineConfig build() {
      return new MineConfig(decayMs);
    }
  }
}
