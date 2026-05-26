// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import java.util.Map;

/**
 * Template tier for a bot role (ADR-0015 / ADR-0002): a role name plus its multiplicative behaviour
 * bias. Loaded from {@code engine-bot-ai.groovy} into the {@code BotRoleRegistry}; the planner reads
 * {@link #behaviourBias} for a bot's {@code BotRole}. {@code behaviourBias} is defensively copied.
 */
public record BotRoleConfig(String name, Map<String, Double> behaviourBias) {

  public BotRoleConfig {
    behaviourBias = Map.copyOf(behaviourBias);
  }
}
