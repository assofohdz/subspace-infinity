// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import java.util.Map;

/**
 * A bot's behaviour-weight vector — the capability-derived map (ADR-0014) the planner scores
 * against. Keys are {@link Behaviour#name()}; absent or zero-weight behaviours are not enumerated.
 * See ADR-0013.
 */
public record ArchetypeConfig(String name, Map<String, Double> behaviourWeights) {

  public ArchetypeConfig {
    behaviourWeights = Map.copyOf(behaviourWeights);
  }

  /** Derived weight for {@code behaviourName}, or {@code 0.0} when the archetype doesn't list it. */
  public double weightOf(final String behaviourName) {
    return behaviourWeights.getOrDefault(behaviourName, 0.0);
  }
}
