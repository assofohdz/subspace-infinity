// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.mathd.Vec3d;
import infinity.map.LevelFile;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;

/** Stateless helpers + pure data containers for {@link MapSystem}. */
public final class MapSystemLogic {

  private MapSystemLogic() {
    // utility class
  }

  /**
   * Center of the arena tile that contains {@code (currentxCoord, currentzCoord)}.
   * Pure math — no {@link MapSystem} state read — so lives here. {@code MapSystem}
   * has a thin delegating wrapper for backward-compat with {@code mapSystem.getCenterOfArena(...)}
   * call sites.
   */
  public static Vec3d getCenterOfArena(
      final double currentxCoord, final double currentzCoord, final int mapSize) {
    final double xArenaCoord = Math.floor(currentxCoord / mapSize);
    final double zArenaCoord = Math.floor(currentzCoord / mapSize);
    final int half = mapSize / 2;
    final double centerOfArenaX = currentxCoord < 0 ? xArenaCoord - half : xArenaCoord + half;
    final double centerOfArenaZ = currentzCoord < 0 ? zArenaCoord - half : zArenaCoord + half;
    return new Vec3d(centerOfArenaX, 1, centerOfArenaZ);
  }

  /** Format the first 40 entries of {@code idHistogram} into a single log line. */
  public static String formatTileIdHistogram(final SortedMap<Integer, Integer> idHistogram) {
    final StringBuilder sb = new StringBuilder("  tile-id histogram:");
    int shown = 0;
    for (final Map.Entry<Integer, Integer> e : idHistogram.entrySet()) {
      sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
      shown++;
      if (shown >= 40) {
        sb.append(" ...(").append(idHistogram.size() - shown).append(" more)");
        break;
      }
    }
    return sb.toString();
  }

  /** Returns true if {@code (inner, outer)} is the start of a wall run on the given axis. */
  public static boolean isRunStart(
      final boolean[][] wall, final boolean horizontal, final int outer, final int inner) {
    if (horizontal) {
      return wall[inner][outer] && (inner == 0 || !wall[inner - 1][outer]);
    }
    return wall[outer][inner] && (inner == 0 || !wall[outer][inner - 1]);
  }

  /** Measure how many contiguous wall cells extend from {@code inner} along the given axis. */
  public static int measureWallRun(
      final boolean[][] wall, final boolean horizontal,
      final int outer, final int inner, final int innerLimit) {
    int len = 0;
    while (inner + len < innerLimit
        && (horizontal ? wall[inner + len][outer] : wall[outer][inner + len])) {
      len++;
    }
    return len;
  }

  /**
   * Final summary line for {@code verifyCleared} — warn-level when any
   * lingering cells, info-level when fully cleared. Logger passed in so
   * messages keep the host class's logger name.
   */
  public static void logVerifyClearedSummary(
      final Logger log, final int lingering, final int total) {
    if (lingering > 0) {
      if (log.isWarnEnabled()) {
        log.warn("Post-clear verify: " + lingering + " / " + total + " cells still non-zero");
      }
    } else {
      if (log.isInfoEnabled()) {
        log.info("Post-clear verify: all " + total + " cells are zero");
      }
    }
  }

  /**
   * Diagnostic summary that follows a map build: entity counts, leaf failures,
   * histogram. Pulled out of {@link MapSystem} so the build pipeline's class CC
   * stays under PMD's class threshold; logger passed so messages keep the host
   * class's logger name.
   */
  public static void logMapBuildSummary(
      final Logger log, final LevelFile map, final Vec3d arenaOffset, final MapBuildStats stats) {
    if (log.isInfoEnabled()) {
      log.info(
          "createBlocksFromLegacyMap: map={} offset={} nonZero={} (visibleCells={} invisibleCells={} leafFailures={})",
          map.getMapName(),
          arenaOffset,
          stats.totalNonZero,
          stats.cellsVisible,
          stats.cellsInvisible,
          stats.cellsFailedLeaf);
      log.info(
          "  entities: turfFlags={} asteroidsSmall={} asteroidsMedium={} over5={} doors={} wormholes={}",
          stats.turfFlags,
          stats.asteroidsSmall,
          stats.asteroidsMedium,
          stats.over5,
          stats.doors,
          stats.wormholes);
      if (stats.firstWritten != null) {
        log.info("  first-written cell: {}    last-written cell: {}",
            stats.firstWritten, stats.lastWritten);
      }
    }
    if (stats.cellsFailedLeaf > 0 && log.isWarnEnabled()) {
      log.warn(
          "  {}/{} cells silently dropped by setWorldCell (leaf==null). "
              + "Usually means the arena offset targets a world region whose leaves are not paged in.",
          stats.cellsFailedLeaf,
          stats.cellsVisible + stats.cellsInvisible + stats.cellsFailedLeaf);
    }
    if (log.isInfoEnabled()) {
      log.info(formatTileIdHistogram(stats.idHistogram));
    }
  }

  /**
   * Mutable accumulator for {@code LegacyMapProjector.project} disposition
   * counters + diagnostic state. Package-private mutable fields because the
   * build pipeline writes them per-tile from inside the projector.
   */
  public static final class MapBuildStats {
    public int totalNonZero;
    public int turfFlags;
    public int asteroidsSmall;
    public int asteroidsMedium;
    public int over5;
    public int doors;
    public int wormholes;
    public int cellsVisible;
    public int cellsInvisible;
    public int cellsFailedLeaf;
    public final SortedMap<Integer, Integer> idHistogram = new TreeMap<>();
    public Vec3d firstWritten;
    public Vec3d lastWritten;
  }

  /**
   * Spiral placement direction enum used by {@code MapSystem.calculateNextOffset}
   * to walk the auto-placement grid (E → N → W → S → E). Moved out of MapSystem
   * so the inner-class methods don't bloat the host's class CC sum.
   */
  public enum Direction {
    E(1, 0) {
      @Override
      public Direction next() {
        return N;
      }
    },
    N(0, 1) {
      @Override
      public Direction next() {
        return W;
      }
    },
    W(-1, 0) {
      @Override
      public Direction next() {
        return S;
      }
    },
    S(0, -1) {
      @Override
      public Direction next() {
        return E;
      }
    };

    private final int dx;
    private final int dz;

    Direction(final int dx, final int dz) {
      this.dx = dx;
      this.dz = dz;
    }

    public Vec3d advance(final Vec3d point) {
      return new Vec3d(point.x + dx, 0, point.z + dz);
    }

    public abstract Direction next();
  }
}
