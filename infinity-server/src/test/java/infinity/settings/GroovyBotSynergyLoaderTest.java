// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import infinity.Ship;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.capability.CapabilityProfile;
import java.util.Map;
import org.junit.Test;

/** Loads the real {@code zone/engine-bot-ai.groovy} and exercises the closure-adapted rules. */
public class GroovyBotSynergyLoaderTest {

  private static final double MIN_WEIGHT = 0.05;
  // 30 catalogued behaviours + follow-traffic (the #07 spatial-fields consumer).
  private static final int CATALOG_SIZE = 31;

  @Test
  public void realFileParsesAllEntries() {
    final BotSynergyTable table = new GroovyBotSynergyLoader().load();
    assertEquals(CATALOG_SIZE, table.rules().size());
  }

  @Test
  public void gatedRulesRespectCapabilityFromGroovyClosures() {
    final BotSynergyTable table = new GroovyBotSynergyLoader().load();
    // mines + cloak Shark, no xradar, no attach.
    final CapabilityProfile shark =
        profile(0.5, 0.2, 0.3, 0.2, 0.6, 0.4, 0.3, true, false, false, 1);
    final Map<String, Double> w = table.weightsFor(shark, MIN_WEIGHT);

    assertTrue("offensive ⇒ engage", w.containsKey("engage"));
    assertTrue("cloak ⇒ ambush", w.containsKey("ambush"));
    assertTrue("mines ⇒ area-denial", w.containsKey("area-denial"));
    assertFalse("attachReceive false ⇒ no anchor", w.containsKey("anchor"));
    assertFalse("xRadar false ⇒ no spot-scout", w.containsKey("spot-scout"));
  }

  @Test
  public void baselineFloorsSurviveForCapabilityNeutralBehaviours() {
    final BotSynergyTable table = new GroovyBotSynergyLoader().load();
    // All-zero profile: only the constant-floor behaviours clear MIN_WEIGHT.
    final CapabilityProfile blank =
        profile(0, 0, 0, 0, 0, 0, 0, false, false, false, 0);
    final Map<String, Double> w = table.weightsFor(blank, MIN_WEIGHT);
    assertTrue(w.containsKey("recharge"));
    assertTrue(w.containsKey("switch-ships"));
    assertFalse("zero mobility ⇒ flank dropped", w.containsKey("flank"));
  }

  private static CapabilityProfile profile(
      final double mobility,
      final double burst,
      final double sustained,
      final double area,
      final double tankiness,
      final double rechargeEconomy,
      final double range,
      final boolean cloak,
      final boolean xRadar,
      final boolean antiwarp,
      final int maxMines) {
    return new CapabilityProfile(
        Ship.SHARK, mobility, burst, sustained, area, tankiness, rechargeEconomy, range,
        false, false, false, cloak, xRadar, antiwarp, false,
        maxMines, 0, 0, 0, 0, 0, 0);
  }
}
