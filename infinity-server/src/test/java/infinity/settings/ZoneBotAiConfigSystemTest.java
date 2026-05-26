// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.simsilica.sim.SimTime;
import infinity.config.ZoneBotAiConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Pins the zone-tier bot-AI loader/holder: {@code zone-bot-ai.groovy} ↔ {@link ZoneBotAiConfig#DEFAULTS}, fallback, hot-reload. */
public class ZoneBotAiConfigSystemTest {

  @Test
  public void load_packagedZoneGroovy_matchesDefaults() {
    final ZoneBotAiConfig cfg = new GroovyZoneBotAiLoader().load();
    assertEquals(
        ZoneBotAiConfig.DEFAULTS.plannerCadenceMillis(), cfg.plannerCadenceMillis());
    assertEquals(ZoneBotAiConfig.DEFAULTS.stickinessMargin(), cfg.stickinessMargin(), 0.0);
    assertEquals(ZoneBotAiConfig.DEFAULTS.minFraction(), cfg.minFraction(), 0.0);
    assertEquals(
        ZoneBotAiConfig.DEFAULTS.minBehaviourWeight(), cfg.minBehaviourWeight(), 0.0);
  }

  @Test
  public void load_missingPath_returnsDefaults() {
    final ZoneBotAiConfig cfg = new GroovyZoneBotAiLoader().load("/no-such-zone-bot-ai.groovy");
    assertSame(ZoneBotAiConfig.DEFAULTS, cfg);
  }

  @Test
  public void system_get_preInit_returnsDefaults() {
    assertSame(ZoneBotAiConfig.DEFAULTS, new ZoneBotAiConfigSystem().get());
  }

  @Test
  public void update_swapsConfigOnMtimeChange() throws IOException {
    final Path temp = Files.createTempFile("test-zone-bot-ai", ".groovy");
    try {
      Files.write(temp, "// initial".getBytes(StandardCharsets.UTF_8));
      final ZoneBotAiConfig first = ZoneBotAiConfig.DEFAULTS;
      final ZoneBotAiConfig second =
          new ZoneBotAiConfig(
              500L, 0.20, 0.40, 0.10, 8000L, 8, 500L, 4, 25, 10, 0.8, 6, 5, 0.4, 0.5, 0.2, true, 8);
      final SequencedLoader loader = new SequencedLoader(first, second);

      final ZoneBotAiConfigSystem sys =
          new ZoneBotAiConfigSystem(loader, "/test-zone-bot-ai.groovy", temp);
      sys.initialize(null);
      assertSame(first, sys.get());
      assertEquals(1, loader.callCount.get());

      Files.setLastModifiedTime(temp, FileTime.fromMillis(System.currentTimeMillis() + 60_000L));
      final SimTime tick = new SimTime();
      tick.setCurrentTime(ZoneBotAiConfigSystem.POLL_INTERVAL_NANOS + 1L);
      sys.update(tick);

      assertSame(second, sys.get());
      assertEquals(2, loader.callCount.get());
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** Returns a different config on each load so the watcher's re-invoke is observable. */
  private static final class SequencedLoader extends GroovyZoneBotAiLoader {
    private final ZoneBotAiConfig first;
    private final ZoneBotAiConfig second;
    final AtomicInteger callCount = new AtomicInteger();

    SequencedLoader(final ZoneBotAiConfig first, final ZoneBotAiConfig second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public ZoneBotAiConfig load(final String classpathPath) {
      return callCount.incrementAndGet() == 1 ? first : second;
    }
  }
}
