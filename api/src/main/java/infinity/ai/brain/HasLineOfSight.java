// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.bt.Condition;
import infinity.ai.bt.Status;
import infinity.ai.field.NavigationFields;
import infinity.ai.tactical.BotAiArenaContext;

/**
 * {@code SUCCESS} when the bot has clear line-of-sight to its target (no wall between), {@code FAILURE}
 * otherwise. Gates the Engage branch so a wall-occluded target makes the bot fall through to
 * {@code SteerApproachTarget} (navigate around the wall) instead of orbit-firing through it.
 * Degrades to {@code SUCCESS} when there's no target-less state or no navigation wired, preserving v1
 * behaviour where LoS can't be evaluated. Cells are converted world→arena-relative via the context origin.
 */
public final class HasLineOfSight implements Condition {

  @Override
  public Status tick(final Blackboard blackboard) {
    final NearbyShip target = blackboard.target();
    if (target == null) {
      return Status.FAILURE;
    }
    final BotAiArenaContext ctx = blackboard.arenaContext();
    final NavigationFields nav = ctx == null ? null : ctx.navigation();
    if (nav == null) {
      return Status.SUCCESS; // no production nav → can't test LoS; don't block engage (v1 behaviour)
    }
    final MoverState self = blackboard.self();
    final int ax = (int) Math.floor(self.position().x) - ctx.originCellX();
    final int ay = (int) Math.floor(self.position().z) - ctx.originCellZ();
    final int bx = (int) Math.floor(target.position().x) - ctx.originCellX();
    final int by = (int) Math.floor(target.position().z) - ctx.originCellZ();
    return nav.lineOfSight(ax, ay, bx, by) ? Status.SUCCESS : Status.FAILURE;
  }
}
