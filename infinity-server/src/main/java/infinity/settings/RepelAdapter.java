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
 *     distance  32      // [Repel] RepelDistance expressed in tiles / world units
 *                       // (Subspace canon authors pixels at 16 px/tile, e.g. 512 px = 32 tiles)
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
    private double distanceTiles = RepelConfig.DEFAULTS.distanceTiles();

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

    /**
     * {@code [Repel] RepelDistance} expressed in <em>tiles / world units</em>
     * (Infinity-native; the simulation layer doesn't speak in pixels).
     * Effect radius applied to {@link infinity.es.Repellable} bodies in
     * range. Subspace canon authors {@code RepelDistance} in pixels
     * (512 px = 32 tiles at the 16 px/tile rate); operators porting an SVS
     * server.cfg divide by 16. Mirrors the {@code BombConfig.explodeRadius}
     * precedent (slice 9a).
     */
    public void distance(final Number value) {
      this.distanceTiles = Validators.finiteNonNegativeDouble("repel.distance", value);
    }

    RepelConfig build() {
      return new RepelConfig(speed, timeMs, distanceTiles);
    }
  }
}
