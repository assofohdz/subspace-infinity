// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.config.ZoneBotAiConfig;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zone-tier bot-AI config holder. Loads {@code zone-bot-ai.groovy} once at init, hot-reloads via
 * {@link GroovyFileWatcher}; the held snapshot is {@code volatile} so watcher writes publish safely
 * to the {@code TacticalPlanner} without further sync. Mirrors {@link EngineConfigSystem}; parse /
 * eval failures fall back to {@link ZoneBotAiConfig#DEFAULTS}. See ADR-0013 / ADR-0014.
 */
public class ZoneBotAiConfigSystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(ZoneBotAiConfigSystem.class);

  // 5 s poll cadence — matches the engine/arena tiers.
  static final long POLL_INTERVAL_NANOS = 5_000_000_000L;

  private final GroovyZoneBotAiLoader loader;
  private final String classpathPath;
  private final Path watchedPathOverride;
  private volatile ZoneBotAiConfig config = ZoneBotAiConfig.DEFAULTS;

  private GroovyFileWatcher<ZoneBotAiConfig> watcher;
  private long nextPollNanos;

  public ZoneBotAiConfigSystem() {
    this(new GroovyZoneBotAiLoader(), GroovyZoneBotAiLoader.DEFAULT_PATH, null);
  }

  /** Test seam — explicit on-disk watch path bypasses {@link GroovySettingsHost#resolveOnDisk}. */
  public ZoneBotAiConfigSystem(
      final GroovyZoneBotAiLoader loader,
      final String classpathPath,
      final Path watchedPathOverride) {
    this.loader = loader;
    this.classpathPath = classpathPath;
    this.watchedPathOverride = watchedPathOverride;
  }

  @Override
  protected void initialize() {
    config = loader.load(classpathPath);
    registerWatch();
  }

  @Override
  public void update(final SimTime time) {
    if (watcher == null) {
      return;
    }
    final long now = time.getTime();
    if (now < nextPollNanos) {
      return;
    }
    nextPollNanos = now + POLL_INTERVAL_NANOS;
    watcher.poll();
  }

  @Override
  protected void terminate() {
    watcher = null;
  }

  /** Current snapshot; never null (defaults if load failed). */
  public ZoneBotAiConfig get() {
    return config;
  }

  private void registerWatch() {
    final Path onDisk =
        watchedPathOverride != null
            ? watchedPathOverride
            : GroovySettingsHost.INSTANCE.resolveOnDisk(classpathPath);
    if (onDisk == null) {
      if (log.isDebugEnabled()) {
        log.debug("{} not on disk; zone-bot-ai live reload disabled for this run", classpathPath);
      }
      return;
    }
    final GroovyFileWatcher<ZoneBotAiConfig> w =
        new GroovyFileWatcher<>(
            onDisk,
            () -> loader.load(classpathPath),
            reloaded -> {
              config = reloaded;
              if (log.isInfoEnabled()) {
                log.info("Zone bot-AI config reloaded from {}", onDisk);
              }
            });
    if (w.arm()) {
      watcher = w;
    }
  }
}
