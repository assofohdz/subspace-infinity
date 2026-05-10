// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.RepelConfig;

/**
 * Typed Groovy adapter for {@code repel.groovy} fragments. Parses a
 * {@code repel { … }} block into a {@link RepelConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadRepel}.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * repel {
 *     speed     5000    // [Repel] RepelSpeed (raw Subspace velocity units)
 *     time      225     // [Repel] RepelTime (centiseconds → ms ×10)
 *     distance  512     // [Repel] RepelDistance (Subspace pixels)
 * }
 * }</pre>
 */
public final class RepelAdapter
    extends SingleClosureAdapter<RepelConfig, RepelAdapter.RepelBuilder> {

  /** Stateless; safe to share across calls. */
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
    private int distancePixels = RepelConfig.DEFAULTS.distancePixels();

    RepelBuilder() {}

    /** {@code [Repel] RepelSpeed} — repulsion speed applied to entities in range. */
    public void speed(final int value) {
      this.speed = value;
    }

    /**
     * {@code [Repel] RepelTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void time(final int centiseconds) {
      this.timeMs = Validators.centisecondsToMs("repel.time", centiseconds);
    }

    /** {@code [Repel] RepelDistance} — effect radius in Subspace pixels. */
    public void distance(final int value) {
      this.distancePixels = value;
    }

    RepelConfig build() {
      return new RepelConfig(speed, timeMs, distancePixels);
    }
  }
}
