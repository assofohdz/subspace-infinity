// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import java.util.HashMap;
import java.util.Map;

/**
 * The shared, normalized bot-AI input vocabulary (ADR-0016): one {@code [0,1]} scalar per named
 * input, computed once per planner cycle and read by every behaviour's {@code situationalFit} via
 * {@link #weightedSum}. Map-backed (keyed by the ADR-0016 input name) so adding a vocabulary row is
 * one {@link Builder#set} call with no constructor churn; an input not yet sourced reads as {@code 0}.
 * Server-only AI scratch; not wire-crossing.
 */
public final class SituationalInputs {

  /** Empty inputs (every name reads 0); the Blackboard default until the first planner cycle. */
  public static final SituationalInputs NEUTRAL = builder().build();

  private final Map<String, Double> values;

  private SituationalInputs(final Map<String, Double> values) {
    this.values = Map.copyOf(values);
  }

  /** The {@code [0,1]} value for {@code input}, or {@code 0} when not sourced this cycle. */
  public double get(final String input) {
    return this.values.getOrDefault(input, 0.0);
  }

  /**
   * Convex-weighted sum of the named inputs against {@code coefficients} (the ADR-0016 fit formula);
   * unknown names contribute 0. Coefficients are expected to sum to {@code 1.0} so the result stays
   * on {@code [0,1]} and behaviours are comparable.
   */
  public double weightedSum(final Map<String, Double> coefficients) {
    double sum = 0.0;
    for (final Map.Entry<String, Double> e : coefficients.entrySet()) {
      sum += e.getValue() * get(e.getKey());
    }
    return sum;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Accumulates named inputs for one planner cycle; {@link #build} snapshots them immutably. */
  public static final class Builder {
    private final Map<String, Double> values = new HashMap<>();

    /** Set one ADR-0016 input by its vocabulary name; returns {@code this} for chaining. */
    public Builder set(final String input, final double value) {
      this.values.put(input, value);
      return this;
    }

    public SituationalInputs build() {
      return new SituationalInputs(this.values);
    }
  }
}
