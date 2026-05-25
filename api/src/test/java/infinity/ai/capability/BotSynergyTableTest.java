// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import infinity.Ship;
import java.util.Map;
import org.junit.Test;

public class BotSynergyTableTest {

  private static final double EPS = 1e-6;
  private static final double MIN_WEIGHT = 0.05;

  private static final BotSynergyTable TABLE =
      new BotSynergyTable(
          Map.of(
              "engage", SynergyRule.ungated(p -> 0.3 * p.sustainedDamage() + 0.2 * p.mobility()),
              "mine", new SynergyRule(p -> p.maxMines() > 0, p -> 0.3 * p.tankiness()),
              "lurk", new SynergyRule(p -> p.cloak() || p.stealth(), p -> p.cloak() ? 0.4 : 0.2)));

  @Test
  public void gatedBehavioursDropWhenIneligible() {
    // No mines, no cloak → only the ungated engage survives.
    final Map<String, Double> w = TABLE.weightsFor(profile(1.0, 1.0, 0.5, false, 0), MIN_WEIGHT);
    assertTrue(w.containsKey("engage"));
    assertFalse(w.containsKey("mine"));
    assertFalse(w.containsKey("lurk"));
    assertEquals(0.5, w.get("engage"), EPS);
  }

  @Test
  public void eligibleBehavioursGetTheirBonus() {
    // Mines + cloak → all three eligible.
    final Map<String, Double> w = TABLE.weightsFor(profile(0.2, 0.1, 0.8, true, 2), MIN_WEIGHT);
    assertEquals(0.07, w.get("engage"), EPS);
    assertEquals(0.24, w.get("mine"), EPS);
    assertEquals(0.4, w.get("lurk"), EPS);
  }

  @Test
  public void subThresholdWeightsAreDropped() {
    // engage bonus = 0.2*0.05 = 0.01 < MIN_WEIGHT → not enumerated.
    final Map<String, Double> w = TABLE.weightsFor(profile(0.05, 0.0, 0.0, false, 0), MIN_WEIGHT);
    assertFalse(w.containsKey("engage"));
    assertTrue(w.isEmpty());
  }

  private static CapabilityProfile profile(
      final double mobility,
      final double sustained,
      final double tankiness,
      final boolean cloak,
      final int maxMines) {
    return new CapabilityProfile(
        Ship.SHARK, mobility, 0.0, sustained, 0.0, tankiness, 0.0, 0.0,
        false, false, false, cloak, false, false, false,
        maxMines, 0, 0, 0, 0, 0, 0);
  }
}
