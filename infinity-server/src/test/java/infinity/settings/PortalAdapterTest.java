// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.PortalConfig;
import org.junit.Test;

/** Pins {@link PortalAdapter} DSL → {@link PortalConfig} translation; see REFERENCE.md {@code ## Misc} ({@code WarpPointDelay}). */
public class PortalAdapterTest {

  @Test
  public void evaluate_activeTime_capturedAsMs() {
    // WarpPointDelay lives in [Misc] but semantically owns Portal point active
    // time — REFERENCE.md cross-section. Centiseconds → ms (×10).
    final String src = "portal { activeTime 6000 }\n";

    final PortalConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PortalAdapter.INSTANCE, src, "test:portal_full");

    assertEquals(60_000L, cfg.activeTimeMs());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final PortalConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PortalAdapter.INSTANCE, "portal { }\n", "test:portal_empty");

    assertEquals(PortalConfig.DEFAULTS, cfg);
    assertEquals(60_000L, cfg.activeTimeMs());
  }

  @Test
  public void activeTime_convertsCentisecondsToMs() {
    final String src = "portal { activeTime 1234 }\n";

    final PortalConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PortalAdapter.INSTANCE, src, "test:portal_cs");

    assertEquals(12_340L, cfg.activeTimeMs());
  }

  @Test
  public void activeTime_negative_rejectedAtBoundary_returnsDefaults() {
    final String src = "portal { activeTime -1 }\n";

    final PortalConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(PortalAdapter.INSTANCE, src, "test:portal_negCs");

    assertSame(PortalConfig.DEFAULTS, cfg);
  }
}
