// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import infinity.config.RepelConfig;
import org.junit.Test;

/** Pins {@link RepelAdapter} DSL → {@link RepelConfig} translation; see REFERENCE.md {@code ## Repel}. */
public class RepelAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesAllFields() {
    // RepelSpeed raw, RepelTime cs (×10 → ms), RepelDistance authored in tiles
    // (Infinity native; canon is pixels — divide by 16 when porting svs).
    final String src =
        "repel {\n"
            + "  speed 6000\n"
            + "  time 300\n"
            + "  distance 64.0\n"
            + "}\n";

    final RepelConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RepelAdapter.INSTANCE, src, "test:repel_full");

    assertEquals(6000, cfg.speed());
    assertEquals(3_000L, cfg.timeMs());
    assertEquals(64.0, cfg.distanceTiles(), 0.0);
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final RepelConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RepelAdapter.INSTANCE, "repel { }\n", "test:repel_empty");

    assertEquals(RepelConfig.DEFAULTS, cfg);
    assertEquals(5000, cfg.speed());
    assertEquals(2250L, cfg.timeMs());
    assertEquals(32.0, cfg.distanceTiles(), 0.0);
  }

  @Test
  public void time_convertsCentisecondsToMs() {
    final String src = "repel { time 225 }\n";

    final RepelConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RepelAdapter.INSTANCE, src, "test:repel_cs");

    assertEquals(2_250L, cfg.timeMs());
  }

  @Test
  public void time_negative_rejectedAtBoundary_returnsDefaults() {
    final String src = "repel { time -1 }\n";

    final RepelConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(RepelAdapter.INSTANCE, src, "test:repel_negCs");

    assertSame(RepelConfig.DEFAULTS, cfg);
  }

  @Test
  public void distance_nanOrInfinite_rejectedByValidator() {
    final RepelAdapter.RepelBuilder b = new RepelAdapter.RepelBuilder();
    assertThrows(IllegalArgumentException.class, () -> b.distance(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> b.distance(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> b.distance(-1.0));
  }
}
