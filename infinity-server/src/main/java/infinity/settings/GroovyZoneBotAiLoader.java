// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.ZoneBotAiConfig;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Evaluates {@code zone-bot-ai.groovy} → {@link ZoneBotAiConfig}. Returns {@link ZoneBotAiConfig#DEFAULTS} on any failure (logged). See ADR-0013 / ADR-0014. */
public class GroovyZoneBotAiLoader {

  public static final String DEFAULT_PATH = "/zone-bot-ai.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyZoneBotAiLoader.class);
  private static final ZoneBotAiAdapter ADAPTER = new ZoneBotAiAdapter();

  public ZoneBotAiConfig load() {
    return load(DEFAULT_PATH);
  }

  public ZoneBotAiConfig load(final String classpathPath) {
    final ZoneBotAiConfig raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final ZoneBotAiConfig cfg = raw == null ? ZoneBotAiConfig.DEFAULTS : raw;
    if (cfg != ZoneBotAiConfig.DEFAULTS && log.isInfoEnabled()) {
      log.info(
          "Applied {}: plannerCadenceMillis={}, stickinessMargin={}, minFraction={}",
          classpathPath,
          cfg.plannerCadenceMillis(),
          cfg.stickinessMargin(),
          cfg.minFraction());
    }
    return cfg;
  }

  private static final class ZoneBotAiAdapter
      implements GroovySettingsAdapter<ZoneBotAiConfig, ZoneBotAiConfigBuilder> {

    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public ZoneBotAiConfigBuilder bind(final Binding binding) {
      final ZoneBotAiConfigBuilder builder = new ZoneBotAiConfigBuilder();
      binding.setVariable("botAi", new BotAiClosure(builder));
      return builder;
    }

    @Override
    public ZoneBotAiConfig extract(final ZoneBotAiConfigBuilder accumulator) {
      return accumulator.build();
    }

    @Override
    public ZoneBotAiConfig empty() {
      return ZoneBotAiConfig.DEFAULTS;
    }
  }

  private static final class BotAiClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient ZoneBotAiConfigBuilder builder;

    BotAiClosure(final ZoneBotAiConfigBuilder builder) {
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

  /** Delegate for {@code botAi{…}}. */
  public static final class ZoneBotAiConfigBuilder {

    private long plannerCadenceMillis = ZoneBotAiConfig.DEFAULTS.plannerCadenceMillis();
    private double stickinessMargin = ZoneBotAiConfig.DEFAULTS.stickinessMargin();
    private double minFraction = ZoneBotAiConfig.DEFAULTS.minFraction();
    private double minBehaviourWeight = ZoneBotAiConfig.DEFAULTS.minBehaviourWeight();

    ZoneBotAiConfigBuilder() {}

    /** Min wall-time (ms) between planner re-selects; must be {@code > 0}. */
    public void plannerCadenceMillis(final Number value) {
      if (value == null) {
        return;
      }
      final long v = value.longValue();
      if (v <= 0L) {
        throw new IllegalArgumentException("plannerCadenceMillis must be > 0; got " + value);
      }
      this.plannerCadenceMillis = v;
    }

    /** Additive preempt margin in weighted-score units; {@code [0,1]}. */
    public void stickinessMargin(final Number value) {
      this.stickinessMargin = unitFraction("stickinessMargin", value, this.stickinessMargin);
    }

    /** Adaptive enumeration floor as a fraction of the top weight; {@code (0,1]}. */
    public void minFraction(final Number value) {
      this.minFraction = unitFraction("minFraction", value, this.minFraction);
    }

    /** Absolute weight floor dropping near-zero synergy bonuses; {@code [0,1]}. */
    public void minBehaviourWeight(final Number value) {
      this.minBehaviourWeight = unitFraction("minBehaviourWeight", value, this.minBehaviourWeight);
    }

    private static double unitFraction(
        final String key, final Number value, final double current) {
      if (value == null) {
        return current;
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0 || v > 1.0) {
        throw new IllegalArgumentException(key + " must be in [0,1]; got " + value);
      }
      return v;
    }

    ZoneBotAiConfig build() {
      return new ZoneBotAiConfig(
          plannerCadenceMillis, stickinessMargin, minFraction, minBehaviourWeight);
    }
  }
}
