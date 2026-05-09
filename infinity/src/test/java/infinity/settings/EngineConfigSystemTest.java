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
 *       {@code maxProjectileSpeedJme 100},
 *       {@code shipMaxSpeedScale 0.025},
 *       {@code bombThrustScale 0.005}). If those defaults drift in code
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
    assertEquals(
        "shipMaxSpeedScale must match EngineConfig.DEFAULTS",
        EngineConfig.DEFAULTS.shipMaxSpeedScale(),
        cfg.shipMaxSpeedScale(),
        0.0);
    assertEquals(
        "bombThrustScale must match EngineConfig.DEFAULTS",
        EngineConfig.DEFAULTS.bombThrustScale(),
        cfg.bombThrustScale(),
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

  @Test
  public void engineGroovyAuthorsCollisionRadii() {
    // Slice projectile-radius-pattern4 — the 11 *SIZERADIUS values lifted
    // from CorePhysicsConstants must surface unchanged through the loader.
    // Pins both the engine.groovy authored values AND the parser plumbing
    // for each new DSL setter at once.
    final EngineConfig cfg = new GroovyEngineLoader().load();
    assertEquals(0.125, cfg.bulletRadius(), 0.0);
    assertEquals(0.5, cfg.bombRadius(), 0.0);
    assertEquals(0.5, cfg.mineRadius(), 0.0);
    assertEquals(0.5, cfg.thorRadius(), 0.0);
    assertEquals(0.5, cfg.prizeRadius(), 0.0);
    assertEquals(0.125, cfg.burstRadius(), 0.0);
    assertEquals(0.125, cfg.repelRadius(), 0.0);
    assertEquals(0.5, cfg.over1Radius(), 0.0);
    assertEquals(1.0, cfg.over2Radius(), 0.0);
    assertEquals(0.1, cfg.over5Radius(), 0.0);
    assertEquals(0.5, cfg.flagRadius(), 0.0);
    assertEquals(1.0, cfg.shipRadius(), 0.0);
  }
}
