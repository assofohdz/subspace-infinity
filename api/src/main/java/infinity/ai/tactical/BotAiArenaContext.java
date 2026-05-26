// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.capability.ArenaCapabilityNorms;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.field.NavigationFields;
import infinity.ai.field.ScalarField;
import infinity.ai.field.TileScored;
import infinity.ai.objective.ArenaObjective;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Per-arena bot-AI services a brain holds a reference to: navigation (ADR-0011), capability norms +
 * the engine synergy table (ADR-0014), and the per-team density scalar fields (ADR-0012).
 * Navigation is {@code null} until the arena's map loads; the dormant {@code NavigateToTile} branch
 * falls through on null. See ADR-0012.
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

  /**
   * Ship-position density for one team (frequency); arena-relative cells, {@code 0} where empty.
   * A bot reads its own freq for allies and blends {@link #activeTeamFreqs()} minus its own for
   * enemies — relativity lives here in the consumer, not in the arena-scoped field.
   */
  ScalarField teamDensity(int freq);

  /** Frequencies with at least one live ship as of the last density rebuild; empty until the map loads. */
  Set<Integer> activeTeamFreqs();

  /**
   * Danger posed by one team (frequency) — weapon-range falloff, line-of-sight gated so walls give
   * cover. A bot blends {@link #activeTeamFreqs()} minus its own freq for its incoming-threat
   * surface and bends its nav gradient away from it. Arena-relative cells, {@code 0} where safe.
   */
  ScalarField threat(int freq);

  /** Prize-value falloff over the arena (team-neutral); arena-relative cells, {@code 0} where empty. */
  ScalarField opportunity();

  /**
   * Chokepoint tiles (arena-relative), ranked hottest-first by the combo {@code pinch × (1 + combat
   * + k·density)}: geometric narrowness from load-time detection, amplified by the live combat (fire)
   * heatmap and position density so a corridor everyone funnels through outranks an unused one. Empty
   * until the map loads. The {@code +1} keeps geometric ranking at cold start (no traffic yet).
   */
  List<TileScored> chokepoints();

  /** The arena's active objective (ADR-0015); never {@code null} — {@link infinity.ai.objective.DeathmatchObjective} when no mechanic supplies one. */
  ArenaObjective objective();
}
