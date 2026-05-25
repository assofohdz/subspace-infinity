// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.capability.ArenaCapabilityNorms;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.field.NavigationFields;
import javax.annotation.Nullable;

/**
 * Per-arena bot-AI services a brain holds a reference to: navigation (ADR-0011), capability norms +
 * the engine synergy table (ADR-0014). Navigation is {@code null} until production flow-field nav
 * lands (slice #03); the dormant {@code NavigateToTile} branch falls through on null. See ADR-0012.
 */
public interface BotAiArenaContext {

  /** Flow fields for goal-tile navigation (arena-relative cells), or {@code null} until the arena's map loads. */
  @Nullable
  NavigationFields navigation();

  /** Per-arena maxima normalizing each {@link infinity.ai.capability.CapabilityProfile} dimension. */
  ArenaCapabilityNorms norms();

  /** Engine-authored synergy table crossing a profile into capability-derived behaviour weights. */
  BotSynergyTable synergyTable();

  /** Arena world-origin cell X — subtract from an absolute world cell to index {@link #navigation()}. */
  int originCellX();

  /** Arena world-origin cell Z — subtract from an absolute world cell to index {@link #navigation()}. */
  int originCellZ();
}
