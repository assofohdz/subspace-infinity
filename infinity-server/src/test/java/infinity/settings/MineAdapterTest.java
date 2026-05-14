// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.MineConfig;
import org.junit.Test;

/** Pins {@link MineAdapter} DSL → {@link MineConfig} translation; see REFERENCE.md {@code ## Mine}. */
public class MineAdapterTest {

  @Test
  public void evaluate_aliveTime_capturedAsMs() {
    // MineAliveTime is centiseconds in canon; loader ×10 → ms.
    // Damage today is sourced from per-ship MineCost (Subspace convention),
    // not a MineConfig field — see MineConfig javadoc.
    final String src = "mine { aliveTime 12000 }\n";

    final MineConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(MineAdapter.INSTANCE, src, "test:mine_full");

    assertEquals(120_000L, cfg.decayMs());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final MineConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(MineAdapter.INSTANCE, "mine { }\n", "test:mine_empty");

    // svs preset baseline: 12000cs = 120000ms = 2min.
    assertEquals(MineConfig.DEFAULTS, cfg);
    assertEquals(120_000L, cfg.decayMs());
  }

  @Test
  public void aliveTime_convertsCentisecondsToMs() {
    final String src = "mine { aliveTime 50 }\n";

    final MineConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(MineAdapter.INSTANCE, src, "test:mine_cs");

    assertEquals(500L, cfg.decayMs());
  }

  @Test
  public void aliveTime_negative_rejectedAtBoundary_returnsDefaults() {
    final String src = "mine { aliveTime -3 }\n";

    final MineConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(MineAdapter.INSTANCE, src, "test:mine_negCs");

    assertSame(MineConfig.DEFAULTS, cfg);
  }
}
