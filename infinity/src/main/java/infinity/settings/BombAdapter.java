// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.BombConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code bomb.groovy} fragments. Parses a
 * {@code bomb { … }} block into a {@link BombConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadBomb} that read {@code [Bomb]} keys
 * from the merged INI store.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * bomb {
 *     damageLevel       750    // [Bomb] BombDamageLevel
 *     aliveTimeCs       6000   // [Bomb] BombAliveTime in centiseconds (×10 → ms)
 *     explodeRadius     5      // [Bomb] BombExplodePixels expressed in tiles /
 *                              // world units (Infinity-native; SVS 80 px = 5
 *                              // tiles at 16 px/tile). Per-level multiplier
 *                              // applied at fire time (L1=1×, L2=2×, L3=3×,
 *                              // L4=4×). See slice 9a.
 *     proximityDistance 3      // [Bomb] ProximityDistance — proximity-arm
 *                              // radius in tiles, base (L1). Per-level
 *                              // additive (+1 per level above L1) at fire
 *                              // time. Value 0 disables. See slice 9b.
 *     explodeDelayCs    10     // [Bomb] BombExplodeDelay in centiseconds
 *                              // (×10 → ms). Fuse delay after arming. Value
 *                              // 0 disables. See slice 9b.
 *     bombSafety        true   // [Bomb] BombSafety — fire-time gate that
 *                              // rejects bomb fire when an enemy sits
 *                              // inside the firing ship's effective
 *                              // proximity-arm radius. Auto-no-ops when
 *                              // proximityDistance == 0. See slice
 *                              // 9c-BombSafety.
 *     jitterTimeCs      100    // [Bomb] JitterTime in centiseconds (×10 →
 *                              // ms). Screen-jitter duration applied to
 *                              // each FF-passing bomb victim. 0 disables.
 *                              // See slice 9c-JitterTime.
 * }
 * }</pre>
 *
 * <p>The remaining Subspace {@code [Bomb]} keys (EBombShutdownTime,
 * EBombDamagePercent, BBombDamagePercent) aren't in {@link BombConfig}
 * today — they belong to future EMP / bouncing-bomb work. The adapter only
 * exposes fields that have a typed config + active consumer.
 */
public final class BombAdapter
    implements GroovySettingsAdapter<BombConfig, BombAdapter.BombBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BombAdapter INSTANCE = new BombAdapter();

  private BombAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public BombBuilder bind(final Binding binding) {
    final BombBuilder builder = new BombBuilder();
    binding.setVariable("bomb", new BombClosure(builder));
    return builder;
  }

  @Override
  public BombConfig extract(final BombBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public BombConfig empty() {
    return BombConfig.DEFAULTS;
  }

  /** Bound to the {@code bomb} variable in the script. */
  private static final class BombClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final BombBuilder builder;

    BombClosure(final BombBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(builder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
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

    /**
     * {@code [Bomb] BombAliveTime} authored in <em>centiseconds</em> (Subspace
     * VIE convention); the adapter multiplies by 10 to store milliseconds.
     * The {@code Cs} suffix on the DSL setter name makes the input unit
     * explicit at the call site.
     */
    public void aliveTimeCs(final int centiseconds) {
      this.decayMs = centiseconds * 10L;
    }

    /**
     * {@code [Bomb] BombExplodePixels} expressed in <em>tiles / world units</em>
     * (Infinity-native; the simulation layer doesn't speak in pixels). L1
     * base blast radius — the fire-time projection in {@code WeaponsSystem}
     * applies the per-level multiplier (L1×1, L2×2, L3×3, L4×4) when
     * stamping {@code SplashDamage} on the bomb entity. Subspace canon
     * authors {@code BombExplodePixels} in pixels (80 px = 5 tiles at the
     * 16 px/tile rate); operators porting an SVS server.cfg divide by 16.
     */
    public void explodeRadius(final Number value) {
      if (value == null) {
        throw new IllegalArgumentException("explodeRadius requires a number");
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0) {
        throw new IllegalArgumentException(
            "explodeRadius must be a finite value >= 0; got " + value);
      }
      this.explodeRadius = v;
    }

    /**
     * {@code [Bomb] ProximityDistance} — base (L1) proximity-arm radius in
     * <em>tiles</em>. Per-level additive scaling (+1 per level above L1) is
     * applied at fire time. Set to 0 to disable proximity arming on this
     * arena (bombs fall back to direct-contact detonation).
     */
    public void proximityDistance(final int tiles) {
      if (tiles < 0) {
        throw new IllegalArgumentException(
            "proximityDistance must be >= 0; got " + tiles);
      }
      this.proximityDistance = tiles;
    }

    /**
     * {@code [Bomb] BombExplodeDelay} authored in <em>centiseconds</em>
     * (Subspace VIE convention); adapter ×10 to store milliseconds. Fuse
     * delay after a bomb arms via proximity. Set to 0 to disable proximity
     * arming (must be paired with {@code proximityDistance 0}).
     */
    public void explodeDelayCs(final int centiseconds) {
      if (centiseconds < 0) {
        throw new IllegalArgumentException(
            "explodeDelayCs must be >= 0; got " + centiseconds);
      }
      this.explodeDelayMs = centiseconds * 10L;
    }

    /**
     * {@code [Bomb] BombSafety} — when {@code true}, fire-time scan rejects
     * bomb fire if an enemy sits inside the firing ship's effective
     * proximity-arm radius. Subspace canon is binary (0/1); the typed DSL
     * accepts native Groovy booleans. Auto-no-ops when
     * {@link #proximityDistance} is 0.
     */
    public void bombSafety(final boolean enabled) {
      this.bombSafety = enabled;
    }

    /**
     * {@code [Bomb] JitterTime} authored in <em>centiseconds</em> (Subspace
     * VIE convention); adapter ×10 to store milliseconds. Screen-jitter
     * duration applied to each bomb-damage victim that passes the FF gate.
     * Set to 0 to disable jitter on this arena. Flat across bomb levels —
     * REFERENCE.md does not specify per-level scaling.
     */
    public void jitterTimeCs(final int centiseconds) {
      if (centiseconds < 0) {
        throw new IllegalArgumentException(
            "jitterTimeCs must be >= 0; got " + centiseconds);
      }
      this.jitterTimeMs = centiseconds * 10L;
    }

    /**
     * Slice S5 — when {@code true} (default), the projectile-spawn projection
     * stamps {@link infinity.es.Repellable} on this arena's bombs so a repel
     * within range pushes them away. Subspace canon: bombs are repellable
     * (the iconic defensive use case). Set {@code false} to opt this arena
     * out of bomb-repel.
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
