// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.EngineConfig;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the engine-scope Groovy config and returns a typed
 * {@link EngineConfig}. Thin facade over {@link GroovySettingsHost} —
 * the host owns I/O, hardening and error handling; this class supplies
 * the {@code engine { … }} DSL.
 *
 * <p>Engine tier is the fourth config scope above preset / arena / zone.
 * Loaded once at server startup from {@code engine.groovy} on the
 * classpath (packaged inside the jar — these are developer-tuned, not
 * operator-tuned). Live-reload deferred; restart-tuned for now.
 *
 * <p>Failure handling mirrors {@link GroovyZoneLoader}: any failure
 * (missing file, parse error, eval error) logs a warning and returns
 * {@link EngineConfig#DEFAULTS}. Callers never see an exception.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * engine {
 *     subspaceVelocityScale 0.01
 *     maxProjectileSpeedJme 100
 *     bulletRadius 0.125
 * }
 * }</pre>
 */
public final class GroovyEngineLoader {

  /** Default classpath path for the engine config. */
  public static final String DEFAULT_PATH = "/engine.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyEngineLoader.class);
  private static final EngineAdapter ADAPTER = new EngineAdapter();

  /**
   * Load and parse {@link #DEFAULT_PATH}. {@link EngineConfig#DEFAULTS} if the
   * file is missing or fails to evaluate.
   */
  public EngineConfig load() {
    return load(DEFAULT_PATH);
  }

  /** Same contract as {@link #load()} but with a caller-supplied classpath path. */
  public EngineConfig load(final String classpathPath) {
    final EngineConfig raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final EngineConfig cfg = raw == null ? EngineConfig.DEFAULTS : raw;
    if (cfg != EngineConfig.DEFAULTS && log.isInfoEnabled()) {
      log.info(
          "Applied {}: subspaceVelocityScale={}, maxProjectileSpeedJme={}",
          classpathPath,
          cfg.subspaceVelocityScale(),
          cfg.maxProjectileSpeedJme());
    }
    return cfg;
  }

  /** Adapter holding the {@code engine { … }} DSL semantics. */
  private static final class EngineAdapter
      implements GroovySettingsAdapter<EngineConfig, EngineConfigBuilder> {

    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public EngineConfigBuilder bind(final Binding binding) {
      final EngineConfigBuilder builder = new EngineConfigBuilder();
      binding.setVariable("engine", new EngineClosure(builder));
      return builder;
    }

    @Override
    public EngineConfig extract(final EngineConfigBuilder accumulator) {
      return accumulator.build();
    }

    @Override
    public EngineConfig empty() {
      return EngineConfig.DEFAULTS;
    }
  }

  /** Bound to the {@code engine} variable in the script. */
  private static final class EngineClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final EngineConfigBuilder builder;

    EngineClosure(final EngineConfigBuilder builder) {
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

  /** Delegate for the {@code engine { ... }} block. */
  public static final class EngineConfigBuilder {

    private double subspaceVelocityScale = EngineConfig.DEFAULTS.subspaceVelocityScale();
    private double maxProjectileSpeedJme = EngineConfig.DEFAULTS.maxProjectileSpeedJme();
    private double shipMaxSpeedScale = EngineConfig.DEFAULTS.shipMaxSpeedScale();
    private double bombThrustScale = EngineConfig.DEFAULTS.bombThrustScale();
    private double bulletRadius = EngineConfig.DEFAULTS.bulletRadius();
    private double bombRadius = EngineConfig.DEFAULTS.bombRadius();
    private double mineRadius = EngineConfig.DEFAULTS.mineRadius();
    private double thorRadius = EngineConfig.DEFAULTS.thorRadius();
    private double prizeRadius = EngineConfig.DEFAULTS.prizeRadius();
    private double burstRadius = EngineConfig.DEFAULTS.burstRadius();
    private double repelRadius = EngineConfig.DEFAULTS.repelRadius();
    private double over1Radius = EngineConfig.DEFAULTS.over1Radius();
    private double over2Radius = EngineConfig.DEFAULTS.over2Radius();
    private double over5Radius = EngineConfig.DEFAULTS.over5Radius();
    private double flagRadius = EngineConfig.DEFAULTS.flagRadius();
    private double shipRadius = EngineConfig.DEFAULTS.shipRadius();

    // Package-private so unit tests can build configs without standing up
    // the full GroovyShell pipeline.
    EngineConfigBuilder() {}

    /**
     * {@code subspaceVelocityScale 0.01} — multiplier applied at fire time
     * to per-ship Subspace velocity values to land in jME world units.
     * Must be positive (negative would flip every projectile direction
     * globally — clearly not intended at this knob's scope).
     */
    public void subspaceVelocityScale(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "subspaceVelocityScale must be a finite value > 0; got " + value);
      }
      this.subspaceVelocityScale = v;
    }

