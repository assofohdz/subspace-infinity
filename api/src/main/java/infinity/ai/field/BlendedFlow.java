// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import infinity.ai.tactical.BotAiArenaContext;
import infinity.math.Vec2d;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The blended bot-steering flow direction (ADR-0011/0012): a goal-descent heading bent away from
 * enemy threat and toward opportunity, {@code navDir + kT·threatDescent + kO·oppAscent}. Shared by
 * {@code SteerToGoalTile} (per-bot, which also folds in a wall-clearance term) and the player
 * flow-field debug sampler so the visualised flow matches what the bots actually steer down.
 */
public final class BlendedFlow {

  private BlendedFlow() {
    /* utility */
  }

  /**
   * {@code navDir + threatWeight·(toward lower enemy threat) + opportunityWeight·(toward higher
   * opportunity)} at the given arena-relative cell. <b>Not</b> normalized and <b>not</b> including
   * any wall-clearance term — callers add those and normalize.
   */
  public static Vec2d blendNav(
      final Vec2d navDir,
      final BotAiArenaContext ctx,
      final int ownFreq,
      final int cellX,
      final int cellY,
      final double threatWeight,
      final double opportunityWeight) {
    final Vec2d threatDescent = enemyThreatDescent(ctx, ownFreq, cellX, cellY);
    // Opportunity ascent = toward higher value = negated descent gradient.
    final Vec2d oppDescent = new FieldGradient(ctx.opportunity()).directionAt(cellX, cellY);
    return navDir
        .add(threatDescent.mult(threatWeight))
        .add(oppDescent.mult(-opportunityWeight));
  }

  /** Descent of the blended enemy-threat surface ({@code activeTeamFreqs} minus own) → toward safety. */
  public static Vec2d enemyThreatDescent(
      final BotAiArenaContext ctx, final int ownFreq, final int cellX, final int cellY) {
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
    return new FieldGradient(blendField).directionAt(cellX, cellY);
  }
}
