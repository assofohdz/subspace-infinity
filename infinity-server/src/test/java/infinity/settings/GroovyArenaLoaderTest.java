// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import infinity.config.ArenaConfig;
import infinity.config.SpawnerSpec;
import infinity.modules.ArenaModuleDeclarations;
import infinity.modules.ModuleSpec;
import infinity.settings.GroovyArenaLoader.ArenaConfigBuilder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Mirrors {@code GroovyZoneLoaderTest} for the arena-scope loader: exercises
 * the package-private {@link ArenaConfigBuilder} for fast unit feedback, plus
 * end-to-end paths for missing-script (returns {@code null}, signalling INI
 * fallback) and broken-script (returns {@code ArenaConfig.EMPTY}, no silent
 * revert).
 */
public class GroovyArenaLoaderTest {

  private static final String ARENA_OPEN = "arena {\n";
  private static final String BLOCK_CLOSE = "}\n";

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
    // Spawn defaults to the arena centre (512, 512) — the documented
    // ArenaConfig.EMPTY contract — so an arena.groovy that forgets the
    // `spawn` directive puts players in the middle, not the NW corner.
    assertEquals(512, cfg.spawnX());
    assertEquals(512, cfg.spawnZ());
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
        ARENA_OPEN
            + "  map '04-2026-trench/pub2025.lvl'\n"
            + "  shipsScript '/conf/trench-04-2026/ships.groovy'\n"
            + "  spawn 1000, 20\n"
            + "  wallFriction 0.5\n"
            + "  includeFragment '/conf/trench-04-2026/trench.conf'\n"
            + BLOCK_CLOSE;

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
  public void evaluate_spawnersBlock_omittedNewFields_useNoOpDefaults() {
    // Slice 8d (C2) introduced 4 optional spawner fields. Authors that
    // pre-date the rename keep their old DSL working with no behavioural
    // change because the new fields collapse to no-op defaults
    // (countPerPlayer=0, radiusPerPlayer=0, regenBatch=1, hidden=false).
    final String source =
        ARENA_OPEN
            + "  map 'foo.lvl'\n"
            + "  spawners {\n"
            + "    spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000\n"
            + "  }\n"
            + BLOCK_CLOSE;

    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(source, "t.groovy");

    assertEquals(1, cfg.spawners().size());
    final SpawnerSpec s = cfg.spawners().get(0);
    assertEquals(0, s.countPerPlayer());
    assertEquals(0.0, s.radiusPerPlayer(), 0.0);
    assertEquals(1, s.regenBatch());
    assertEquals(false, s.hidden());
  }

  @Test
  public void evaluate_spawnersBlock_explicitNewFields_passThrough() {
    final String source =
        ARENA_OPEN
            + "  map 'foo.lvl'\n"
            + "  spawners {\n"
            + "    spawn x: 512, z: 512, radius: 400, maxCount: 5, intervalMs: 1500,\n"
            + "          countPerPlayer: 2, radiusPerPlayer: 50, regenBatch: 3, hidden: true\n"
            + "  }\n"
            + BLOCK_CLOSE;

    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(source, "t.groovy");

    assertEquals(1, cfg.spawners().size());
    final SpawnerSpec s = cfg.spawners().get(0);
    assertEquals(2, s.countPerPlayer());
    assertEquals(50.0, s.radiusPerPlayer(), 0.0);
    assertEquals(3, s.regenBatch());
    assertEquals(true, s.hidden());
  }

  @Test
  public void emptySnapshot_hasDocumentedDefaults() {
    final ArenaConfig empty = ArenaConfig.EMPTY;

    assertNotNull(empty);
    assertEquals("", empty.mapFile());
    assertEquals("", empty.shipsScript());
    // Spawn defaults to the arena centre (512, 512) so an unconfigured arena
    // (or one whose arena.groovy omits `spawn`) puts the player at the middle
    // of the map instead of the NW corner.
    assertEquals(512, empty.spawnX());
    assertEquals(512, empty.spawnZ());
    assertTrue(empty.fragmentIncludes().isEmpty());
    assertEquals(0.0, empty.wallFriction(), 0.0);
    // Slice 9a — friendly-fire defaults to 0 (off) so unmigrated arenas keep
    // the safer "no same-team damage" behaviour without authoring the knob.
    assertEquals(0, empty.friendlyFire());
  }

  @Test
  public void friendlyFire_omitted_defaultsToOff() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    final ArenaConfig cfg = build(builder);
    assertEquals(0, cfg.friendlyFire());
  }

  @Test
  public void friendlyFire_validValues_passThrough() {
    for (int v = 0; v <= 2; v++) {
      final ArenaConfigBuilder builder = new ArenaConfigBuilder();
      builder.friendlyFire(v);
      assertEquals(v, build(builder).friendlyFire());
    }
  }

  @Test
  public void friendlyFire_outOfRange_rejected() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.friendlyFire(-1));
    assertThrows(IllegalArgumentException.class, () -> builder.friendlyFire(3));
    assertThrows(IllegalArgumentException.class, () -> builder.friendlyFire(null));
  }

  @Test
  public void evaluate_friendlyFireDirective_capturedInConfig() {
    final String source =
        ARENA_OPEN
            + "  map 'foo.lvl'\n"
            + "  friendlyFire 1\n"
            + BLOCK_CLOSE;

    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(source, "t.groovy");

    assertEquals(1, cfg.friendlyFire());
  }

  @Test
  public void builder_emptyModuleDeclarations_byDefault() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    final ArenaConfig cfg = build(builder);
    assertEquals(ArenaModuleDeclarations.EMPTY, cfg.modules());
  }

  @Test
  public void builder_capturesSinglePickAndLayeredAndMechanic() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    builder.teamSetup(Map.of(), "ffa-private-freqs");
    builder.roster("all-ships");
    builder.scoring(Map.of("perKill", 100), "kill-points");
    builder.scoring("flag-points");
    builder.mechanic(Map.of("count", 1), "crowns");

    final ArenaModuleDeclarations decls = build(builder).modules();
    assertEquals("ffa-private-freqs", decls.teamSetup().orElseThrow().moduleId());
    assertEquals("all-ships", decls.roster().orElseThrow().moduleId());
    assertEquals(2, decls.scoring().size());
    assertEquals("kill-points", decls.scoring().get(0).moduleId());
    assertEquals(100, decls.scoring().get(0).kwargs().get("perKill"));
    assertEquals(1, decls.mechanics().size());
    assertEquals("crowns", decls.mechanics().get("crowns").moduleId());
  }

  @Test
  public void builder_singlePickLastWins() {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();
    builder.teamSetup("first");
    builder.teamSetup("second");
    final ModuleSpec spec = build(builder).modules().teamSetup().orElseThrow();
    assertEquals("second", spec.moduleId());
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
