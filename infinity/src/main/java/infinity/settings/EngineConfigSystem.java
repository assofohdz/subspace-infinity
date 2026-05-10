// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-tier config holder — the fourth scope above preset / arena / zone.
 * Loads {@code engine.groovy} once at server initialize and exposes the
 * resulting {@link EngineConfig} for runtime consumers.
 *
 * <p>Runtime consumers (today: {@code WeaponsSystem.getAttackInfo} for the
 * projectile-speed scale + cap, {@code RepelSystem}, {@code PrizeSystem},
 * {@code PlayerDriver}) call {@link #get()} to read the current snapshot.
 * The held snapshot is {@code volatile}, so re-assignment by the watcher
 * publishes safely to other threads without further synchronization.
 *
 * <p>Loading sequence:
 * <ol>
 *   <li>{@code GroovyEngineLoader.load()} reads {@code /engine.groovy}
 *       from the classpath.
 *   <li>Failure (missing file, parse error) falls back to
 *       {@link EngineConfig#DEFAULTS} with a warning log.
 *   <li>The system holds the result for the lifetime of the server, with
 *       the file watcher (below) replacing it atomically when the on-disk
 *       source is edited.
 * </ol>
 *
 * <p><b>Hot-reload watcher</b> — mirrors {@link infinity.systems.ArenaReloadWatcher}
 * one tier up. {@link #initialize()} stats {@code engine.groovy} on disk
 * and registers a watch; {@link #update(SimTime)} polls mtime once per
 * {@link #POLL_INTERVAL_NANOS} and, on change, calls
 * {@link GroovyEngineLoader#load(String)} to produce a fresh snapshot and
 * atomically swaps it into the {@code volatile config} field. Consumers
 * see the new snapshot on their next {@link #get()} call — no broadcast
 * needed because every consumer re-reads {@code get()} on every use, and
 * {@code EngineConfig} is an immutable record (per
 * {@code .claude/rules/config-pattern.md} — replace, don't mutate).
 *
 * <p>Single-file source means simpler than the per-arena watcher in
 * {@link infinity.systems.ArenaReloadWatcher}: no map of arenas, no
 * register/unregister lifecycle on arena load/unload. One watched file,
 * one snapshot field.
 *
 * <p><b>Failure modes</b> mirror the arena watcher:
 * <ul>
 *   <li><b>File not on disk</b> (production / classpath-only deployments)
 *       — {@code initialize()} no-ops the watcher with a debug log; live
 *       reload is silently disabled and {@code update()} returns early.
 *   <li><b>{@code stat()} throws</b> mid-poll — logged at debug level and
 *       the next tick retries.
 *   <li><b>Reload throws</b> — {@code GroovyEngineLoader.load} already
 *       catches and returns {@link EngineConfig#DEFAULTS} on parse / eval
 *       failure, so the watcher swap-in cannot leak an exception. Any
 *       residual {@code RuntimeException} is logged + contained so the
 *       watcher stays armed.
 * </ul>
 *
 * <p><b>Polling cadence</b> is fixed at 5 s — matches
 * {@link infinity.config.ZoneConfig#scriptPollIntervalSeconds()}'s default
 * so engine-tier and arena-tier hot-reload feel identical to a developer
 * editing both. Engine-tier doesn't bind to {@code ZoneConfig} directly to
 * avoid an arena-tier dependency at engine-tier (engine is "above"
 * arena/zone in the config hierarchy).
 *
 * <p>Registered in {@code GameServer} alongside the other persistent
 * config systems (e.g. {@code ConfigRegistrySystem}).
 */
public class EngineConfigSystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(EngineConfigSystem.class);

  /**
   * Hot-reload poll cadence. Mirrors the default
   * {@link infinity.config.ZoneConfig#scriptPollIntervalSeconds()} (5 s)
   * so engine-tier and arena-tier feel identical when a developer edits
   * either file. Hard-coded rather than read from {@code ZoneConfig}
   * because engine-tier is above arena/zone in the config hierarchy and
   * shouldn't depend on it.
   */
  static final long POLL_INTERVAL_NANOS = 5_000_000_000L;

  private final GroovyEngineLoader loader;
  private final String classpathPath;
  private final Path watchedPathOverride;
  private volatile EngineConfig config = EngineConfig.DEFAULTS;

  // Watcher state — only set when engine.groovy is reachable on disk.
  // Stays null in production / classpath-only runs, in which case
  // update() short-circuits.
  private Path watchedPath;
  private FileTime lastModified;
  private long nextPollNanos;

  /** Production constructor — uses the default classpath path. */
  public EngineConfigSystem() {
    this(new GroovyEngineLoader(), GroovyEngineLoader.DEFAULT_PATH, null);
  }

  /** Test seam — caller-supplied loader, default path. */
  public EngineConfigSystem(final GroovyEngineLoader loader) {
    this(loader, GroovyEngineLoader.DEFAULT_PATH, null);
  }

  /** Test seam — caller-supplied loader and classpath path. */
  public EngineConfigSystem(final GroovyEngineLoader loader, final String classpathPath) {
    this(loader, classpathPath, null);
  }

  /**
   * Test seam — caller-supplied loader, classpath path, and an explicit
   * on-disk path to watch (bypasses {@link #resolveOnDisk}). Production
   * code never supplies the override; tests use it to drop a temp
   * {@code engine.groovy} on disk and exercise the hot-reload path
   * end-to-end without depending on the dev-mode path-resolution
   * heuristics.
   */
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
    if (watchedPath == null) {
      return;
    }
    final long now = time.getTime();
    if (now < nextPollNanos) {
      return;
    }
    nextPollNanos = now + POLL_INTERVAL_NANOS;
    pollWatch();
  }

  @Override
  protected void terminate() {
    watchedPath = null;
    lastModified = null;
  }

  /**
   * Current engine-tier config snapshot. Never returns {@code null} —
   * pre-{@code initialize()} returns {@link EngineConfig#DEFAULTS}, and
   * post-init returns the loaded config (or DEFAULTS if loading failed).
   * Always read fresh — the field is {@code volatile} and the watcher
   * replaces (never mutates) the snapshot on hot-reload.
   */
  public EngineConfig get() {
    return config;
  }

  /**
   * Stat the on-disk source for {@link #classpathPath} and arm the
   * watcher. No-op if no on-disk path is reachable (production /
   * classpath-only deployment) or if the stat fails.
   */
  private void registerWatch() {
    final Path onDisk = watchedPathOverride != null ? watchedPathOverride : resolveOnDisk(classpathPath);
    if (onDisk == null) {
      if (log.isDebugEnabled()) {
        log.debug("{} not on disk; engine-tier live reload disabled for this run", classpathPath);
      }
      return;
    }
    try {
      lastModified = Files.getLastModifiedTime(onDisk);
      watchedPath = onDisk;
      if (log.isInfoEnabled()) {
        log.info("Watching {} for engine-tier hot-reload", onDisk);
      }
    } catch (final IOException e) {
      if (log.isWarnEnabled()) {
        log.warn("Could not stat {} to enable engine-tier live reload: {}", onDisk, e.toString());
      }
    }
  }

  /**
   * Stat the watched file; if mtime changed, reload via
   * {@link GroovyEngineLoader#load(String)} and atomically replace the
   * snapshot. Stat failures are skipped; reload exceptions are logged
   * and contained so the watcher stays armed.
   */
  private void pollWatch() {
    final FileTime current;
    try {
      current = Files.getLastModifiedTime(watchedPath);
    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("Stat failed for {}; skipping engine-tier reload tick", watchedPath);
      }
      return;
    }
    if (current.equals(lastModified)) {
      return;
    }
    lastModified = current;
    try {
      final EngineConfig reloaded = loader.load(classpathPath);
      config = reloaded;
      if (log.isInfoEnabled()) {
        log.info("Engine config reloaded from {}", watchedPath);
      }
    } catch (final RuntimeException e) {
      if (log.isWarnEnabled()) {
        log.warn("Engine-tier reload of {} failed: {}", watchedPath, e.toString());
      }
    }
  }

  /**
   * Resolve {@code classpathPath} to an on-disk file usable for mtime
   * polling. First tries the standard zone resolution used by every other
   * Groovy adapter ({@code zone/<rel>}, {@code infinity/zone/<rel>}); if
   * that misses, falls back to the {@code src/main/resources} candidates
   * because {@code engine.groovy} is packaged with the jar (developer-tuned)
   * rather than under {@code zone/} (operator-tuned), so its on-disk dev
   * source lives in resources rather than under {@code zone/}.
   *
   * <p>Returns {@code null} when no candidate resolves — production
   * classpath-only deployments fall through here and live reload stays
   * silently disabled.
   */
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
