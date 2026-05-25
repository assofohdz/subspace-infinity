// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.NearbyShip;
import infinity.ai.brain.Blackboard;
import java.util.List;

/**
 * {@code engage} (catalog #01): close on the nearest threat and fire. Baseline fit = energy fraction
 * (a healthy bot wants to fight); the full ADR-0016 fit lands with the catalog issue. See ADR-0013.
 */
public final class EngageBehaviour implements Behaviour {

  @Override
  public String name() {
    return "engage";
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    final NearbyShip target = bb.target();
    return target == null ? List.of() : List.of(new Engage(target.id()));
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    final double frac = BaselineFit.energyFraction(bb);
    return frac < 0.0 ? 1.0 : frac; // unknown energy ⇒ willing to fight
  }
}
