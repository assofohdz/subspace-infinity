// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.DecoyConfig;

/** Typed adapter for {@code decoy {…}} → {@link DecoyConfig}. REFERENCE.md §Misc {@code DecoyAliveTime}. */
public final class DecoyAdapter
    extends SingleClosureAdapter<DecoyConfig, DecoyAdapter.DecoyBuilder> {

  public static final DecoyAdapter INSTANCE = new DecoyAdapter();

  private DecoyAdapter() {
    super("decoy", DecoyConfig.DEFAULTS);
  }

  @Override
  protected DecoyBuilder newBuilder() {
    return new DecoyBuilder();
  }

  @Override
  public DecoyConfig extract(final DecoyBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code decoy { ... }} block. */
  public static final class DecoyBuilder {

    private long aliveTimeMs = DecoyConfig.DEFAULTS.aliveTimeMs();

    DecoyBuilder() {}

    /** {@code [Misc] DecoyAliveTime} — centiseconds (×10 → ms). */
    public void aliveTime(final int centiseconds) {
      this.aliveTimeMs = Validators.centisecondsToMs("decoy.aliveTime", centiseconds);
    }

    DecoyConfig build() {
      return new DecoyConfig(aliveTimeMs);
    }
  }
}
