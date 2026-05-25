// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.capability.ArenaCapabilityNorms;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.field.NavigationFields;
import javax.annotation.Nullable;

/**
 * Per-arena {@link BotAiArenaContext} the brain reads through the blackboard (ADR-0012). Navigation
 * is {@code null} until the arena's map (hence its passability grid) has loaded; norms + synergy are
 * always present. {@code originCell*} is the arena's world-min corner, used to convert a bot's
 * absolute world cell into the arena-relative cell the {@link NavigationFields} grid is indexed by.
 */
public record ServerBotAiArenaContext(
    ArenaCapabilityNorms norms,
    BotSynergyTable synergyTable,
    @Nullable NavigationFields navigation,
    int originCellX,
    int originCellZ)
    implements BotAiArenaContext {

  @Override
  @Nullable
  public NavigationFields navigation() {
    return this.navigation;
  }
}
