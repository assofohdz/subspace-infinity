// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import java.util.List;
import java.util.Map;

/**
 * What an arena is trying to win at, produced by a mechanic {@code ArenaModule} (ADR-0015). Biases
 * capability-derived bot behaviour and supplies static goal tiles the nav layer pre-builds fields
 * for. Plain (non-sealed) interface so community modules can ship their own gametypes. Per-bot
 * {@code BotRole} assignment ({@code assignRole}) lands in a later increment.
 *
 * @see DeathmatchObjective the identity default when no mechanic supplies one
 */
public interface ArenaObjective {

  /** Stable identifier; surfaces in BotDebug HUD + telemetry. */
  String name();

  /** Multiplicative bias on capability-derived weights ({@code 1.0} = no change). Consumed by the planner. */
  Map<String, Double> behaviourBias();

  /** Static goal tiles the nav layer pre-builds DistanceFields for (flag spawns, KOTH center, …). */
  List<GoalTile> staticGoalTiles();
}