    /**
     * {@code maxProjectileSpeedJme 100} — post-translation cap (jME world
     * units / sec). Clamps absolute projectile speed to keep stray legacy
     * Subspace values from producing physics-breaking velocities. Must be
     * positive.
     */
    public void maxProjectileSpeedJme(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "maxProjectileSpeedJme must be a finite value > 0; got " + value);
      }
      this.maxProjectileSpeedJme = v;
    }

    /**
     * {@code shipMaxSpeedScale 0.025} — multiplier applied to a ship's
     * {@code Speed} component (raw Subspace velocity units) at
     * {@code PlayerDriver} consumer time to derive the jME max-speed cap.
     * Slice S1-cal. Must be positive (negative would invert the cap
     * direction, which has no meaningful interpretation).
     */
    public void shipMaxSpeedScale(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "shipMaxSpeedScale must be a finite value > 0; got " + value);
      }
      this.shipMaxSpeedScale = v;
    }

    /**
     * {@code bombThrustScale 0.005} — multiplier applied to per-ship
     * {@code BombThrust} at {@code WeaponsSystem.applyBombRecoil} time.
     * Slice S2-cal. Must be positive.
     */
    public void bombThrustScale(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "bombThrustScale must be a finite value > 0; got " + value);
      }
      this.bombThrustScale = v;
    }

    /**
     * {@code bulletRadius 0.125} — bullet collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void bulletRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "bulletRadius must be a finite value > 0; got " + value);
      }
      this.bulletRadius = v;
    }

    /**
     * {@code bombRadius 0.5} — bomb collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void bombRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "bombRadius must be a finite value > 0; got " + value);
      }
      this.bombRadius = v;
    }

    /**
     * {@code mineRadius 0.5} — mine collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void mineRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "mineRadius must be a finite value > 0; got " + value);
      }
      this.mineRadius = v;
    }

    /**
     * {@code thorRadius 0.5} — thor collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void thorRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "thorRadius must be a finite value > 0; got " + value);
      }
      this.thorRadius = v;
    }

    /**
     * {@code prizeRadius 0.5} — prize collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void prizeRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "prizeRadius must be a finite value > 0; got " + value);
      }
      this.prizeRadius = v;
    }

    /**
     * {@code burstRadius 0.125} — burst projectile collision radius in jME
     * world units. Slice projectile-radius-pattern4.
     */
    public void burstRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "burstRadius must be a finite value > 0; got " + value);
      }
      this.burstRadius = v;
    }

    /**
     * {@code repelRadius 0.125} — repel collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void repelRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "repelRadius must be a finite value > 0; got " + value);
      }
      this.repelRadius = v;
    }

    /**
     * {@code over1Radius 0.5} — generic decoration "Over1" collision radius
     * in jME world units. Slice projectile-radius-pattern4.
     */
    public void over1Radius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "over1Radius must be a finite value > 0; got " + value);
      }
      this.over1Radius = v;
    }

    /**
     * {@code over2Radius 1.0} — generic decoration "Over2" collision radius
     * in jME world units. Slice projectile-radius-pattern4.
     */
    public void over2Radius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "over2Radius must be a finite value > 0; got " + value);
      }
      this.over2Radius = v;
    }

    /**
     * {@code over5Radius 0.1} — generic decoration "Over5" collision radius
     * in jME world units. Slice projectile-radius-pattern4.
     */
    public void over5Radius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "over5Radius must be a finite value > 0; got " + value);
      }
      this.over5Radius = v;
    }

    /**
     * {@code flagRadius 0.5} — flag collision radius in jME world units.
     * Slice projectile-radius-pattern4.
     */
    public void flagRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "flagRadius must be a finite value > 0; got " + value);
      }
      this.flagRadius = v;
    }

    /**
     * {@code shipRadius 1.0} — ship collision radius in jME world units.
     * Slice s6-ship-radius.
     */
    public void shipRadius(final Number value) {
      if (value == null) {
        return;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(
            "shipRadius must be a finite value > 0; got " + value);
      }
      this.shipRadius = v;
    }

    EngineConfig build() {
      return new EngineConfig(
          subspaceVelocityScale,
          maxProjectileSpeedJme,
          shipMaxSpeedScale,
          bombThrustScale,
          bulletRadius,
          bombRadius,
          mineRadius,
          thorRadius,
          prizeRadius,
          burstRadius,
          repelRadius,
          over1Radius,
          over2Radius,
          over5Radius,
          flagRadius,
          shipRadius);
    }
  }
}
