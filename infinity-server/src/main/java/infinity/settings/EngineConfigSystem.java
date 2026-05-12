// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads {@code engine.groovy} once at init, hot-reloads via {@link GroovyFileWatcher}, exposes {@link EngineConfig} via {@link #get()}. */
public class EngineConfigSystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(EngineConfigSystem.class);

  // 5 s poll cadence — matches arena-tier default; hard-coded since engine-tier is above arena/zone.
  static final long POLL_INTERVAL_NANOS = 5_000_000_000L;

  private final GroovyEngineLoader loader;
  private final String classpathPath;
  private final Path watchedPathOverride;
  private volatile EngineConfig config = EngineConfig.DEFAULTS;

  // Watcher — null in production / classpath-only runs (engine.groovy not
  // reachable on disk), in which case update() short-circuits.
  private GroovyFileWatcher<EngineConfig> watcher;
  private long nextPollNanos;

  public EngineConfigSystem() {
    this(new GroovyEngineLoader(), GroovyEngineLoader.DEFAULT_PATH, null);
  }

  public EngineConfigSystem(final GroovyEngineLoader loader) {
    this(loader, GroovyEngineLoader.DEFAULT_PATH, null);
  }

  public EngineConfigSystem(final GroovyEngineLoader loader, final String classpathPath) {
    this(loader, classpathPath, null);
  }

  /** Test seam — explicit on-disk watch path bypasses {@link #resolveOnDisk}. */
  public EngineConfigSystem(
      final GroovyEngineLoader loader,
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
  public EngineConfig get() {
    return config;
  }

  private void registerWatch() {
    final Path onDisk = watchedPathOverride != null ? watchedPathOverride : resolveOnDisk(classpathPath);
    if (onDisk == null) {
      if (log.isDebugEnabled()) {
        log.debug("{} not on disk; engine-tier live reload disabled for this run", classpathPath);
      }
      return;
    }
    final GroovyFileWatcher<EngineConfig> w =
        new GroovyFileWatcher<>(
            onDisk,
            () -> loader.load(classpathPath),
            reloaded -> {
              config = reloaded;
              if (log.isInfoEnabled()) {
                log.info("Engine config reloaded from {}", onDisk);
              }
            });
    if (w.arm()) {
      watcher = w;
    }
  }

  /** Resolves classpath path to a watchable on-disk file; falls back to {@code src/main/resources} since engine.groovy ships with the jar. */
  private static Path resolveOnDisk(final String classpathPath) {
    final Path standard = GroovySettingsHost.INSTANCE.resolveOnDisk(classpathPath);
    if (standard != null) {
      return standard;
    }
    if (classpathPath == null || classpathPath.isBlank()) {
      return null;
    }
    final String relative =
        classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
    // Run-from-infinity-subproject + run-from-repo-root candidates. Mirrors
    // the dual-candidate shape in GroovySettingsHost.resolveOnDisk; just
    // points at the resources tree where engine.groovy actually lives.
    final Path[] candidates = {
        Paths.get("src/main/resources", relative),
        Paths.get("infinity/src/main/resources", relative),
    };
    for (final Path p : candidates) {
      if (Files.isReadable(p)) {
        return p;
      }
    }
    return null;
  }
}
