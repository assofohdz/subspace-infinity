// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.BrickConfig;
import org.junit.Test;

/** Pins {@link BrickAdapter} DSL → {@link BrickConfig} translation; see REFERENCE.md {@code ## Brick}. */
public class BrickAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesSpanAndTime() {
    // BrickSpan = tile count, BrickTime = centiseconds → ms (×10).
    final String src =
        "brick {\n"
            + "  span 12\n"
            + "  time 6000\n"
            + "}\n";

    final BrickConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BrickAdapter.INSTANCE, src, "test:brick_full");

    assertEquals(12, cfg.spanTiles());
    assertEquals(60_000L, cfg.timeMs());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final BrickConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BrickAdapter.INSTANCE, "brick { }\n", "test:brick_empty");

    assertEquals(BrickConfig.DEFAULTS, cfg);
  }

  @Test
  public void time_convertsCentisecondsToMs() {
    final String src = "brick { time 200 }\n";

    final BrickConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BrickAdapter.INSTANCE, src, "test:brick_cs");

    assertEquals(2_000L, cfg.timeMs());
  }

  @Test
  public void time_negativeCs_rejectedAtBoundary_returnsDefaults() {
    final String src = "brick { time -5 }\n";

    final BrickConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BrickAdapter.INSTANCE, src, "test:brick_negCs");

    assertSame(BrickConfig.DEFAULTS, cfg);
  }
}
