// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-file mtime poll watcher used by Groovy config holders for
 * dev-mode hot-reload. Collapses the previously-duplicated poll loops in
 * {@link infinity.systems.ArenaSystem}'s zone.groovy watcher and
 * {@link EngineConfigSystem}'s engine.groovy watcher.
 *
 * <p>Logging style follows {@link EngineConfigSystem}'s side of the
 * original duplicate: {@code isXxxEnabled()} guards on every line plus a
 * defensive {@code RuntimeException} catch around the loader call so a
 * single bad reload can't disarm the watcher. {@link infinity.systems.ArenaSystem}'s
 * original poll lacked the catch — this is the only deliberate refinement,
 * the watcher's per-reload INFO log is intentionally omitted so callers
 * can keep their own consumer-specific success messages verbatim.
 *
 * <p>Single-thread by contract — {@link #arm()} once at startup and
 * {@link #poll()} from a single thread (typically the sim thread's tick).
 * No internal synchronization; if multiple threads must observe the
 * reloaded value, the {@code onLoaded} consumer is responsible for
 * publishing it (e.g. assigning to a {@code volatile} field).
 *
 * @param <T> the typed config snapshot the watched file produces
 */
public final class GroovyFileWatcher<T> {

  private static final Logger log = LoggerFactory.getLogger(GroovyFileWatcher.class);

  private final Path path;
  private final Supplier<T> loader;
  private final Consumer<T> onLoaded;
  private FileTime lastModified;
  private boolean armed;

  /**
   * Construct a watcher for {@code path}. {@code loader} is invoked when
   * the file's mtime changes; its return value is handed to {@code onLoaded}
   * (typically to assign into a held config field). Call {@link #arm()}
   * before the first {@link #poll()} to capture the initial mtime baseline.
   */
  public GroovyFileWatcher(
      final Path path, final Supplier<T> loader, final Consumer<T> onLoaded) {
    this.path = path;
    this.loader = loader;
    this.onLoaded = onLoaded;
  }

  /**
   * Stat the watched path to capture the initial mtime baseline. Returns
   * whether the watcher is now armed. Failure cases (stat throws) are
   * logged at WARN and leave the watcher disarmed; subsequent
   * {@link #poll()} calls become no-ops.
   */
  public boolean arm() {
    try {
      lastModified = Files.getLastModifiedTime(path);
      armed = true;
      if (log.isInfoEnabled()) {
        log.info("Watching {} for hot-reload", path);
      }
      return true;
    } catch (final IOException e) {
      if (log.isWarnEnabled()) {
        log.warn("Could not stat {} to enable live reload: {}", path, e.toString());
      }
      return false;
    }
  }

  /**
   * Whether the watcher has captured an initial mtime baseline. A disarmed
   * watcher's {@link #poll()} short-circuits without stat overhead.
   */
  public boolean isArmed() {
    return armed;
  }

  /**
   * Stat the watched file; if mtime changed, invoke the loader and pass
   * the result to the consumer. Stat failures log at DEBUG and skip the
   * tick. Loader exceptions are caught and logged at WARN so the watcher
   * stays armed for the next tick.
   */
  public void poll() {
    if (!armed) {
      return;
    }
    final FileTime current;
    try {
      current = Files.getLastModifiedTime(path);
    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("stat() on {} failed: {}", path, e.toString());
      }
      return;
    }
    if (current.equals(lastModified)) {
      return;
    }
    lastModified = current;
    try {
      final T reloaded = loader.get();
      onLoaded.accept(reloaded);
    } catch (final RuntimeException e) {
      if (log.isWarnEnabled()) {
        log.warn("Reload of {} failed: {}", path, e.toString());
      }
    }
  }
}
