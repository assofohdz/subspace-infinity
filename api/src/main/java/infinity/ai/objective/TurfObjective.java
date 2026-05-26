// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import com.simsilica.es.EntityId;
import java.util.List;
import java.util.Map;

/**
 * Turf (flag-occupancy) objective: the map's stationary flag tiles are the static goals bots hold
 * (ADR-0015). Biases {@code hold-position} up so even hulls with modest tankiness lean toward
 * holding the flag (the planner multiplies this onto the capability-derived weight). Splits bots into
 * {@code flag-defender} / {@code flag-attacker} roles so some hold the flag while others hunt.
 */
public record TurfObjective(List<GoalTile> flagTiles) implements ArenaObjective {

  // Multiplier on the capability-derived hold-position weight; analogous to KOTH's anchor bias.
  private static final double HOLD_POSITION_BIAS = 2.0;

  public TurfObjective {
    flagTiles = List.copyOf(flagTiles);
  }

  @Override
  public String name() {
    return "turf";
  }

  @Override
  public Map<String, Double> behaviourBias() {
    return Map.of("hold-position", HOLD_POSITION_BIAS);
  }

  @Override
  public List<GoalTile> staticGoalTiles() {
    return this.flagTiles;
  }

  /**
   * Split bots into flag-defenders and flag-attackers by entity-id parity — a simple, deterministic,
   * roughly-even split per team (ids interleave across freqs). A flag-control-aware assignment using
   * {@code snapshot} is a follow-up; for now the snapshot is unused here.
   */
  @Override
  public String assignRole(final EntityId bot, final ArenaSnapshot snapshot) {
    return (bot.getId() & 1L) == 0L ? "flag-defender" : "flag-attacker";
  }
}
