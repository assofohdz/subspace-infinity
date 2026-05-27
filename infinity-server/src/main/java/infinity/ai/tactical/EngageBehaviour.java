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
 * {@code engage} (catalog #01): close on the nearest threat and fire. {@code situationalFit} is the
 * ADR-0016 convex sum over {@link SituationalInputs} (coefficients from {@code engine-bot-ai.groovy}'s
 * {@code fit { }} block, read live so reloads take effect). The {@code has_offensive_weapon} half of
 * the hard gate is the synergy {@code requires} clause (ADR-0014); this behaviour enforces the
 * remaining {@code los} half by not enumerating an occluded target. See ADR-0013 / ADR-0016.
 */
public final class EngageBehaviour implements Behaviour {

  // Seed fit used when engine-bot-ai.groovy authors no fit { } block for engage (ADR-0016 §engage).
  private static final Map<String, Double> SEED_FIT =
      Map.of(
          "range_fit", 0.30,
          "energy_adv", 0.25,
          "recharge_rdy", 0.15,
          "support", 0.15,
          "bounty_pull", 0.15);

  private final Supplier<BotSynergyTable> synergy;

  public EngageBehaviour(final Supplier<BotSynergyTable> synergy) {
    this.synergy = synergy;
  }

  @Override
  public String name() {
    return "engage";
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    final NearbyShip target = bb.target();
    if (target == null) {
      return List.of();
    }
    // los hard gate: don't engage a wall-occluded target — fall through to nav/search (ADR-0016).
    if (bb.situationalInputs().get("los") <= 0.0) {
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
