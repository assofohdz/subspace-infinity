// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import java.util.List;
import java.util.Map;

/** Identity objective — no bias, no goal tiles. The implicit default when no mechanic supplies one (ADR-0015). */
public record DeathmatchObjective() implements ArenaObjective {

  @Override
  public String name() {
    return "deathmatch";
  }

  @Override
  public Map<String, Double> behaviourBias() {
    return Map.of();
  }

  @Override
  public List<GoalTile> staticGoalTiles() {
    return List.of();
  }
}
