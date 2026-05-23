// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import infinity.config.BotBrainConfig;
import org.junit.Test;

/** Pins {@link BotBrainAdapter} DSL → {@link BotBrainConfig} translation. See ADR-0009 / ADR-0010. */
public class BotBrainAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesAllFields() {
    final String src =
        "botBrain {\n"
            + "  archetypeName         'Duelist'\n"
            + "  perceptionRadius      45.0\n"
            + "  engageRange           25.0\n"
            + "  orbitRadius           12.0\n"
            + "  evadeEnergyFraction   0.4\n"
            + "  leadPredictionSeconds 0.75\n"
            + "  aimConeDegrees        10.0\n"
            + "}\n";

    final BotBrainConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BotBrainAdapter.INSTANCE, src, "test:botBrain_full");

    assertEquals("Duelist", cfg.archetypeName());
    assertEquals(45.0, cfg.perceptionRadius(), 0.0);
    assertEquals(25.0, cfg.engageRange(), 0.0);
    assertEquals(12.0, cfg.orbitRadius(), 0.0);
    assertEquals(0.4, cfg.evadeEnergyFraction(), 0.0);
    assertEquals(0.75, cfg.leadPredictionSeconds(), 0.0);
    assertEquals(10.0, cfg.aimConeDegrees(), 0.0);
  }

  @Test
  public void evaluate_emptyBlock_yieldsBrawlerDefaults() {
    final BotBrainConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            BotBrainAdapter.INSTANCE, "botBrain { }\n", "test:botBrain_empty");
    assertEquals(BotBrainConfig.DEFAULTS, cfg);
  }

  @Test
  public void evaluate_noScript_returnsAdapterEmpty_canonicalDefaults() {
    final BotBrainConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            BotBrainAdapter.INSTANCE, "// comment only\n", "test:botBrain_noop");
    assertEquals(BotBrainConfig.DEFAULTS, cfg);
  }

  @Test
  public void evadeEnergyFraction_outOfRange_rejectedAtBoundary_returnsAdapterEmpty() {
    // > 1.0 → IAE thrown by builder; host catches → empty().
    final String src = "botBrain { evadeEnergyFraction 1.5 }\n";
    final BotBrainConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            BotBrainAdapter.INSTANCE, src, "test:botBrain_badFraction");
    assertSame(BotBrainConfig.DEFAULTS, cfg);
  }

  @Test
  public void engageRange_negative_rejectedByValidator() {
    final BotBrainAdapter.BotBrainBuilder b = new BotBrainAdapter.BotBrainBuilder();
    assertThrows(IllegalArgumentException.class, () -> b.engageRange(-1.0));
    assertThrows(IllegalArgumentException.class, () -> b.perceptionRadius(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> b.aimConeDegrees(Double.POSITIVE_INFINITY));
  }

  @Test
  public void archetypeName_blank_preservesDefault() {
    // Blank/null on archetypeName silently drops (allows partial overrides to keep defaults).
    final String src = "botBrain { archetypeName '' }\n";
    final BotBrainConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            BotBrainAdapter.INSTANCE, src, "test:botBrain_blankName");
    assertEquals(BotBrainConfig.DEFAULTS.archetypeName(), cfg.archetypeName());
  }
}
