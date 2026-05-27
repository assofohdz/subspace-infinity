// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.brain.Blackboard;
import infinity.ai.field.NavigationFields;
import infinity.config.ZoneBotAiConfig;

/**
 * Computes the ADR-0016 {@link SituationalInputs} snapshot once per planner cycle from the
 * Blackboard's perception + own state + the zone normalization refs. Behaviours read the result;
 * they never recompute an input. New vocabulary rows are sourced here as the first behaviour that
 * needs them lands. See ADR-0016 §"Shared input vocabulary".
 */
public final class SituationalInputsFactory {

  private SituationalInputsFactory() {}

  /**
   * Snapshot the inputs for {@code bb} this cycle. {@code weaponReady} is the bot's own primary-weapon
   * cooldown-elapsed state (sampled from ECS by the caller). Target-relative inputs collapse to a
   * neutral snapshot when the bot has no current target.
   */
  public static SituationalInputs compute(
      final Blackboard bb, final boolean weaponReady, final ZoneBotAiConfig cfg) {
    final NearbyShip target = bb.target();
    final MoverState self = bb.self();
    if (target == null || self == null) {
      return SituationalInputs.NEUTRAL;
    }
    final double dx = target.position().x - self.position().x;
    final double dz = target.position().z - self.position().z;
    final double dist = Math.hypot(dx, dz);

    final double rangeOpt = cfg.engagementRangeUnits();
    final double rangeFit = rangeOpt > 0 ? 1.0 - clamp(Math.abs(dist - rangeOpt) / rangeOpt) : 0.0;

    final double selfPct = energyFraction(bb.currentEnergy(), bb.maxEnergy());
    final double targetPct = target.energyPct() >= 0 ? target.energyPct() : 0.5;
    final double energyAdv = clamp(selfPct - targetPct + 0.5);

    final double rechargeRdy = weaponReady ? 1.0 : 0.0;
    final double support = clamp(alliesInRadius(bb, self, cfg.supportRadiusUnits()) / 2.0);
    final double bountyPull =
        cfg.bountyReference() > 0 ? clamp(target.bounty() / cfg.bountyReference()) : 0.0;
    final double los = lineOfSight(bb, self, target);

    return new SituationalInputs(rangeFit, energyAdv, rechargeRdy, support, bountyPull, los);
  }

  /** Allies within {@code radius} world units of {@code self} (the {@code support} input numerator). */
  private static int alliesInRadius(final Blackboard bb, final MoverState self, final double radius) {
    if (bb.perception() == null) {
      return 0;
    }
    final double r2 = radius * radius;
    int count = 0;
    for (final NearbyShip ally : bb.perception().allies()) {
      final double ax = ally.position().x - self.position().x;
      final double az = ally.position().z - self.position().z;
      if (ax * ax + az * az <= r2) {
        count++;
      }
    }
    return count;
  }

  /**
   * {@code 1} if Bresenham line-of-sight to {@code target} is clear, else {@code 0}; degrades to
   * {@code 1} when no production nav is wired (mirrors {@code HasLineOfSight}, preserving v1 behaviour
   * where LoS can't be evaluated).
   */
  private static double lineOfSight(
      final Blackboard bb, final MoverState self, final NearbyShip target) {
    final BotAiArenaContext ctx = bb.arenaContext();
    if (ctx == null) {
      return 1.0;
    }
    final NavigationFields nav = ctx.navigation();
    if (nav == null) {
      return 1.0;
    }
    final int ax = (int) Math.floor(self.position().x) - ctx.originCellX();
    final int ay = (int) Math.floor(self.position().z) - ctx.originCellZ();
    final int bx = (int) Math.floor(target.position().x) - ctx.originCellX();
    final int by = (int) Math.floor(target.position().z) - ctx.originCellZ();
    return nav.lineOfSight(ax, ay, bx, by) ? 1.0 : 0.0;
  }

  /** Energy as a {@code [0,1]} fraction; {@code 0.5} neutral when unsampled. */
  private static double energyFraction(final int current, final int max) {
    if (current < 0 || max <= 0) {
      return 0.5;
    }
    return clamp((double) current / max);
  }

  private static double clamp(final double x) {
    return Math.max(0.0, Math.min(1.0, x));
  }
}
