// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;

import infinity.config.BotDerivationConfig;
import org.junit.Test;

/**
 * Loads the real {@code zone/engine-bot-ai.groovy} derivation block (ignoring its synergy + roles
 * blocks). Confirms the file authors values matching {@link BotDerivationConfig#DEFAULTS} so the
 * migration preserves behaviour.
 */
public class GroovyBotDerivationLoaderTest {

  @Test
  public void realFileParsesDerivationMatchingDefaults() {
    final BotDerivationConfig cfg = new GroovyBotDerivationLoader().load();
    assertEquals(BotDerivationConfig.DEFAULTS.gravBombArea(), cfg.gravBombArea(), 0.0);
    assertEquals(BotDerivationConfig.DEFAULTS.burstArea(), cfg.burstArea(), 0.0);
    assertEquals(BotDerivationConfig.DEFAULTS.thorArea(), cfg.thorArea(), 0.0);
    assertEquals(
        BotDerivationConfig.DEFAULTS.sustainRechargeScale(), cfg.sustainRechargeScale(), 0.0);
    assertEquals(
        BotDerivationConfig.DEFAULTS.mobilitySpeedWeight(), cfg.mobilitySpeedWeight(), 0.0);
    assertEquals(
        BotDerivationConfig.DEFAULTS.mobilityRotationWeight(), cfg.mobilityRotationWeight(), 0.0);
    assertEquals(
        BotDerivationConfig.DEFAULTS.mobilityThrustWeight(), cfg.mobilityThrustWeight(), 0.0);
  }

  @Test
  public void missingFileFallsBackToDefaults() {
    final BotDerivationConfig cfg = new GroovyBotDerivationLoader().load("/no-such-file.groovy");
    assertEquals(BotDerivationConfig.DEFAULTS, cfg);
  }
}
