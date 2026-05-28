// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Per-bot role tag (ADR-0015): the name of the {@code BotRoleConfig} whose behaviour bias the planner
 * multiplies in. Server-only — does not cross the wire; assigned by the canonical writer
 * ({@code BotBrainSystem}) at spawn from the arena objective. Carries only the role <em>name</em>;
 * the bias data lives in the {@code BotRoleRegistry} template tier (Config-Component Projection,
 * ADR-0002), so live-reloading role bias doesn't re-stamp bots.
 *
 * <p><strong>Lifetime invariant:</strong> once assigned, a {@code BotRole} is fixed for the entity's
 * lifetime. Mid-round role changes (e.g. KOTH ownership flipping the attacker/defender split) require
 * the bot to die and respawn. Event-driven reassignment is deferred to v2.x per ADR-0015 §"Open work".
 */
public final class BotRole implements EntityComponent {

  public static final String DEFAULT = "default";

  private final String name;

  public BotRole() {
    this(DEFAULT);
  }

  public BotRole(final String name) {
    this.name = name;
  }

  public String name() {
    return this.name;
  }
}
