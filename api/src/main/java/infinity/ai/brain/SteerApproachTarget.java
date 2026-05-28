// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyShip;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;
import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.NavigationFields;
import infinity.ai.tactical.BotAiArenaContext;
import infinity.math.Vec2d;

/**
 * Approaches the current target by steering down the flow-field gradient toward the target's tile —
 * the navigate-to-threat path that replaces straight-line {@code SteerPursue} in walled arenas
 * (ADR-0011/0013). Returns {@code FAILURE} (so the BT falls through to {@code SteerPursue}) when
 * there's no target, no production navigation wired, or the gradient is undefined at the bot's cell
 * (at the goal / unreachable). World cells are converted to arena-relative cells via the context
 * origin before sampling the grid.
 */
public final class SteerApproachTarget implements Action {

  // Coarsen the (moving) target's goal cell to a block so its flow field is reused while the target
  // stays within the block, instead of requesting a fresh per-tile field every tick — the field can
  // then finish its async build (~hundreds of ms) and persist. The bot navigates toward the block
  // centre; in-weapon-range Engage takes over the final approach. ADR-0011 transient-goal turnover
  // mitigation; ~16 tiles is inside the engage handoff range. Sourced from
  // {@code ZoneBotAiConfig.steerGoalBlockCells} at construction time.
  private final int goalBlockCells;

  public SteerApproachTarget(final int goalBlockCells) {
    this.goalBlockCells = goalBlockCells;
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    final NearbyShip target = blackboard.target();
    if (target == null) {
      blackboard.setNavDiag("no-target");
      return Status.FAILURE;
    }
    final BotAiArenaContext ctx = blackboard.arenaContext();
    final NavigationFields nav = ctx == null ? null : ctx.navigation();
    if (nav == null) {
      blackboard.setNavDiag("no-nav");
      return Status.FAILURE; // no production nav → straight-line Pursue fallback
    }
    final MoverSnapshot self = blackboard.self();
    final int goalX = blockCentre((int) Math.floor(target.position().x) - ctx.originCellX());
    final int goalY = blockCentre((int) Math.floor(target.position().z) - ctx.originCellZ());
    final int selfX = (int) Math.floor(self.position().x) - ctx.originCellX();
    final int selfY = (int) Math.floor(self.position().z) - ctx.originCellZ();

    // Diagnostic-rich: use fieldFor (the DistanceField) so we can tell "still building" (EMPTY) from
    // "unreachable" (∞ distance at the bot) from "at goal" (zero gradient) — surfaced in the HUD.
    final DistanceField field = nav.fieldFor(goalX, goalY);
    if (field.width() == 0) {
      blackboard.setNavDiag("building"); // async Dijkstra not done yet (or goal block changed)
      return Status.FAILURE;
    }
    if (!Double.isFinite(field.valueAt(selfX, selfY))) {
      blackboard.setNavDiag("unreach"); // goal impassable, or bot's cell not connected to goal
      return Status.FAILURE;
    }
    final Vec2d dir = new FieldGradient(field).directionAt(selfX, selfY);
    if (dir.lengthSq() < 1e-9) {
      blackboard.setNavDiag("at-goal"); // bot already at/adjacent to the goal cell
      return Status.FAILURE;
    }
    blackboard.seek().setDesiredDirection(new Vec3d(dir.x, 0.0, dir.y));
    final Vec3d intent = blackboard.seek().steer(self, blackboard.perception());
    if (intent == null) {
      blackboard.setNavDiag("no-intent");
      return Status.FAILURE;
    }
    blackboard.intent().set(intent);
    blackboard.setNavDiag("flow");
    blackboard.setLastBranch("Approach");
    return Status.SUCCESS;
  }

  /** Snap a cell coord to the centre of its {@code goalBlockCells}-tile block. */
  private int blockCentre(final int cell) {
    return Math.floorDiv(cell, this.goalBlockCells) * this.goalBlockCells + this.goalBlockCells / 2;
  }
}
