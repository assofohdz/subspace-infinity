// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.NearbyShip;
import infinity.ai.brain.Blackboard;
import infinity.ai.capability.BotSynergyTable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code assassinate / pick} (catalog #03): single out a high-value isolated target. Same Execute as
 * {@code engage} (close + fire on the current target — target re-selection is a later targeting-layer
 * concern), but a burst/cloak lens: scores high when the target is isolated and high-bounty. Gate:
 * {@code has_offensive_weapon} (synergy {@code requires}) ∧ {@code los}. See ADR-0013 / ADR-0016.
 */
public final class AssassinateBehaviour implements Behaviour {

  private static final Map<String, Double> SEED_FIT =
      Map.of("bounty_pull", 0.30, "isolation", 0.30, "approach_safety", 0.20, "concealment", 0.20);

  private final Supplier<BotSynergyTable> synergy;

  public AssassinateBehaviour(final Supplier<BotSynergyTable> synergy) {
    this.synergy = synergy;
  }

  @Override
  public String name() {
    return "assassinate";
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    final NearbyShip target = bb.target();
    if (target == null || bb.situationalInputs().get("los") <= 0.0) {
      return List.of();
    }
    return List.of(new Engage(target.id()));
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    final Map<String, Double> coeffs = this.synergy.get().fitFor(name());
    return bb.situationalInputs().weightedSum(coeffs.isEmpty() ? SEED_FIT : coeffs);
  }
}
