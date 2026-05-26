// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lookup of {@link BotRoleConfig} by role name (ADR-0015), loaded from {@code engine-bot-ai.groovy}
 * by the server-side Groovy loader (mirrors {@code BotSynergyTable}). {@link #get} returns the
 * {@code default} identity-bias role for an unknown name, so a stale or unregistered {@code BotRole}
 * degrades to no bias rather than throwing on the planner hot path. Immutable after construction.
 */
public final class BotRoleRegistry {

  private static final BotRoleConfig DEFAULT =
      new BotRoleConfig(infinity.es.BotRole.DEFAULT, Map.of());

  private final Map<String, BotRoleConfig> byName;

  public BotRoleRegistry(final Collection<BotRoleConfig> roles) {
    final Map<String, BotRoleConfig> m = new LinkedHashMap<>();
    m.put(DEFAULT.name(), DEFAULT); // always present; overridden if authored
    for (final BotRoleConfig role : roles) {
      m.put(role.name(), role);
    }
    this.byName = Map.copyOf(m);
  }

  /** Config for {@code roleName}, or the identity {@code default} role if not registered. */
  public BotRoleConfig get(final String roleName) {
    return this.byName.getOrDefault(roleName, DEFAULT);
  }

  /** Whether {@code roleName} is registered (for load-time validation of {@code assignRole} returns). */
  public boolean isRegistered(final String roleName) {
    return this.byName.containsKey(roleName);
  }

  /** Every registered role name (telemetry + validation). */
  public Collection<String> registeredNames() {
    return this.byName.keySet();
  }
}
