// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BrickConfig;

/**
 * Typed Groovy adapter for {@code brick.groovy} fragments. Parses a
 * {@code brick { … }} block into a {@link BrickConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * brick {
 *     span  7      // [Brick] BrickSpan (wall length in tiles)
 *     time  1000   // [Brick] BrickTime (centiseconds → ms ×10)
 * }
 * }</pre>
 */
public final class BrickAdapter
    extends SingleClosureAdapter<BrickConfig, BrickAdapter.BrickBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BrickAdapter INSTANCE = new BrickAdapter();

  private BrickAdapter() {
    super("brick", BrickConfig.DEFAULTS);
  }

  @Override
  protected BrickBuilder newBuilder() {
    return new BrickBuilder();
  }

  @Override
  public BrickConfig extract(final BrickBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code brick { ... }} block. */
  public static final class BrickBuilder {

    private int spanTiles = BrickConfig.DEFAULTS.spanTiles();
    private long timeMs = BrickConfig.DEFAULTS.timeMs();

    BrickBuilder() {}

    /** {@code [Brick] BrickSpan} — wall length in tiles. */
    public void span(final int value) {
      this.spanTiles = value;
    }

    /**
     * {@code [Brick] BrickTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void time(final int centiseconds) {
      this.timeMs = Validators.centisecondsToMs("brick.time", centiseconds);
    }

    BrickConfig build() {
      return new BrickConfig(spanTiles, timeMs);
    }
  }
}
