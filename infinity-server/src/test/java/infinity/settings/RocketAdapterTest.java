// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;

import infinity.config.RocketConfig;
import org.junit.Test;

/** Pins {@link RocketAdapter} DSL → {@link RocketConfig} translation; see REFERENCE.md {@code ## Rocket}. */
public class RocketAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesThrustAndSpeed() {
    // RocketThrust + RocketSpeed are raw integer overrides applied while the
    // rocket buff is active (Decay-bound override of ship's normal max speed +
    // thrust per replacement-as-mutation rule).
    final String src =
        "rocket {\n"
            + "  thrust 150\n"
            + "  speed 4000\n"
            + "}\n";

    final RocketConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RocketAdapter.INSTANCE, src, "test:rocket_full");

    assertEquals(150, cfg.thrust());
    assertEquals(4000, cfg.speed());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final RocketConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RocketAdapter.INSTANCE, "rocket { }\n", "test:rocket_empty");

    assertEquals(RocketConfig.DEFAULTS, cfg);
    assertEquals(100, cfg.thrust());
    assertEquals(3000, cfg.speed());
  }

  @Test
  public void evaluate_partialFragment_unspecifiedKnobsKeepDefaults() {
    final String src = "rocket { thrust 200 }\n";

    final RocketConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RocketAdapter.INSTANCE, src, "test:rocket_partial");

    assertEquals(200, cfg.thrust());
    assertEquals(RocketConfig.DEFAULTS.speed(), cfg.speed());
  }
}
