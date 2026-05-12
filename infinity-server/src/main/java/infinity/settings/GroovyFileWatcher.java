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

/** Single-file mtime poll watcher for Groovy hot-reload. Single-threaded by contract. */
public final class GroovyFileWatcher<T> {

  private static final Logger log = LoggerFactory.getLogger(GroovyFileWatcher.class);

  private final Path path;
  private final Supplier<T> loader;
  private final Consumer<T> onLoaded;
  private FileTime lastModified;
  private boolean armed;

  public GroovyFileWatcher(
      final Path path, final Supplier<T> loader, final Consumer<T> onLoaded) {
    this.path = path;
    this.loader = loader;
    this.onLoaded = onLoaded;
  }

  /** Captures the initial mtime; failure leaves the watcher disarmed (poll becomes no-op). */
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

  public boolean isArmed() {
    return armed;
  }

  /** Stat + load on mtime change; loader exceptions are caught so the watcher stays armed. */
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
