// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The behaviour roster's synergy rules (one per behaviour). Crossing a {@link CapabilityProfile}
 * through the table yields the capability-derived behaviour-weight vector the planner consumes
 * (objective + role bias are applied later, ADR-0015). See ADR-0014.
 */
public record BotSynergyTable(
    Map<String, SynergyRule> rules, Map<String, Map<String, Double>> fitCoefficients) {

  public BotSynergyTable {
    rules = Map.copyOf(rules);
    fitCoefficients = Map.copyOf(fitCoefficients);
  }

  /** Rules-only table with no fit coefficients (back-compat for callers that don't load the fit block). */
  public BotSynergyTable(final Map<String, SynergyRule> rules) {
    this(rules, Map.of());
  }

  /** The ADR-0016 {@code situationalFit} coefficients for {@code behaviour}; empty when none authored. */
  public Map<String, Double> fitFor(final String behaviour) {
    return this.fitCoefficients.getOrDefault(behaviour, Map.of());
  }

  /**
   * Capability-derived weights: for each behaviour whose {@code requires} gate passes, its
   * {@code bonus}, dropping any below {@code minWeight} (the enumeration floor, ADR-0013).
   */
  public Map<String, Double> weightsFor(final CapabilityProfile profile, final double minWeight) {
    final Map<String, Double> out = new LinkedHashMap<>();
    for (final Map.Entry<String, SynergyRule> e : this.rules.entrySet()) {
      final SynergyRule rule = e.getValue();
      if (!rule.requires().test(profile)) {
        continue; // ineligible — behaviour not even enumerated for this ship
      }
      final double weight = rule.bonus().applyAsDouble(profile);
      if (weight >= minWeight) {
        out.put(e.getKey(), weight);
      }
    }
    return out;
  }
}
