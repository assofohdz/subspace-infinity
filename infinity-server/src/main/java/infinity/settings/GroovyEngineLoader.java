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

/** Evaluates {@code engine.groovy} → {@link EngineConfig}. Returns {@link EngineConfig#DEFAULTS} on any failure (logged). */
public class GroovyEngineLoader {

  public static final String DEFAULT_PATH = "/engine.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyEngineLoader.class);
  private static final EngineAdapter ADAPTER = new EngineAdapter();

  public EngineConfig load() {
    return load(DEFAULT_PATH);
  }

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

  /** Delegate for {@code engine{…}}. */
  // CC=98 from per-key parser methods; intentional fan-out
  @SuppressWarnings("PMD.CyclomaticComplexity")
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

    EngineConfigBuilder() {}

    /** Multiplier on Subspace velocity → jME world units at fire time. */
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

    /** Post-translation cap on projectile speed (jME world units/sec). */
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

    /** Multiplier on ship {@code Speed} → jME max-speed cap at {@code PlayerDriver}. */
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

    /** Multiplier on per-ship {@code BombThrust} at {@code WeaponsSystem.applyBombRecoil}. */
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

    /** Bullet collision radius in jME world units. */
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

    /** Bomb collision radius in jME world units. */
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

    /** Mine collision radius in jME world units. */
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

    /** Thor collision radius in jME world units. */
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

    /** Prize collision radius in jME world units. */
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

    /** Burst projectile collision radius in jME world units. */
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

    /** Repel collision radius in jME world units. */
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

    /** Decoration "Over1" collision radius in jME world units. */
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

    /** Decoration "Over2" collision radius in jME world units. */
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

    /** Decoration "Over5" collision radius in jME world units. */
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

    /** Flag collision radius in jME world units. */
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

    /** Ship collision radius in jME world units. */
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
