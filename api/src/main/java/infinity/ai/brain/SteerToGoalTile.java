// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;
import infinity.ai.field.BlendedFlow;
import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.NavigationFields;
import infinity.ai.tactical.BotAiArenaContext;
import infinity.ai.tactical.NavigateToTile;
import infinity.math.Vec2d;

/**
 * Executes a {@link NavigateToTile} goal by steering down a <em>blended</em> flow-field gradient
 * (ADR-0011/0012): the descent toward the goal cell, bent away from incoming threat and toward
 * opportunity — {@code navDir + kT·threatDescent + kO·oppAscent}. Cell-precise: world cells are
 * converted to the arena-relative grid via the context origin (the same frame {@code SteerApproachTarget}
 * uses). Diagnostic {@code navDiag}: building / unreach / at-goal / flow. Returns {@code FAILURE}
 * (BT falls through) when there's no nav, the field is still building, the goal is unreachable, or the
 * bot is already at the goal.
 */
public final class SteerToGoalTile implements Action {

  // Arrival: within this many cells of the goal, thrust tapers linearly to zero so the bot
  // decelerates and settles on the goal instead of cruising through it and orbiting back.
  private static final double ARRIVAL_RADIUS_CELLS = 6.0;

  // Weight of the wall hull-clearance push blended into the flow heading (cooperative — eases the
  // diameter-2 hull off walls the point-flow routes adjacent to, without reversing). Kept low so it
  // doesn't fight the flow at tight chokepoints (where the gap IS between walls and the flow must win
  // to thread it); it only nudges the hull off walls in open routing.
  private static final double WALL_AVOID_WEIGHT = 0.3;

  @Override
  public Status tick(final Blackboard blackboard) {
    if (!(blackboard.currentGoal() instanceof NavigateToTile goal)) {
      return Status.FAILURE;
    }
    final BotAiArenaContext ctx = blackboard.arenaContext();
    final NavigationFields nav = ctx == null ? null : ctx.navigation();
    if (nav == null) {
      blackboard.setNavDiag("no-nav");
      return Status.FAILURE;
    }
    final MoverState self = blackboard.self();
    final int goalX = goal.cellX() - ctx.originCellX();
    final int goalY = goal.cellZ() - ctx.originCellZ();
    final int selfX = (int) Math.floor(self.position().x) - ctx.originCellX();
    final int selfY = (int) Math.floor(self.position().z) - ctx.originCellZ();

    final DistanceField field = nav.fieldFor(goalX, goalY);
    if (field.width() == 0) {
      blackboard.setNavDiag("building");
      return Status.FAILURE;
    }
    if (!Double.isFinite(field.valueAt(selfX, selfY))) {
      blackboard.setNavDiag("unreach");
      return Status.FAILURE;
    }
    final Vec2d navDir = new FieldGradient(field).directionAt(selfX, selfY);
    if (navDir.lengthSq() < 1e-9) {
      blackboard.setNavDiag("at-goal");
      return Status.FAILURE;
    }

    final Vec2d blended = blend(navDir, ctx, blackboard, selfX, selfY);
    blackboard.seek().setDesiredDirection(new Vec3d(blended.x, 0.0, blended.y));
    final Vec3d intent = blackboard.seek().steer(self, blackboard.perception());
    if (intent == null) {
      blackboard.setNavDiag("no-intent");
      return Status.FAILURE;
    }
    // Arrival taper: ease thrust toward zero within ARRIVAL_RADIUS_CELLS of the goal so the bot settles
    // on it rather than overshooting and orbiting back (the flag-orbit seen in smoke). Branchless —
    // min() makes it a no-op beyond the radius.
    final double distCells = Math.hypot((double) goalX - selfX, (double) goalY - selfY);
    intent.z *= Math.min(1.0, distCells / ARRIVAL_RADIUS_CELLS);
    blackboard.intent().set(intent);
    blackboard.setNavDiag("flow");
    blackboard.setLastBranch("Navigate");
    return Status.SUCCESS;
  }

  /**
   * {@code navDir + kT·(toward lower threat) + kO·(toward higher opportunity) + kW·(away from walls)},
   * re-normalized. The wall term is the cooperative hull-clearance push (replaces the reverse-override).
   */
  private static Vec2d blend(
      final Vec2d navDir,
      final BotAiArenaContext ctx,
      final Blackboard bb,
      final int selfX,
      final int selfY) {
    Vec2d acc =
        BlendedFlow.blendNav(
            navDir, ctx, bb.ownFreq(), selfX, selfY, bb.navThreatWeight(), bb.navOpportunityWeight());
    final Vec3d wallAvoid = bb.wallAvoid();
    if (wallAvoid != null) {
      acc = acc.add(new Vec2d(wallAvoid.x, wallAvoid.z).mult(WALL_AVOID_WEIGHT));
    }
    return acc.lengthSq() < 1e-9 ? navDir : acc.normalize();
  }
}
