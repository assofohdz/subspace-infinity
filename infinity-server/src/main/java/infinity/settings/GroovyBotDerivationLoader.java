// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.BotDerivationConfig;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the {@code derivation { }} block of {@code engine-bot-ai.groovy} →
 * {@link BotDerivationConfig}. ADR-0014 committed open-work. Returns
 * {@link BotDerivationConfig#DEFAULTS} on any failure (logged). Sibling of
 * {@link GroovyBotSynergyLoader} / {@link GroovyBotRolesLoader} — same file, different block.
 */
public class GroovyBotDerivationLoader {

  public static final String DEFAULT_PATH = "/engine-bot-ai.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyBotDerivationLoader.class);
  private static final DerivationAdapter ADAPTER = new DerivationAdapter();

  public BotDerivationConfig load() {
    return load(DEFAULT_PATH);
  }

  public BotDerivationConfig load(final String classpathPath) {
    final BotDerivationConfig raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final BotDerivationConfig cfg = raw == null ? BotDerivationConfig.DEFAULTS : raw;
    if (cfg != BotDerivationConfig.DEFAULTS && log.isInfoEnabled()) {
      log.info(
          "Applied {} derivation: gravBombArea={}, burstArea={}, thorArea={}, sustainRechargeScale={}",
          classpathPath,
          cfg.gravBombArea(),
          cfg.burstArea(),
          cfg.thorArea(),
          cfg.sustainRechargeScale());
    }
    return cfg;
  }

  private static final class DerivationAdapter
      implements GroovySettingsAdapter<BotDerivationConfig, DerivationBuilder> {

    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public DerivationBuilder bind(final Binding binding) {
      final DerivationBuilder builder = new DerivationBuilder();
      binding.setVariable("derivation", new DerivationClosure(builder));
      // Same file also holds synergy { } / roles { } blocks; ignore them on the derivation pass.
      binding.setVariable("synergy", new IgnoringDslClosure());
      binding.setVariable("roles", new IgnoringDslClosure());
      return builder;
    }

    @Override
    public BotDerivationConfig extract(final DerivationBuilder accumulator) {
      return accumulator.build();
    }

    @Override
    public BotDerivationConfig empty() {
      return BotDerivationConfig.DEFAULTS;
    }
  }

  private static final class DerivationClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final transient DerivationBuilder builder;

    DerivationClosure(final DerivationBuilder builder) {
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

  /** DSL delegate for the {@code derivation { … }} body; public for Groovy dispatch. */
  public static final class DerivationBuilder {

    private double gravBombArea = BotDerivationConfig.DEFAULTS.gravBombArea();
    private double burstArea = BotDerivationConfig.DEFAULTS.burstArea();
    private double thorArea = BotDerivationConfig.DEFAULTS.thorArea();
    private double sustainRechargeScale = BotDerivationConfig.DEFAULTS.sustainRechargeScale();
    private double mobilitySpeedWeight = BotDerivationConfig.DEFAULTS.mobilitySpeedWeight();
    private double mobilityRotationWeight = BotDerivationConfig.DEFAULTS.mobilityRotationWeight();
    private double mobilityThrustWeight = BotDerivationConfig.DEFAULTS.mobilityThrustWeight();

    DerivationBuilder() {}

    /** Grav-bomb area-damage proxy: contributes {@code count × gravBombArea} to {@code areaRaw}. */
    public void gravBombArea(final Number value) {
      this.gravBombArea = validatedNonNeg("gravBombArea", value, this.gravBombArea);
    }

    /** Burst area-damage proxy: contributes {@code count × burstArea} to {@code areaRaw}. */
    public void burstArea(final Number value) {
      this.burstArea = validatedNonNeg("burstArea", value, this.burstArea);
    }

    /** Thor area-damage proxy: contributes {@code count × thorArea} to {@code areaRaw}. */
    public void thorArea(final Number value) {
      this.thorArea = validatedNonNeg("thorArea", value, this.thorArea);
    }

    /** Denominator scaling sustained DPS so the recharge-rate fraction reads as a 0..1ish multiplier. */
    public void sustainRechargeScale(final Number value) {
      this.sustainRechargeScale = validatedPositive("sustainRechargeScale", value, this.sustainRechargeScale);
    }

    /** Blend weight for ship.speed in the mobility composite (default 1.0). */
    public void mobilitySpeedWeight(final Number value) {
      this.mobilitySpeedWeight = validatedNonNeg("mobilitySpeedWeight", value, this.mobilitySpeedWeight);
    }

    /** Blend weight for ship.rotation in the mobility composite (default 1.0). */
    public void mobilityRotationWeight(final Number value) {
      this.mobilityRotationWeight =
          validatedNonNeg("mobilityRotationWeight", value, this.mobilityRotationWeight);
    }

    /** Blend weight for ship.thrust in the mobility composite (default 1.0). */
    public void mobilityThrustWeight(final Number value) {
      this.mobilityThrustWeight =
          validatedNonNeg("mobilityThrustWeight", value, this.mobilityThrustWeight);
    }

    private static double validatedNonNeg(final String name, final Number value, final double previous) {
      if (value == null) {
        return previous;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0) {
        throw new IllegalArgumentException(name + " must be a finite value >= 0; got " + value);
      }
      return v;
    }

    private static double validatedPositive(final String name, final Number value, final double previous) {
      if (value == null) {
        return previous;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
        throw new IllegalArgumentException(name + " must be a finite value > 0; got " + value);
      }
      return v;
    }

    BotDerivationConfig build() {
      return new BotDerivationConfig(
          gravBombArea,
          burstArea,
          thorArea,
          sustainRechargeScale,
          mobilitySpeedWeight,
          mobilityRotationWeight,
          mobilityThrustWeight);
    }
  }
}
