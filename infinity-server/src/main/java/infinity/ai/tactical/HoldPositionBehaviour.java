// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import infinity.ai.objective.GoalTile;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code hold-position} (catalog #09): occupy a valuable tile. Candidates are <em>all</em> the arena
 * objective's static goal tiles (ADR-0015) — e.g. the turf flags — each emitting a
 * {@link NavigateToTile}. Enumerating all (not just the nearest) is load-bearing: with several
 * near-equidistant flags, a "nearest only" goal flips as the bot drifts and the abandoned goal stops
 * being offered, so the planner's stickiness can't hold it and the bot thrashes in place. Offering
 * every flag each cycle lets stickiness lock onto one and commit. Capability-gated by the
 * {@code hold-position} synergy weight (a flat base plus tankiness/antiwarp), so all hulls bias toward
 * the flag and durable hulls hold best. The full ADR-0016 fit formula (position_value / support /
 * defensive-item / energy-buffer) is a refinement; this is the idle-biased baseline. See ADR-0013 / ADR-0016.
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
    final List<TacticalGoal> out = new ArrayList<>(goals.size());
    for (final GoalTile g : goals) {
      out.add(new NavigateToTile(g.worldCellX(), g.worldCellZ()));
    }
    return out;
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    return bb.target() == null ? IDLE_FIT : ENGAGED_FIT;
  }
}
