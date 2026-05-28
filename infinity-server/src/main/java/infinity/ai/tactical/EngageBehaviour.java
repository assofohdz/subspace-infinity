// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import infinity.ai.brain.Blackboard;
import infinity.ai.capability.BotSynergyTable;
import java.util.ArrayList;
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
    final PerceptionSnapshot perception = bb.perception();
    if (perception == null || perception.threats().isEmpty()) {
      return List.of();
    }
    // los hard gate: don't engage when the primary line-of-sight is occluded. Per-threat
    // LoS would require expanding SituationalInputs to be per-target; for now the gate
    // is global (matches the pre-thrash-fix behaviour). See bot-ai-v3 #01.2.
    if (bb.situationalInputs().get("los") <= 0.0) {
      return List.of();
    }
    // Offer ALL alive threats (sorted nearest-first) — not just the nearest — so the
    // planner's stickiness guard (TacticalPlannerImpl: withinMargin) can re-score the
    // running Engage goal when its target stops being the nearest by a hair. Without
    // this the goal silently flips every ~150ms cadence on equidistant threats.
    final MoverSnapshot self = bb.self();
    final List<NearbyShip> sorted = new ArrayList<>(perception.threats());
    sorted.sort(
        (a, b) ->
            Double.compare(
                a.position().distanceSq(self.position()),
                b.position().distanceSq(self.position())));
    final List<TacticalGoal> goals = new ArrayList<>(sorted.size());
    for (final NearbyShip threat : sorted) {
      goals.add(new Engage(threat.id()));
    }
    return List.copyOf(goals);
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    final Map<String, Double> coeffs = this.synergy.get().fitFor(name());
    return bb.situationalInputs().weightedSum(coeffs.isEmpty() ? SEED_FIT : coeffs);
  }
}
