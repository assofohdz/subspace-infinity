// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import infinity.ai.MoverState;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;
import infinity.ai.field.NavigationFields;
import infinity.ai.tactical.BotAiArenaContext;
import infinity.ai.tactical.NavigateToTile;
import infinity.math.Vec2d;

/**
 * Executes a {@code NavigateToTile} goal by steering down the flow-field gradient toward the goal
 * tile. Dormant until production navigation lands (slice #03): returns {@code FAILURE} (falls through
 * to the v1 fallback) whenever the bot has no arena context or navigation fields. See ADR-0011 / ADR-0013.
 */
public final class SteerToGoalTile implements Action {

  @Override
  public Status tick(final Blackboard blackboard) {
    if (!(blackboard.currentGoal() instanceof NavigateToTile goal)) {
      return Status.FAILURE;
    }
    final BotAiArenaContext ctx = blackboard.arenaContext();
    final NavigationFields nav = ctx == null ? null : ctx.navigation();
    if (nav == null) {
      return Status.FAILURE; // production nav not yet wired — slice #03
    }
    final MoverState self = blackboard.self();
    // 1 world unit = 1 nav cell (.lvl grid); exact grid-origin alignment is finalized with #03.
    final int cellX = (int) Math.floor(self.position().x);
    final int cellY = (int) Math.floor(self.position().z);
    final Vec3i goalCell = goal.tile().getWorld(new Vec3i());
    final Vec2d dir = nav.gradientFor(goalCell.x, goalCell.z).directionAt(cellX, cellY);
    if (dir.lengthSq() < 1e-9) {
      return Status.FAILURE; // at the goal cell or undefined gradient
    }
    blackboard.seek().setDesiredDirection(new Vec3d(dir.x, 0.0, dir.y));
    final Vec3d intent = blackboard.seek().steer(self, blackboard.perception());
    if (intent == null) {
      return Status.FAILURE;
    }
    blackboard.intent().set(intent);
    blackboard.setLastBranch("Navigate");
    return Status.SUCCESS;
  }
}
