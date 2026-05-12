// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BombConfig;

/** Typed adapter for {@code bomb {…}} blocks → {@link BombConfig}. REFERENCE.md §Bomb. */
public final class BombAdapter extends SingleClosureAdapter<BombConfig, BombAdapter.BombBuilder> {

  public static final BombAdapter INSTANCE = new BombAdapter();

  private BombAdapter() {
    super("bomb", BombConfig.DEFAULTS);
  }

  @Override
  protected BombBuilder newBuilder() {
    return new BombBuilder();
  }

  @Override
  public BombConfig extract(final BombBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code bomb { ... }} block. */
  public static final class BombBuilder {

    private int damage = BombConfig.DEFAULTS.damage();
    private long decayMs = BombConfig.DEFAULTS.decayMs();
    private double explodeRadius = BombConfig.DEFAULTS.explodeRadius();
    private int proximityDistance = BombConfig.DEFAULTS.proximityDistance();
    private long explodeDelayMs = BombConfig.DEFAULTS.explodeDelayMs();
    private boolean bombSafety = BombConfig.DEFAULTS.bombSafety();
    private long jitterTimeMs = BombConfig.DEFAULTS.jitterTimeMs();
    private boolean repellable = BombConfig.DEFAULTS.repellable();

    BombBuilder() {}

    /** {@code [Bomb] BombDamageLevel} — damage applied on detonation. */
    public void damageLevel(final int value) {
      this.damage = value;
    }

    /** {@code [Bomb] BombAliveTime} — centiseconds (×10 → ms at the loader boundary). {@code Cs} suffix flags the input unit at call sites. */
    public void aliveTimeCs(final int centiseconds) {
      this.decayMs = Validators.centisecondsToMs("bomb.aliveTimeCs", centiseconds);
    }

    /**
     * {@code [Bomb] BombExplodePixels} expressed in tiles (Infinity-native;
     * sim doesn't speak pixels). L1 base; fire-time projection in
     * {@code WeaponsSystem} applies the per-level multiplier (L1×1 L2×2 L3×3
     * L4×4) onto {@code SplashDamage}. SVS authors pixels (80 px = 5 tiles
     * at 16 px/tile); divide by 16 when porting a server.cfg.
     */
    public void explodeRadius(final Number value) {
      this.explodeRadius = Validators.finiteNonNegativeDouble("explodeRadius", value);
    }

    /**
     * {@code [Bomb] ProximityDistance} — L1 base in tiles; per-level additive
     * (+1 per level above L1) at fire time. {@code 0} disables prox-arming
     * (bombs fall back to direct-contact detonation).
     */
    public void proximityDistance(final int tiles) {
      this.proximityDistance = Validators.nonNegativeInt("proximityDistance", tiles);
    }

    /** {@code [Bomb] BombExplodeDelay} — centiseconds (×10 → ms); fuse delay after arming; 0 disables. */
    public void explodeDelayCs(final int centiseconds) {
      this.explodeDelayMs = Validators.centisecondsToMs("explodeDelayCs", centiseconds);
    }

    /** {@code [Bomb] BombSafety} — rejects fire when an enemy sits inside proximity range; no-op when {@link #proximityDistance} is 0. */
    public void bombSafety(final boolean enabled) {
      this.bombSafety = enabled;
    }

    /** {@code [Bomb] JitterTime} — centiseconds (×10 → ms); screen-jitter applied to FF-passing victims; 0 disables. */
    public void jitterTimeCs(final int centiseconds) {
      this.jitterTimeMs = Validators.centisecondsToMs("jitterTimeCs", centiseconds);
    }

    /**
     * When {@code true} (default), spawn projection stamps {@link infinity.es.Repellable} on bombs.
     */
    public void repellable(final boolean enabled) {
      this.repellable = enabled;
    }

    BombConfig build() {
      return new BombConfig(
          damage,
          decayMs,
          explodeRadius,
          proximityDistance,
          explodeDelayMs,
          bombSafety,
          jitterTimeMs,
          repellable);
    }
  }
}
