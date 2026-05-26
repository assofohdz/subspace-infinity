// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import java.util.List;
import java.util.Map;

/**
 * Turf (flag-occupancy) objective: the map's stationary flag tiles are the static goals bots hold
 * (ADR-0015). {@code behaviourBias} is identity here; the hold-position bias lands with planner
 * bias-consumption in a later increment.
 */
public record TurfObjective(List<GoalTile> flagTiles) implements ArenaObjective {

  public TurfObjective {
    flagTiles = List.copyOf(flagTiles);
  }

  @Override
  public String name() {
    return "turf";
  }

  @Override
  public Map<String, Double> behaviourBias() {
    return Map.of();
  }

  @Override
  public List<GoalTile> staticGoalTiles() {
    return this.flagTiles;
  }
}
