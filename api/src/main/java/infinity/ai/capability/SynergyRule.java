// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * One behaviour's synergy entry: an eligibility {@code requires} gate + a {@code bonus} weight over
 * a {@link CapabilityProfile}. Authored in {@code engine-bot-ai.groovy}; the Groovy loader adapts
 * its closures into these functional interfaces so api/ stays Groovy-free. See ADR-0014.
 */
public record SynergyRule(
    Predicate<CapabilityProfile> requires, ToDoubleFunction<CapabilityProfile> bonus) {

  /** Ungated rule (always eligible) with the given bonus. */
  public static SynergyRule ungated(final ToDoubleFunction<CapabilityProfile> bonus) {
    return new SynergyRule(p -> true, bonus);
  }
}
