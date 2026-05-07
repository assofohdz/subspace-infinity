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
    if (cfg != EngineConfig.DEFAULTS) {
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

    EngineConfig build() {
      return new EngineConfig(
          subspaceVelocityScale, maxProjectileSpeedJme, shipMaxSpeedScale, bombThrustScale);
    }
  }
}
