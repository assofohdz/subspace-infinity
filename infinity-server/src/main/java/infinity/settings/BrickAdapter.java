// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BrickConfig;

/** Typed adapter for {@code brick {…}} → {@link BrickConfig}. REFERENCE.md §Brick. */
public final class BrickAdapter
    extends SingleClosureAdapter<BrickConfig, BrickAdapter.BrickBuilder> {

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

    /** {@code [Brick] BrickTime} — centiseconds (×10 → ms). */
    public void time(final int centiseconds) {
      this.timeMs = Validators.centisecondsToMs("brick.time", centiseconds);
    }

    BrickConfig build() {
      return new BrickConfig(spanTiles, timeMs);
    }
  }
}
