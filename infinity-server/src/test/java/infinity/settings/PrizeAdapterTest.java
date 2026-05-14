// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.PrizeConfig;
import org.junit.Test;

/** Pins {@link PrizeAdapter} DSL → {@link PrizeConfig} translation; see REFERENCE.md {@code ## Prize}. */
public class PrizeAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesAllFields() {
    // PrizeMaxExist + PrizeMinExist (cs → ms) bracket the per-prize random TTL;
    // DeathPrizeTime (cs → ms) controls death-drop lifetime; PrizeNegativeFactor
    // is a 1-in-N roll (raw int, not converted).
    final String src =
        "prize {\n"
            + "  maxExist 8000\n"
            + "  minExist 6000\n"
            + "  deathPrizeTime 2000\n"
            + "  negativeFactor 32\n"
            + "}\n";

    final PrizeConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PrizeAdapter.INSTANCE, src, "test:prize_full");

    assertEquals(80_000L, cfg.defaultDecayMs());
    assertEquals(60_000L, cfg.defaultMinDecayMs());
    assertEquals(20_000L, cfg.deathPrizeTimeMs());
    assertEquals(32, cfg.prizeNegativeFactor());
    // defaultMaxCount + bountyValue are pinned at builder time from DEFAULTS.
    assertEquals(PrizeConfig.DEFAULTS.defaultMaxCount(), cfg.defaultMaxCount());
    assertEquals(PrizeConfig.DEFAULTS.bountyValue(), cfg.bountyValue());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final PrizeConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PrizeAdapter.INSTANCE, "prize { }\n", "test:prize_empty");

    assertEquals(PrizeConfig.DEFAULTS, cfg);
    // Death-drops + negative-factor default to disabled; min defaults to max
    // (= no random spread).
    assertEquals(0L, cfg.deathPrizeTimeMs());
    assertEquals(0, cfg.prizeNegativeFactor());
    assertEquals(cfg.defaultDecayMs(), cfg.defaultMinDecayMs());
  }

  @Test
  public void minExistOmitted_pinsToMaxExist() {
    // PrizeMinExist defaulting to PrizeMaxExist matches PrizeSystem.sampleDecayMs
    // contract: equal min/max collapses the uniform sample to a constant.
    final String src = "prize { maxExist 5000 }\n";

    final PrizeConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PrizeAdapter.INSTANCE, src, "test:prize_minOmit");

    assertEquals(50_000L, cfg.defaultDecayMs());
    assertEquals(50_000L, cfg.defaultMinDecayMs());
  }

  @Test
  public void minExistGreaterThanMaxExist_rejected_returnsDefaults() {
    // PrizeAdapter throws on min > max at build(); host catches → empty().
    final String src = "prize { maxExist 5000 ; minExist 6000 }\n";

    final PrizeConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PrizeAdapter.INSTANCE, src, "test:prize_minGtMax");

    assertSame(PrizeConfig.DEFAULTS, cfg);
  }

  @Test
  public void allTimingFieldsConvertCentisecondsToMs() {
    final String src =
        "prize {\n"
            + "  maxExist 100\n"
            + "  minExist 50\n"
            + "  deathPrizeTime 75\n"
            + "}\n";

    final PrizeConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PrizeAdapter.INSTANCE, src, "test:prize_cs");

    assertEquals(1_000L, cfg.defaultDecayMs());
    assertEquals(500L, cfg.defaultMinDecayMs());
    assertEquals(750L, cfg.deathPrizeTimeMs());
  }
}
