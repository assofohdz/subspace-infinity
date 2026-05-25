// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.capability.ArenaCapabilityNorms;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.field.NavigationFields;
import javax.annotation.Nullable;

/**
 * Per-arena {@link BotAiArenaContext} the brain reads through the blackboard. Navigation is
 * {@code null} until production flow-field nav lands (slice #03); norms + synergy are live. See
 * ADR-0012 / ADR-0014.
 */
public record ServerBotAiArenaContext(
    ArenaCapabilityNorms norms, BotSynergyTable synergyTable) implements BotAiArenaContext {

  @Override
  @Nullable
  public NavigationFields navigation() {
    return null; // wired in slice #03 when MapSystem exposes a passability grid
  }
}
