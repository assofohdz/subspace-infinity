// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import infinity.config.TeamSpawn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.slf4j.Logger;

/**
 * Stateless helpers extracted from {@link ArenaSystem} to keep the host
 * system's class-level cyclomatic-complexity sum below PMD's class threshold.
 *
 * <p>Only pure helpers (math, string normalisation, mtime polling) live here —
 * the registry, EntitySets, chat-command handlers, and the lifecycle reconcile
 * loop all stay in {@link ArenaSystem} so arena state is owned by one system.
 *
 * <p>Logger is passed explicitly to the helpers that emit log messages so the
 * output keeps the host class's logger name.
 */
public final class ArenaLogic {

  private ArenaLogic() {
    // utility class
  }

  /**
   * Uniform-disc sample around a team's spawn centre. Returns the exact centre
   * when {@link TeamSpawn#radiusTiles()} is {@code 0} (point spawn) so authors
   * get deterministic behaviour without needing to seed an RNG.
   */
  public static double[] sampleTeamSpawn(final TeamSpawn team) {
    final int radius = team.radiusTiles();
    if (radius <= 0) {
      return new double[] {team.x(), team.y()};
    }
    // sqrt(rand) gives a uniform area distribution over the disc;
    // omitting the sqrt would cluster samples toward the centre.
    final double r = radius * Math.sqrt(Math.random());
    final double theta = Math.random() * 2.0 * Math.PI;
    return new double[] {team.x() + r * Math.cos(theta), team.y() + r * Math.sin(theta)};
  }

  /** Drop a trailing path separator if present. */
  public static String stripTrailingSlash(final String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  /**
   * Stat one watched file; if its mtime changed, run the reload callback. The
   * callback is contained — a {@link RuntimeException} from {@code w.onChanged}
   * is logged at {@code warn} via the supplied logger but doesn't propagate.
   */
  public static void pollSingleWatch(final Logger log, final WatchedFile w) {
    final FileTime mtime;
    try {
      mtime = Files.getLastModifiedTime(w.onDisk);
    } catch (final java.io.IOException e) {
      log.debug("Stat failed for {} (arena {}); skipping reload tick", w.onDisk, w.arenaName);
      return;
    }
    if (mtime.equals(w.lastModified)) {
      return;
    }
    w.lastModified = mtime;
    try {
      w.onChanged.run();
    } catch (final RuntimeException e) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Reload of {} for arena {} failed: {}",
            w.classpathPath, w.arenaName, e.toString());
      }
    }
  }

  /**
   * One Groovy file the watcher is tracking on disk. The {@link #onChanged}
   * callback is the per-file reload action. Moved out of {@link ArenaSystem}
   * so the inner-class fields don't bloat the host's class CC sum.
   */
  public static final class WatchedFile {
    public final String arenaName;
    public final String classpathPath;
    public final Path onDisk;
    public FileTime lastModified;
    public final Runnable onChanged;

    public WatchedFile(
        final String arenaName,
        final String classpathPath,
        final Path onDisk,
        final FileTime lastModified,
        final Runnable onChanged) {
      this.arenaName = arenaName;
      this.classpathPath = classpathPath;
      this.onDisk = onDisk;
      this.lastModified = lastModified;
      this.onChanged = onChanged;
    }
  }
}
