// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.DecoyConfig;

/**
 * Typed Groovy adapter for {@code decoy.groovy} fragments. Parses a
 * {@code decoy { … }} block into a {@link DecoyConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * decoy {
 *     aliveTime  3000   // [Misc] DecoyAliveTime (centiseconds → ms ×10)
 * }
 * }</pre>
 */
public final class DecoyAdapter
    extends SingleClosureAdapter<DecoyConfig, DecoyAdapter.DecoyBuilder> {

  /** Stateless; safe to share across calls. */
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

    /**
     * {@code [Misc] DecoyAliveTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void aliveTime(final int centiseconds) {
      this.aliveTimeMs = Validators.centisecondsToMs("decoy.aliveTime", centiseconds);
    }

    DecoyConfig build() {
      return new DecoyConfig(aliveTimeMs);
    }
  }
}
