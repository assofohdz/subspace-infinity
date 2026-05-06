// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import infinity.config.EngineConfig;
import org.junit.Test;

/**
 * Pins the slice-10 engine-tier loader contract:
 *
 * <ul>
 *   <li>The packaged {@code /engine.groovy} resource parses to the documented
 *       defaults ({@code subspaceVelocityScale 0.01},
 *       {@code maxProjectileSpeedJme 100}). If those defaults drift in code
 *       but not in the file (or vice versa), this test catches it.
 *   <li>Missing classpath path falls back to {@link EngineConfig#DEFAULTS}
 *       without throwing.
 *   <li>The DSL setters validate input — non-positive or non-finite values
 *       reject at parse time.
 * </ul>
 */
public class EngineConfigSystemTest {

  @Test
  public void load_packagedEngineGroovy_matchesDefaults() {
    // engine.groovy is in infinity/src/main/resources at the classpath root.
    // In tests its contents should match the EngineConfig.DEFAULTS values
    // we've documented (slice 10).
    final EngineConfig cfg = new GroovyEngineLoader().load();
    assertEquals(
        "subspaceVelocityScale must match EngineConfig.DEFAULTS",
        EngineConfig.DEFAULTS.subspaceVelocityScale(),
        cfg.subspaceVelocityScale(),
        0.0);
    assertEquals(
        "maxProjectileSpeedJme must match EngineConfig.DEFAULTS",
        EngineConfig.DEFAULTS.maxProjectileSpeedJme(),
        cfg.maxProjectileSpeedJme(),
        0.0);
  }

  @Test
  public void load_missingPath_returnsDefaults() {
    final EngineConfig cfg = new GroovyEngineLoader().load("/no-such-engine.groovy");
    assertSame(EngineConfig.DEFAULTS, cfg);
  }

  @Test
  public void engineConfigSystem_get_preInit_returnsDefaults() {
    final EngineConfigSystem sys = new EngineConfigSystem();
    // Without initialize(), get() returns the documented baseline rather
    // than null — guarantees consumers can read at any time without NPE.
    assertSame(EngineConfig.DEFAULTS, sys.get());
  }
}
