// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import infinity.config.BombConfig;
import org.junit.Test;

/** Pins {@link BombAdapter} DSL → {@link BombConfig} translation; see REFERENCE.md {@code ## Bomb}. */
public class BombAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesAllFields() {
    // Subspace canon: BombDamageLevel raw, BombAliveTime cs (×10 → ms),
    // BombExplodePixels-in-tiles (Infinity native), ProximityDistance tiles,
    // BombExplodeDelay cs, JitterTime cs.
    final String src =
        "bomb {\n"
            + "  damageLevel 1500\n"
            + "  aliveTimeCs 6000\n"
            + "  explodeRadius 8.5\n"
            + "  proximityDistance 4\n"
            + "  explodeDelayCs 50\n"
            + "  bombSafety true\n"
            + "  jitterTimeCs 25\n"
            + "  repellable false\n"
            + "}\n";

    final BombConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BombAdapter.INSTANCE, src, "test:bomb_full");

    assertEquals(1500, cfg.damage());
    assertEquals(60_000L, cfg.decayMs());
    assertEquals(8.5, cfg.explodeRadius(), 0.0);
    assertEquals(4, cfg.proximityDistance());
    assertEquals(500L, cfg.explodeDelayMs());
    assertTrue(cfg.bombSafety());
    assertEquals(250L, cfg.jitterTimeMs());
    assertEquals(false, cfg.repellable());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final BombConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BombAdapter.INSTANCE, "bomb { }\n", "test:bomb_empty");

    // Defaults pinned at REFERENCE.md §Bomb canonical values; per BombConfig.DEFAULTS.
    assertEquals(BombConfig.DEFAULTS, cfg);
  }

  @Test
  public void aliveTimeCs_convertsCentisecondsToMs() {
    // Subspace VIE timing convention: cs × 10 → ms at the loader boundary.
    final String src = "bomb { aliveTimeCs 1234 }\n";

    final BombConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BombAdapter.INSTANCE, src, "test:bomb_cs");

    assertEquals(12_340L, cfg.decayMs());
  }

  @Test
  public void evaluate_noScript_returnsAdapterEmpty_canonicalDefaults() {
    // No top-level call to bomb {…}; builder stays at defaults.
    final BombConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BombAdapter.INSTANCE, "// comment only\n", "test:bomb_noop");

    assertEquals(BombConfig.DEFAULTS, cfg);
  }

  @Test
  public void aliveTimeCs_negative_rejectedAtBoundary_returnsAdapterEmpty() {
    // Validators.centisecondsToMs throws on negative; host catches → empty().
    final String src = "bomb { aliveTimeCs -1 }\n";

    final BombConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BombAdapter.INSTANCE, src, "test:bomb_negCs");

    assertSame("eval failure must return BombConfig.DEFAULTS sentinel", BombConfig.DEFAULTS, cfg);
  }

  @Test
  public void explodeRadius_nanOrInfinite_rejectedByValidator() {
    // Direct builder check (host swallows; validator surface is what's load-bearing).
    final BombAdapter.BombBuilder b = new BombAdapter.BombBuilder();
    assertThrows(IllegalArgumentException.class, () -> b.explodeRadius(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> b.explodeRadius(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> b.explodeRadius(-1.0));
  }
}
