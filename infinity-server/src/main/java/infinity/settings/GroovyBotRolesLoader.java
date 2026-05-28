// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import groovy.lang.Binding;
import infinity.ai.objective.BotRoleConfig;
import infinity.ai.objective.BotRoleRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the {@code roles { role 'name', bias: [behaviour: factor, ...] }} block of
 * {@code engine-bot-ai.groovy} into a {@link BotRoleRegistry} (ADR-0015). Shares the file with the
 * {@code synergy { }} block, so it binds a no-op {@code synergy} to ignore that block on this pass.
 * Returns an empty registry (just the default role) on any failure.
 */
public class GroovyBotRolesLoader {

  public static final String DEFAULT_PATH = "/engine-bot-ai.groovy";
  private static final Logger log = LoggerFactory.getLogger(GroovyBotRolesLoader.class);
  private static final RolesAdapter ADAPTER = new RolesAdapter();

  public BotRoleRegistry load() {
    return load(DEFAULT_PATH);
  }

  public BotRoleRegistry load(final String classpathPath) {
    final BotRoleRegistry raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final BotRoleRegistry registry = raw == null ? new BotRoleRegistry(List.of()) : raw;
    if (log.isInfoEnabled()) {
      log.info("Applied {}: {} bot roles", classpathPath, registry.registeredNames().size());
    }
    return registry;
  }

  private static final class RolesAdapter
      implements GroovySettingsAdapter<BotRoleRegistry, List<BotRoleConfig>> {
    @Override
    public List<String> allowedImports() {
      return Collections.emptyList();
    }

    @Override
    public List<BotRoleConfig> bind(final Binding binding) {
      final List<BotRoleConfig> roles = new ArrayList<>();
      binding.setVariable("roles", new RolesClosure(roles));
      // Same file also holds synergy { } + derivation { } blocks (their own loaders); ignore here.
      binding.setVariable("synergy", new IgnoringDslClosure());
      binding.setVariable("derivation", new IgnoringDslClosure());
      return roles;
    }

    @Override
    public BotRoleRegistry extract(final List<BotRoleConfig> roles) {
      return new BotRoleRegistry(roles);
    }

    @Override
    public BotRoleRegistry empty() {
      return new BotRoleRegistry(List.of());
    }
  }

  private static final class RolesClosure extends groovy.lang.Closure<Void> {
    private static final long serialVersionUID = 1L;
    private final transient List<BotRoleConfig> roles;

    RolesClosure(final List<BotRoleConfig> roles) {
      super(null);
      this.roles = roles;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final groovy.lang.Closure<?> body) {
      body.setDelegate(new RoleCollector(this.roles));
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /** DSL delegate for the {@code roles { ... }} body; public so Groovy can dispatch to it. */
  public static final class RoleCollector {
    private final List<BotRoleConfig> roles;

    RoleCollector(final List<BotRoleConfig> roles) {
      this.roles = roles;
    }

    /** {@code role 'name', bias: [behaviour: factor, ...]} — named-arg form (bias map first). */
    public void role(final Map<String, ?> namedArgs, final String name) {
      this.roles.add(new BotRoleConfig(name, toBias(namedArgs.get("bias"))));
    }

    /** {@code role 'name'} — no bias (identity). */
    public void role(final String name) {
      this.roles.add(new BotRoleConfig(name, Map.of()));
    }

    private static Map<String, Double> toBias(final Object biasObj) {
      if (!(biasObj instanceof Map<?, ?> raw)) {
        return Map.of();
      }
      final Map<String, Double> bias = new LinkedHashMap<>();
      for (final Map.Entry<?, ?> e : raw.entrySet()) {
        if (e.getKey() != null && e.getValue() instanceof Number n) {
          bias.put(e.getKey().toString(), n.doubleValue());
        }
      }
      return bias;
    }
  }
}
