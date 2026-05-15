// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.ThorConfig;
import org.junit.Test;

/** Pins {@link ThorAdapter} DSL → {@link ThorConfig} translation. Infinity divergence — no Subspace canon. */
public class ThorAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesAllFields() {
    final String src =
        "thor {\n"
            + "  damage 25\n"
            + "  aliveTimeCs 200\n"
            + "  launchVelocity 75\n"
            + "}\n";

    final ThorConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(ThorAdapter.INSTANCE, src, "test:thor_full");

    assertEquals(25, cfg.damage());
    assertEquals(2_000L, cfg.decayMs());
    assertEquals(75, cfg.launchVelocity());
  }

  @Test
  public void evaluate_emptyBlock_yieldsDefaults() {
    final ThorConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(ThorAdapter.INSTANCE, "thor { }\n", "test:thor_empty");

    assertEquals(ThorConfig.DEFAULTS, cfg);
  }

  @Test
  public void aliveTimeCs_convertsCentisecondsToMs() {
    final String src = "thor { aliveTimeCs 150 }\n";

    final ThorConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(ThorAdapter.INSTANCE, src, "test:thor_cs");

    assertEquals(1_500L, cfg.decayMs());
  }

  @Test
  public void launchVelocity_pinnedAtFifty_legacyDefault() {
    // Legacy value before promotion was an inline `50` literal in
    // ConsumableSystem.getActionPosition. Preserve as the DEFAULTS sentinel.
    assertEquals(50, ThorConfig.DEFAULTS.launchVelocity());
  }

  @Test
  public void launchVelocity_negative_rejectedAtBoundary_returnsAdapterEmpty() {
    // Parenthesise to force Groovy to dispatch the builder method (otherwise
    // `launchVelocity -1` reads as `launchVelocity - 1` — a method-handle
    // arithmetic expression that never reaches the validator).
    final String src = "thor { launchVelocity(-1) }\n";

    final ThorConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(ThorAdapter.INSTANCE, src, "test:thor_neg");

    assertSame("eval failure must return ThorConfig.DEFAULTS sentinel", ThorConfig.DEFAULTS, cfg);
  }

  @Test
  public void evaluate_noScript_returnsAdapterEmpty_defaults() {
    final ThorConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(ThorAdapter.INSTANCE, "// comment only\n", "test:thor_noop");

    assertEquals(ThorConfig.DEFAULTS, cfg);
  }
}
