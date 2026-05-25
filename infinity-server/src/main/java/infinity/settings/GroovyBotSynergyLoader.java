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

  private static final class SynergyAdapter
      implements GroovySettingsAdapter<BotSynergyTable, Map<String, SynergyRule>> {
    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public Map<String, SynergyRule> bind(final Binding binding) {
      final Map<String, SynergyRule> rules = new LinkedHashMap<>();
      binding.setVariable("synergy", new SynergyClosure(rules));
      return rules;
    }

    @Override
    public BotSynergyTable extract(final Map<String, SynergyRule> rules) {
      return new BotSynergyTable(rules);
    }

    @Override
    public BotSynergyTable empty() {
      return new BotSynergyTable(Map.of());
    }
  }

  private static final class SynergyClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final transient Map<String, SynergyRule> rules;

    SynergyClosure(final Map<String, SynergyRule> rules) {
      super(null);
      this.rules = rules;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(new BehaviourCollector(this.rules));
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /** DSL delegate for the {@code synergy { ... }} body; public so Groovy can dispatch to it. */
  public static final class BehaviourCollector {
    private final Map<String, SynergyRule> rules;

    BehaviourCollector(final Map<String, SynergyRule> rules) {
      this.rules = rules;
    }

    public void behaviour(final String name, final Closure<?> spec) {
      final RuleSpec rs = new RuleSpec();
      spec.setDelegate(rs);
      spec.setResolveStrategy(Closure.DELEGATE_FIRST);
      spec.call();
      this.rules.put(name, rs.toRule());
    }
  }

  /** DSL delegate for one behaviour's {@code requires}/{@code bonus}; public for Groovy dispatch. */
  public static final class RuleSpec {
    @Nullable private transient Closure<?> requires;
    @Nullable private transient Closure<?> bonus;

    public void requires(final Closure<?> c) {
      this.requires = c;
    }

    public void bonus(final Closure<?> c) {
      this.bonus = c;
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
