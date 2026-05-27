// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import org.junit.Test;

/** The ADR-0016 vocabulary holder: the convex-weighted-sum contract + unknown-input handling. */
public class SituationalInputsTest {

  private static final double EPS = 1e-9;

  @Test
  public void weightedSumIsConvexCombination() {
    final SituationalInputs in =
        SituationalInputs.builder().set("range_fit", 0.4).set("energy_adv", 0.8).build();
    final double sum =
        in.weightedSum(Map.of("range_fit", 0.5, "energy_adv", 0.5)); // 0.5·0.4 + 0.5·0.8
    assertEquals(0.6, sum, EPS);
  }

  @Test
  public void unknownInputContributesZero() {
    final SituationalInputs in = SituationalInputs.builder().set("range_fit", 1.0).build();
    // "predictability" is a real vocabulary row but not yet sourced here → contributes 0.
    assertEquals(0.0, in.weightedSum(Map.of("predictability", 1.0)), EPS);
  }

  @Test
  public void neutralIsAllZero() {
    assertEquals(0.0, SituationalInputs.NEUTRAL.weightedSum(Map.of("range_fit", 1.0)), EPS);
  }
}
