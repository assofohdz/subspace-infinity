// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;

import infinity.config.BurstFireConfig;
import org.junit.Test;

/** Pins {@link BurstAdapter} DSL → {@link BurstFireConfig} translation; see REFERENCE.md {@code ## Burst}. */
public class BurstAdapterTest {

  @Test
  public void evaluate_damageLevel_capturedIntoConfig() {
    // Only BurstDamageLevel is exposed via DSL today; projectileCount + decayMs
    // are pinned by BurstFireConfig.DEFAULTS at build() — see adapter.
    final String src =
        "burst {\n"
            + "  damageLevel 500\n"
            + "}\n";

    final BurstFireConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BurstAdapter.INSTANCE, src, "test:burst_full");

    assertEquals(500, cfg.damage());
    assertEquals(BurstFireConfig.DEFAULTS.projectileCount(), cfg.projectileCount());
    assertEquals(BurstFireConfig.DEFAULTS.decayMs(), cfg.decayMs());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final BurstFireConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BurstAdapter.INSTANCE, "burst { }\n", "test:burst_empty");

    // Per BurstFireConfig.DEFAULTS — pinning the Infinity-divergent
    // projectileCount/decayMs alongside Subspace canon damage=250.
    assertEquals(BurstFireConfig.DEFAULTS, cfg);
    assertEquals(250, cfg.damage());
  }

  @Test
  public void evaluate_noBlock_yieldsCanonicalDefaults() {
    final BurstFireConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BurstAdapter.INSTANCE, "// no block\n", "test:burst_noop");

    assertEquals(BurstFireConfig.DEFAULTS, cfg);
  }
}
