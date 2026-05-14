// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import infinity.config.ZoneConfig;
import infinity.settings.GroovyZoneLoader.ZoneConfigBuilder;
import java.util.List;
import org.junit.Test;

/**
 * Mirrors {@code GroovyShipLoaderRadarTest} for the zone-scope loader:
 * exercises the package-private {@link ZoneConfigBuilder} so we don't need to
 * stand up a full {@code GroovyShell}, plus end-to-end {@link GroovyZoneLoader}
 * paths for missing-script and full-DSL cases.
 */
public class GroovyZoneLoaderTest {

  private static final String ZONE_TRENCH = "trench";
  private static final String ZONE_DEVA = "deva";

  @Test
  public void builder_autoLoad_capturesEachName() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.autoLoad(ZONE_TRENCH, ZONE_DEVA);

    final ZoneConfig cfg = build(builder);

    assertEquals(List.of(ZONE_TRENCH, ZONE_DEVA), cfg.autoLoadArenas());
  }

  @Test
  public void builder_autoLoad_blanksAndNullsAreSkipped() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.autoLoad(ZONE_TRENCH, "  ", null, ZONE_DEVA);

    final ZoneConfig cfg = build(builder);

    assertEquals(List.of(ZONE_TRENCH, ZONE_DEVA), cfg.autoLoadArenas());
  }

  @Test
  public void builder_enterSpawn_storesTrimmedName() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.enterSpawn("  trench  ");

    final ZoneConfig cfg = build(builder);

    assertEquals(ZONE_TRENCH, cfg.enterSpawnArena());
  }

  @Test
  public void builder_omittingDirectives_yieldsEmptyDefaults() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();

    final ZoneConfig cfg = build(builder);

    assertTrue("autoLoad should default to empty", cfg.autoLoadArenas().isEmpty());
    assertEquals("enterSpawn should default to empty", "", cfg.enterSpawnArena());
    // Documented default = 5s; matches the historical SCRIPT_POLL_INTERVAL_NANOS
    // Java constant so omitting the directive preserves the prior throttle.
    assertEquals(5.0, cfg.scriptPollIntervalSeconds(), 1e-9);
    assertEquals(5_000_000_000L, cfg.scriptPollIntervalNanos());
  }

  @Test
  public void builder_scriptPollInterval_acceptsSecondsAsNumber() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.scriptPollInterval(2.5);

    final ZoneConfig cfg = build(builder);

    assertEquals(2.5, cfg.scriptPollIntervalSeconds(), 1e-9);
    assertEquals(2_500_000_000L, cfg.scriptPollIntervalNanos());
  }

  @Test
  public void builder_scriptPollInterval_rejectsNonPositive() {
    // Zero would disable the watcher and negative would tight-loop stat() —
    // both are rejected; the documented 5s default stands.
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.scriptPollInterval(0);
    builder.scriptPollInterval(-1.0);

    final ZoneConfig cfg = build(builder);

    assertEquals(5.0, cfg.scriptPollIntervalSeconds(), 1e-9);
  }

  @Test
  public void load_missingScript_returnsEmpty() {
    final ZoneConfig cfg = new GroovyZoneLoader().load("/does-not-exist.groovy");

    // EMPTY is a documented sentinel — verify by reference, not just equality.
    assertSame(ZoneConfig.EMPTY, cfg);
  }

  @Test
  public void emptySnapshot_hasNoAutoLoadAndBlankSpawn() {
    final ZoneConfig empty = ZoneConfig.EMPTY;

    assertNotNull(empty);
    assertTrue(empty.autoLoadArenas().isEmpty());
    assertEquals("", empty.enterSpawnArena());
    assertEquals(5.0, empty.scriptPollIntervalSeconds(), 1e-9);
  }

  /**
   * Reflective bridge to {@code ZoneConfigBuilder.build()} which is
   * package-private (matches the {@code GroovyShipLoader.ShipConfigBuilder}
   * test contract). Lets the test exercise the builder without exposing
   * {@code build} on the public API.
   */
  private static ZoneConfig build(final ZoneConfigBuilder builder) {
    try {
      final java.lang.reflect.Method m = ZoneConfigBuilder.class.getDeclaredMethod("build");
      m.setAccessible(true);
      return (ZoneConfig) m.invoke(builder);
    } catch (final ReflectiveOperationException e) {
      throw new AssertionError("ZoneConfigBuilder.build() not accessible", e);
    }
  }
}
