// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pins the planner-cadence gate in {@link BotBrainSystem#shouldPlan}, incl. the first-tick overflow guard. */
public class BotBrainPlanCadenceTest {

  private static final long CADENCE = 150_000_000L; // 150ms in nanos

  @Test
  public void firstTickAlwaysPlans() {
    // Regression: lastPlanNanos == Long.MIN_VALUE must plan on the first tick, NOT overflow
    // nowNanos - Long.MIN_VALUE into a negative "cadence not elapsed".
    assertTrue(BotBrainSystem.shouldPlan(Long.MIN_VALUE, 0L, CADENCE));
    assertTrue(BotBrainSystem.shouldPlan(Long.MIN_VALUE, 1_000_000L, CADENCE));
    assertTrue(BotBrainSystem.shouldPlan(Long.MIN_VALUE, Long.MAX_VALUE, CADENCE));
  }

  @Test
  public void withinCadenceDoesNotPlan() {
    assertFalse(BotBrainSystem.shouldPlan(1_000_000_000L, 1_000_000_000L + CADENCE - 1, CADENCE));
  }

  @Test
  public void atOrPastCadencePlans() {
    assertTrue(BotBrainSystem.shouldPlan(1_000_000_000L, 1_000_000_000L + CADENCE, CADENCE));
    assertTrue(BotBrainSystem.shouldPlan(1_000_000_000L, 1_000_000_000L + CADENCE + 1, CADENCE));
  }
}
