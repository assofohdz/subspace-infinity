// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.capability.CapabilityProfile;
import infinity.ai.capability.SynergyRule;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates {@code engine-bot-ai.groovy} → {@link BotSynergyTable}, adapting each behaviour's
 * {@code requires}/{@code bonus} Groovy closures into the api functional interfaces (so api/ stays
 * Groovy-free). Returns an empty table on any failure (logged). See ADR-0014.
 */
public class GroovyBotSynergyLoader {

  public static final String DEFAULT_PATH = "/engine-bot-ai.groovy";
  private static final Logger log = LoggerFactory.getLogger(GroovyBotSynergyLoader.class);
  private static final SynergyAdapter ADAPTER = new SynergyAdapter();

  public BotSynergyTable load() {
    return load(DEFAULT_PATH);
  }

  public BotSynergyTable load(final String classpathPath) {
    final BotSynergyTable raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final BotSynergyTable table = raw == null ? new BotSynergyTable(Map.of()) : raw;
    if (!table.rules().isEmpty() && log.isInfoEnabled()) {
      log.info("Applied {}: {} synergy rules", classpathPath, table.rules().size());
    }
    return table;
  }

  /** Mutable accumulator gathering both the synergy rules and the per-behaviour fit coefficients. */
  private static final class Collected {
    final Map<String, SynergyRule> rules = new LinkedHashMap<>();
    final Map<String, Map<String, Double>> fit = new LinkedHashMap<>();
  }

  private static final class SynergyAdapter
      implements GroovySettingsAdapter<BotSynergyTable, Collected> {
    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public Collected bind(final Binding binding) {
      final Collected collected = new Collected();
      binding.setVariable("synergy", new SynergyClosure(collected));
      // Same file also holds a roles { } block (GroovyBotRolesLoader); ignore it on the synergy pass.
      binding.setVariable("roles", new IgnoringDslClosure());
      return collected;
    }

    @Override
    public BotSynergyTable extract(final Collected collected) {
      return new BotSynergyTable(collected.rules, collected.fit);
    }

    @Override
    public BotSynergyTable empty() {
      return new BotSynergyTable(Map.of());
    }
  }

  private static final class SynergyClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final transient Collected collected;

    SynergyClosure(final Collected collected) {
      super(null);
      this.collected = collected;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(new BehaviourCollector(this.collected));
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /** DSL delegate for the {@code synergy { ... }} body; public so Groovy can dispatch to it. */
  public static final class BehaviourCollector {
    private final Collected collected;

    BehaviourCollector(final Collected collected) {
      this.collected = collected;
    }

    public void behaviour(final String name, final Closure<?> spec) {
      final RuleSpec rs = new RuleSpec();
      spec.setDelegate(rs);
      spec.setResolveStrategy(Closure.DELEGATE_FIRST);
      spec.call();
      this.collected.rules.put(name, rs.toRule());
      final Map<String, Double> fit = rs.toFit();
      if (!fit.isEmpty()) {
        this.collected.fit.put(name, fit);
      }
    }
  }

  /** DSL delegate for one behaviour's {@code requires}/{@code bonus}/{@code fit}; public for Groovy dispatch. */
  public static final class RuleSpec {
    @Nullable private transient Closure<?> requires;
    @Nullable private transient Closure<?> bonus;
    @Nullable private transient Closure<?> fit;

    public void requires(final Closure<?> c) {
      this.requires = c;
    }

    public void bonus(final Closure<?> c) {
      this.bonus = c;
    }

    public void fit(final Closure<?> c) {
      this.fit = c;
    }

    /** The {@code fit { [name: coeff, ...] }} closure as a coefficient map; empty when omitted. */
    @SuppressWarnings("unchecked")
    Map<String, Double> toFit() {
      final Closure<?> f = this.fit;
      if (f == null) {
        return Map.of();
      }
      final Map<String, Double> out = new LinkedHashMap<>();
      final Object result = f.call();
      if (result instanceof Map<?, ?> map) {
        for (final Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
          out.put(String.valueOf(e.getKey()), ((Number) e.getValue()).doubleValue());
        }
      }
      return out;
    }

    SynergyRule toRule() {
      final Closure<?> req = this.requires;
      final Closure<?> bon = this.bonus;
      final Predicate<CapabilityProfile> gate =
          req == null ? p -> true : p -> truthy(req.call(p));
      final ToDoubleFunction<CapabilityProfile> weight =
          bon == null ? p -> 0.0 : p -> ((Number) bon.call(p)).doubleValue();
      return new SynergyRule(gate, weight);
    }

    private static boolean truthy(final Object o) {
      if (o instanceof Boolean) {
        return (Boolean) o;
      }
      if (o instanceof Number) {
        return ((Number) o).doubleValue() != 0;
      }
      return o != null;
    }
  }
}
