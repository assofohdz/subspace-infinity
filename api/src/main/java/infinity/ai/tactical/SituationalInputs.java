// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import java.util.Map;

/**
 * The shared, normalized bot-AI input vocabulary (ADR-0016): one {@code [0,1]} scalar per named
 * input, computed once per planner cycle and read by every behaviour's {@code situationalFit}.
 * Behaviours never recompute an input — they reference it by name via {@link #weightedSum}, the
 * convex-weighted-sum the ADR pins {@code situationalFit} to. New inputs are added here (and to the
 * ADR-0016 table) by the first behaviour that needs them — never redefined inline. Server-only AI
 * scratch; not wire-crossing.
 */
public record SituationalInputs(
    double rangeFit,
    double energyAdv,
    double rechargeRdy,
    double support,
    double bountyPull,
    double los) {

  /** All-zero inputs; the Blackboard default until the first planner cycle computes a snapshot. */
  public static final SituationalInputs NEUTRAL = new SituationalInputs(0, 0, 0, 0, 0, 0);

  /**
   * Convex-weighted sum of the named inputs against {@code coefficients} (the ADR-0016 fit formula);
   * unknown names contribute 0. Coefficients are expected to sum to {@code 1.0} so the result stays
   * on {@code [0,1]} and behaviours are comparable.
   */
  public double weightedSum(final Map<String, Double> coefficients) {
    double sum = 0.0;
    for (final Map.Entry<String, Double> e : coefficients.entrySet()) {
      sum += e.getValue() * valueOf(e.getKey());
    }
    return sum;
  }

  private double valueOf(final String input) {
    return switch (input) {
      case "range_fit" -> this.rangeFit;
      case "energy_adv" -> this.energyAdv;
      case "recharge_rdy" -> this.rechargeRdy;
      case "support" -> this.support;
      case "bounty_pull" -> this.bountyPull;
      case "los" -> this.los;
      default -> 0.0; // unknown input — a behaviour's coefficient names a vocabulary row not yet sourced
    };
  }
}
