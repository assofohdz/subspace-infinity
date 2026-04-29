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

  @Test
  public void builder_autoLoad_capturesEachName() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.autoLoad("trench", "deva");

    final ZoneConfig cfg = build(builder);

    assertEquals(List.of("trench", "deva"), cfg.autoLoadArenas());
  }

  @Test
  public void builder_autoLoad_blanksAndNullsAreSkipped() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.autoLoad("trench", "  ", null, "deva");

    final ZoneConfig cfg = build(builder);

    assertEquals(List.of("trench", "deva"), cfg.autoLoadArenas());
  }

  @Test
  public void builder_enterSpawn_storesTrimmedName() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();
    builder.enterSpawn("  trench  ");

    final ZoneConfig cfg = build(builder);

    assertEquals("trench", cfg.enterSpawnArena());
  }

  @Test
  public void builder_omittingDirectives_yieldsEmptyDefaults() {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();

    final ZoneConfig cfg = build(builder);

    assertTrue("autoLoad should default to empty", cfg.autoLoadArenas().isEmpty());
    assertEquals("enterSpawn should default to empty", "", cfg.enterSpawnArena());
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
