// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.RepelConfig;

/** Typed adapter for {@code repel {…}} → {@link RepelConfig}. REFERENCE.md §Repel. */
public final class RepelAdapter
    extends SingleClosureAdapter<RepelConfig, RepelAdapter.RepelBuilder> {

  public static final RepelAdapter INSTANCE = new RepelAdapter();

  private RepelAdapter() {
    super("repel", RepelConfig.DEFAULTS);
  }

  @Override
  protected RepelBuilder newBuilder() {
    return new RepelBuilder();
  }

  @Override
  public RepelConfig extract(final RepelBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code repel { ... }} block. */
  public static final class RepelBuilder {

    private int speed = RepelConfig.DEFAULTS.speed();
    private long timeMs = RepelConfig.DEFAULTS.timeMs();
    private double distanceTiles = RepelConfig.DEFAULTS.distanceTiles();

    RepelBuilder() {}

    /** {@code [Repel] RepelSpeed} — repulsion speed applied to entities in range. */
    public void speed(final int value) {
      this.speed = value;
    }

    /** {@code [Repel] RepelTime} — centiseconds (×10 → ms). */
    public void time(final int centiseconds) {
      this.timeMs = Validators.centisecondsToMs("repel.time", centiseconds);
    }

    /**
     * {@code [Repel] RepelDistance} expressed in tiles (Infinity-native;
     * sim doesn't speak pixels). Effect radius applied to
     * {@link infinity.es.Repellable} bodies in range. SVS authors pixels
     * (512 px = 32 tiles at 16 px/tile); divide by 16 when porting.
     */
    public void distance(final Number value) {
      this.distanceTiles = Validators.finiteNonNegativeDouble("repel.distance", value);
    }

    RepelConfig build() {
      return new RepelConfig(speed, timeMs, distanceTiles);
    }
  }
}
