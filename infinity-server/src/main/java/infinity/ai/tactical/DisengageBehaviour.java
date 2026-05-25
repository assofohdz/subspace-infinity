// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.NearbyShip;
import infinity.ai.brain.Blackboard;
import java.util.List;

/**
 * {@code disengage} (catalog #06): break off and flee a known threat. Baseline fit = {@code 1 −
 * energyFraction} (flee harder as energy drains); the full ADR-0016 fit lands with the catalog issue.
 * See ADR-0013.
 */
public final class DisengageBehaviour implements Behaviour {

  @Override
  public String name() {
    return "disengage";
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    final NearbyShip threat = bb.target();
    return threat == null ? List.of() : List.of(new Disengage(threat.id()));
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    final double frac = BaselineFit.energyFraction(bb);
    return frac < 0.0 ? 0.0 : 1.0 - frac; // unknown energy ⇒ no reason to flee
  }
}
