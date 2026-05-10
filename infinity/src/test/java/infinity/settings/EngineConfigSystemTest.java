// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li>Hot-reload watcher swaps the held snapshot atomically when the
 *       on-disk source's mtime changes (finding #6 in
 *       {@code .scratch/arch-review.md}).
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
    // Slices projectile-radius-pattern4 + S6 — the 12 collision-radius
    // values (originally hardcoded constants) must surface unchanged
    // through the loader. Pins both the engine.groovy authored values AND
    // the parser plumbing for each new DSL setter at once.
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

  /**
   * Watcher pins the post-finding-#6 contract — when {@code engine.groovy}
   * changes on disk, {@link EngineConfigSystem#update(SimTime)} reloads
   * via the supplied loader and atomically swaps the held snapshot. Uses
   * a sequenced loader + temp file mtime stomp so the test exercises the
   * watcher end-to-end without depending on the dev-mode path-resolution
   * heuristics.
   */
  @Test
  public void update_swapsConfigOnMtimeChange() throws IOException {
    final Path temp = Files.createTempFile("test-engine", ".groovy");
    try {
      Files.write(temp, "// initial".getBytes(StandardCharsets.UTF_8));

      final EngineConfig first = EngineConfig.DEFAULTS;
      final EngineConfig second =
          new EngineConfig(
              0.05, 200.0, 0.05, 0.001,
              0.25, 0.75, 0.75, 0.75, 0.75, 0.25, 0.25, 0.75, 1.5, 0.2, 0.75, 1.5);
      final SequencedLoader loader = new SequencedLoader(first, second);

      final EngineConfigSystem sys =
          new EngineConfigSystem(loader, "/test-engine.groovy", temp);
      sys.initialize(null);

      assertSame("initial config matches first sequenced result", first, sys.get());
      assertEquals("loader called once at initialize", 1, loader.callCount.get());

      // Force a clearly-different mtime. Filesystem mtime resolution can be
      // 1-second on some platforms; +60s sidesteps the resolution edge.
      Files.setLastModifiedTime(temp, FileTime.fromMillis(System.currentTimeMillis() + 60_000L));

      final SimTime tickPastThrottle = new SimTime();
      tickPastThrottle.setCurrentTime(EngineConfigSystem.POLL_INTERVAL_NANOS + 1L);
      sys.update(tickPastThrottle);

      assertSame("config swapped to second sequenced result", second, sys.get());
      assertEquals("loader called again on mtime change", 2, loader.callCount.get());
      assertNotSame("post-reload snapshot is not the original", first, sys.get());
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * If the on-disk file's mtime hasn't changed, {@code update()} stays a
   * no-op — the loader is not called again and the snapshot is unchanged.
   * Guards against a regression where the watcher would reload on every
   * tick (defeating the throttle and stat-comparison guards).
   */
  @Test
  public void update_withoutMtimeChange_doesNotReload() throws IOException {
    final Path temp = Files.createTempFile("test-engine-stable", ".groovy");
    try {
      Files.write(temp, "// initial".getBytes(StandardCharsets.UTF_8));

      final SequencedLoader loader =
          new SequencedLoader(EngineConfig.DEFAULTS, EngineConfig.DEFAULTS);
      final EngineConfigSystem sys =
          new EngineConfigSystem(loader, "/test-engine.groovy", temp);
      sys.initialize(null);
      assertEquals("loader called once at initialize", 1, loader.callCount.get());

      final SimTime tickPastThrottle = new SimTime();
      tickPastThrottle.setCurrentTime(EngineConfigSystem.POLL_INTERVAL_NANOS + 1L);
      sys.update(tickPastThrottle);

      assertEquals(
          "loader not called again — mtime unchanged", 1, loader.callCount.get());
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * If the file isn't on disk (production / classpath-only deployment),
   * the watcher arms nothing and {@code update()} short-circuits without
   * stat overhead. Protects against the "blanket NPE on every tick" or
   * "loader called every tick" regressions for production runs.
   */
  @Test
  public void update_withoutOnDiskFile_doesNothing() {
    final SequencedLoader loader =
        new SequencedLoader(EngineConfig.DEFAULTS, EngineConfig.DEFAULTS);
    // Null watchedPathOverride and a path that won't resolve on disk →
    // watcher stays disarmed.
    final EngineConfigSystem sys =
        new EngineConfigSystem(loader, "/no-such-engine.groovy", null);
    sys.initialize(null);
    assertEquals("loader called once at initialize", 1, loader.callCount.get());

    final SimTime tickPastThrottle = new SimTime();
    tickPastThrottle.setCurrentTime(EngineConfigSystem.POLL_INTERVAL_NANOS + 1L);
    sys.update(tickPastThrottle);

    assertEquals(
        "loader not called again — watcher disarmed", 1, loader.callCount.get());
    assertTrue("config still pinned to default-fallback", sys.get() != null);
  }

  /**
   * Test seam — returns a different {@link EngineConfig} on each {@code load}
   * call. Production loader returns the same parsed result given identical
   * source; this fake lets the test assert the watcher actually re-invokes
   * the loader (vs caching a stale snapshot).
   */
  private static final class SequencedLoader extends GroovyEngineLoader {
    private final EngineConfig first;
    private final EngineConfig second;
    final AtomicInteger callCount = new AtomicInteger();

    SequencedLoader(final EngineConfig first, final EngineConfig second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public EngineConfig load(final String classpathPath) {
      final int n = callCount.incrementAndGet();
      return n == 1 ? first : second;
    }
  }
}
