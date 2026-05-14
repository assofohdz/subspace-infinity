// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.BulletConfig;
import org.junit.Test;

/** Pins {@link BulletAdapter} DSL → {@link BulletConfig} translation; see REFERENCE.md {@code ## Bullet}. */
public class BulletAdapterTest {

  @Test
  public void evaluate_completeFragment_capturesAllFields() {
    // BulletDamageLevel = L1 base, BulletDamageUpgrade = additive per level,
    // BulletAliveTime = cs → ms (×10).
    final String src =
        "bullet {\n"
            + "  damageLevel 200\n"
            + "  damageUpgrade 75\n"
            + "  aliveTime 5500\n"
            + "}\n";

    final BulletConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BulletAdapter.INSTANCE, src, "test:bullet_full");

    assertEquals(200, cfg.damage());
    assertEquals(75, cfg.damageUpgrade());
    assertEquals(55_000L, cfg.decayMs());
  }

  @Test
  public void evaluate_emptyBlock_yieldsCanonicalDefaults() {
    final BulletConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BulletAdapter.INSTANCE, "bullet { }\n", "test:bullet_empty");

    assertEquals(BulletConfig.DEFAULTS, cfg);
  }

  @Test
  public void aliveTime_convertsCentisecondsToMs() {
    final String src = "bullet { aliveTime 550 }\n";

    final BulletConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BulletAdapter.INSTANCE, src, "test:bullet_cs");

    assertEquals(5_500L, cfg.decayMs());
  }

  @Test
  public void damageAtLevel_pinned_byBulletConfigInvariant() {
    // Pin Subspace canon damage scaling formula: damage + (level-1) * upgrade.
    final BulletConfig cfg = new BulletConfig(100, 50, 5500L);

    assertEquals(100, cfg.damageAtLevel(1));
    assertEquals(150, cfg.damageAtLevel(2));
    assertEquals(200, cfg.damageAtLevel(3));
    assertEquals(250, cfg.damageAtLevel(4));
  }

  @Test
  public void aliveTime_negative_rejectedAtBoundary_returnsDefaults() {
    final String src = "bullet { aliveTime -1 }\n";

    final BulletConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(BulletAdapter.INSTANCE, src, "test:bullet_negCs");

    assertSame(BulletConfig.DEFAULTS, cfg);
  }
}
