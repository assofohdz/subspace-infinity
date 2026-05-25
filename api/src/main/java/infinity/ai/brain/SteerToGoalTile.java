// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.bt.Action;
import infinity.ai.bt.Status;
import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldBlend;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.NavigationFields;
import infinity.ai.field.ScalarField;
import infinity.ai.field.Weighted;
import infinity.ai.tactical.BotAiArenaContext;
import infinity.ai.tactical.NavigateToTile;
import infinity.math.Vec2d;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    blackboard.intent().set(intent);
    blackboard.setNavDiag("flow");
    blackboard.setLastBranch("Navigate");
    return Status.SUCCESS;
  }

  /** {@code navDir + kT·(toward lower threat) + kO·(toward higher opportunity)}, re-normalized. */
  private static Vec2d blend(
      final Vec2d navDir,
      final BotAiArenaContext ctx,
      final Blackboard bb,
      final int selfX,
      final int selfY) {
    final Vec2d threatDescent = enemyThreatDescent(ctx, bb.ownFreq(), selfX, selfY);
    // Opportunity ascent = toward higher value = negated descent gradient.
    final Vec2d oppDescent = new FieldGradient(ctx.opportunity()).directionAt(selfX, selfY);
    final Vec2d acc =
        navDir
            .add(threatDescent.mult(bb.navThreatWeight()))
            .add(oppDescent.mult(-bb.navOpportunityWeight()));
    return acc.lengthSq() < 1e-9 ? navDir : acc.normalize();
  }

  /** Descent of the blended enemy-threat surface ({@code activeTeamFreqs} minus own) → toward safety. */
  private static Vec2d enemyThreatDescent(
      final BotAiArenaContext ctx, final int ownFreq, final int selfX, final int selfY) {
    final Set<Integer> freqs = ctx.activeTeamFreqs();
    final List<Weighted> enemies = new ArrayList<>(freqs.size());
    for (final int f : freqs) {
      if (f != ownFreq) {
        enemies.add(Weighted.of(ctx.threat(f), 1.0));
      }
    }
    if (enemies.isEmpty()) {
      return Vec2d.ZERO;
    }
    final ScalarField blendField = FieldBlend.of(enemies.toArray(new Weighted[0]));
    return new FieldGradient(blendField).directionAt(selfX, selfY);
  }
}
