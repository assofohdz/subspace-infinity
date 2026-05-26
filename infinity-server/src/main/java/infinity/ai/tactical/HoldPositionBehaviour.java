// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.MoverState;
import infinity.ai.brain.Blackboard;
import infinity.ai.objective.GoalTile;
import java.util.List;

/**
 * {@code hold-position} (catalog #09): occupy a valuable tile. Candidates are the arena objective's
 * static goal tiles (ADR-0015) — e.g. the turf flag — emitting a {@link NavigateToTile} toward the
 * nearest. Capability-gated by the existing {@code hold-position} synergy weight (tankiness +
 * antiwarp), so tanky hulls hold while glass cannons don't. The full ADR-0016 fit formula
 * (position_value / support / defensive-item / energy-buffer) is a refinement; this is the
 * idle-biased baseline that lands the objective-nav path. See ADR-0013 / ADR-0016.
 */
public final class HoldPositionBehaviour implements Behaviour {

  private static final String NAME = "hold-position";
  // Fit floors: prefer holding the objective when idle, yield to combat when a target is in view.
  private static final double IDLE_FIT = 0.55;
  private static final double ENGAGED_FIT = 0.15;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    final BotAiArenaContext ctx = bb.arenaContext();
    if (ctx == null || ctx.objective() == null) {
      return List.of();
    }
    final List<GoalTile> goals = ctx.objective().staticGoalTiles();
    if (goals.isEmpty()) {
      return List.of();
    }
    final MoverState self = bb.self();
    GoalTile nearest = null;
    double bestSq = Double.POSITIVE_INFINITY;
    for (final GoalTile g : goals) {
      final double dx = g.worldCellX() - self.position().x;
      final double dz = g.worldCellZ() - self.position().z;
      final double dSq = dx * dx + dz * dz;
      if (dSq < bestSq) {
        bestSq = dSq;
        nearest = g;
      }
    }
    return List.of(new NavigateToTile(nearest.worldCellX(), nearest.worldCellZ()));
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    return bb.target() == null ? IDLE_FIT : ENGAGED_FIT;
  }
}
