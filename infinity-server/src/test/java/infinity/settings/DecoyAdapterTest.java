// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.DecoyConfig;
import org.junit.Test;

/** Pins {@link DecoyAdapter} DSL → {@link DecoyConfig} translation; see REFERENCE.md {@code ## Misc} ({@code DecoyAliveTime}). */
public class DecoyAdapterTest {

  @Test
  public void evaluate_aliveTime_capturedAsMs() {
    // DecoyAliveTime is centiseconds in canon; loader ×10 → ms.
    final String src = "decoy { aliveTime 6000 }\n";

    final DecoyConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(DecoyAdapter.INSTANCE, src, "test:decoy_full");

    assertEquals(60_000L, cfg.aliveTimeMs());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final DecoyConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(DecoyAdapter.INSTANCE, "decoy { }\n", "test:decoy_empty");

    assertEquals(DecoyConfig.DEFAULTS, cfg);
    assertEquals(30_000L, cfg.aliveTimeMs());
  }

  @Test
  public void aliveTime_convertsCentisecondsToMs() {
    final String src = "decoy { aliveTime 1234 }\n";

    final DecoyConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(DecoyAdapter.INSTANCE, src, "test:decoy_cs");

    assertEquals(12_340L, cfg.aliveTimeMs());
  }

  @Test
  public void aliveTime_negative_rejectedAtBoundary_returnsDefaults() {
    final String src = "decoy { aliveTime -1 }\n";

    final DecoyConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(DecoyAdapter.INSTANCE, src, "test:decoy_negCs");

    assertSame(DecoyConfig.DEFAULTS, cfg);
  }
}
