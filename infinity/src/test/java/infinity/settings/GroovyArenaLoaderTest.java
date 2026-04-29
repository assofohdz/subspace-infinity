/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import infinity.config.ArenaConfig;
import infinity.settings.GroovyArenaLoader.ArenaConfigBuilder;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

/**
 * Mirrors {@code GroovyZoneLoaderTest} for the arena-scope loader: exercises
 * the package-private {@link ArenaConfigBuilder} for fast unit feedback, plus
 * end-to-end paths for missing-script (returns {@code null}, signalling INI
 * fallback) and broken-script (returns {@code ArenaConfig.EMPTY}, no silent
 * revert).
 */
public class GroovyArenaLoaderTest {

  @Test
  public void builder_capturesAllDirectives() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    builder.map("04-2026-trench/pub2025.lvl");
    builder.shipsScript("/conf/trench-04-2026/ships.groovy");
    builder.spawn(1000, 20);
    builder.includeFragment("/conf/trench-04-2026/trench.conf");
    builder.includeFragment("/conf/trench-04-2026/extra.conf");

    final ArenaConfig cfg = build(builder);

    assertEquals("04-2026-trench/pub2025.lvl", cfg.mapFile());
    assertEquals("/conf/trench-04-2026/ships.groovy", cfg.shipsScript());
    assertEquals(1000, cfg.spawnX());
    assertEquals(20, cfg.spawnZ());
    assertEquals(
        List.of("/conf/trench-04-2026/trench.conf", "/conf/trench-04-2026/extra.conf"),
        cfg.fragmentIncludes());
  }

  @Test
  public void builder_blankFragmentsSkipped() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    builder.includeFragment("/conf/a.conf");
    builder.includeFragment("");
    builder.includeFragment(null);
    builder.includeFragment("  ");
    builder.includeFragment("/conf/b.conf");

    final ArenaConfig cfg = build(builder);

    assertEquals(List.of("/conf/a.conf", "/conf/b.conf"), cfg.fragmentIncludes());
  }

  @Test
  public void builder_omittingDirectives_yieldsEmptyDefaults() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();

    final ArenaConfig cfg = build(builder);

    assertEquals("", cfg.mapFile());
    assertEquals("", cfg.shipsScript());
    assertEquals(0, cfg.spawnX());
    assertEquals(0, cfg.spawnZ());
    assertTrue(cfg.fragmentIncludes().isEmpty());
  }

  @Test
  public void load_missingScript_returnsNull_signallingIniFallback() {
    // The "no Groovy file" case is intentionally distinguished from "broken
    // Groovy file" so callers can fall back to the legacy INI loader without
    // logging a warning for unmigrated arenas.
    final ArenaConfig cfg = new GroovyArenaLoader().load("does-not-exist", "/nope.groovy");
    assertNull(cfg);
  }

  @Test
  public void evaluate_brokenSource_returnsEmpty_notNull() {
    // A parse / eval failure should NOT silently fall back to INI — it returns
    // ArenaConfig.EMPTY so the caller treats the arena as Groovy-managed but
    // misconfigured rather than reverting to the legacy path.
    final ArenaConfig cfg =
        new GroovyArenaLoader().evaluateSourceForTest("this is not valid groovy {{}", "broken.groovy");
    assertSame(ArenaConfig.EMPTY, cfg);
  }

  @Test
  public void evaluate_validDsl_capturesAllDirectives() {
    final String source =
        "arena {\n"
            + "  map '04-2026-trench/pub2025.lvl'\n"
            + "  shipsScript '/conf/trench-04-2026/ships.groovy'\n"
            + "  spawn 1000, 20\n"
            + "  wallFriction 0.5\n"
            + "  includeFragment '/conf/trench-04-2026/trench.conf'\n"
            + "}\n";

    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(source, "trench.groovy");

    assertEquals("04-2026-trench/pub2025.lvl", cfg.mapFile());
    assertEquals("/conf/trench-04-2026/ships.groovy", cfg.shipsScript());
    assertEquals(1000, cfg.spawnX());
    assertEquals(20, cfg.spawnZ());
    assertEquals(0.5, cfg.wallFriction(), 0.0);
    assertEquals(List.of("/conf/trench-04-2026/trench.conf"), cfg.fragmentIncludes());
  }

  @Test
  public void wallFriction_omitted_defaultsToEmptyValue() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    final ArenaConfig cfg = build(builder);
    assertEquals(ArenaConfig.EMPTY.wallFriction(), cfg.wallFriction(), 0.0);
  }

  @Test
  public void wallFriction_outsideRange_rejected() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.wallFriction(-0.01));
    assertThrows(IllegalArgumentException.class, () -> builder.wallFriction(1.01));
    assertThrows(IllegalArgumentException.class, () -> builder.wallFriction(Double.NaN));
    assertThrows(
        IllegalArgumentException.class, () -> builder.wallFriction(Double.POSITIVE_INFINITY));
  }

  @Test
  public void emptySnapshot_hasZeroDefaults() {
    final ArenaConfig empty = ArenaConfig.EMPTY;

    assertNotNull(empty);
    assertEquals("", empty.mapFile());
    assertEquals("", empty.shipsScript());
    assertEquals(0, empty.spawnX());
    assertEquals(0, empty.spawnZ());
    assertTrue(empty.fragmentIncludes().isEmpty());
    assertEquals(0.0, empty.wallFriction(), 0.0);
  }

  /** Reflective bridge to package-private build() — same trick as GroovyZoneLoaderTest. */
  private static ArenaConfig build(final ArenaConfigBuilder builder) {
    try {
      final Method m = ArenaConfigBuilder.class.getDeclaredMethod("build");
      m.setAccessible(true);
      return (ArenaConfig) m.invoke(builder);
    } catch (final ReflectiveOperationException e) {
      throw new AssertionError("ArenaConfigBuilder.build() not accessible", e);
    }
  }
}
