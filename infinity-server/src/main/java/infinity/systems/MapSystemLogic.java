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
        log.warn("Post-clear verify: {} / {} cells still non-zero", lingering, total);
      }
    } else {
      if (log.isInfoEnabled()) {
        log.info("Post-clear verify: all {} cells are zero", total);
      }
    }
  }

  /**
   * Diagnostic summary that follows a map build: entity counts, leaf failures,
   * histogram. Pulled out of {@link MapSystem} so the build pipeline's class CC
   * stays under PMD's class threshold; logger passed so messages keep the host
   * class's logger name.
   */
  @SuppressWarnings("PMD.GuardLogStatement") // already guarded by outer if (log.isInfoEnabled()) at L98
  public static void logMapBuildSummary(
      final Logger log, final LevelFile map, final Vec3d arenaOffset, final MapBuildStats stats) {
    if (log.isInfoEnabled()) {
      log.info(
          "createBlocksFromLegacyMap: map={} offset={} nonZero={} (visibleCells={} invisibleCells={} leafFailures={})",
          map.getMapName(),
          arenaOffset,
          stats.getTotalNonZero(),
          stats.getCellsVisible(),
          stats.getCellsInvisible(),
          stats.getCellsFailedLeaf());
      log.info(
          "  entities: turfFlags={} asteroidsSmall={} asteroidsMedium={} over5={} doors={} wormholes={}",
          stats.getTurfFlags(),
          stats.getAsteroidsSmall(),
          stats.getAsteroidsMedium(),
          stats.getOver5(),
          stats.getDoors(),
          stats.getWormholes());
      if (stats.getFirstWritten() != null) {
        log.info("  first-written cell: {}    last-written cell: {}",
            stats.getFirstWritten(), stats.getLastWritten());
      }
    }
    if (stats.getCellsFailedLeaf() > 0 && log.isWarnEnabled()) {
      log.warn(
          "  {}/{} cells silently dropped by setWorldCell (leaf==null). "
              + "Usually means the arena offset targets a world region whose leaves are not paged in.",
          stats.getCellsFailedLeaf(),
          stats.getCellsVisible() + stats.getCellsInvisible() + stats.getCellsFailedLeaf());
    }
    if (log.isInfoEnabled()) {
      log.info(formatTileIdHistogram(stats.getIdHistogram()));
    }
  }

  /**
   * Mutable accumulator for {@code LegacyMapProjector.project} disposition
   * counters + diagnostic state. Counters mutate via {@code increment*}
   * helpers; first/last-written cells via setters. Read via getters.
   */
  public static final class MapBuildStats {
    private int totalNonZero;
    private int turfFlags;
    private int asteroidsSmall;
    private int asteroidsMedium;
    private int over5;
    private int doors;
    private int wormholes;
    private int cellsVisible;
    private int cellsInvisible;
    private int cellsFailedLeaf;
    private final SortedMap<Integer, Integer> idHistogram = new TreeMap<>();
    private Vec3d firstWritten;
    private Vec3d lastWritten;

    public int getTotalNonZero() {
      return totalNonZero;
    }

    public void incrementTotalNonZero() {
      totalNonZero++;
    }

    public int getTurfFlags() {
      return turfFlags;
    }

    public void incrementTurfFlags() {
      turfFlags++;
    }

    public int getAsteroidsSmall() {
      return asteroidsSmall;
    }

    public void incrementAsteroidsSmall() {
      asteroidsSmall++;
    }

    public int getAsteroidsMedium() {
      return asteroidsMedium;
    }

    public void incrementAsteroidsMedium() {
      asteroidsMedium++;
    }

    public int getOver5() {
      return over5;
    }

    public void incrementOver5() {
      over5++;
    }

    public int getDoors() {
      return doors;
    }

    public void incrementDoors() {
      doors++;
    }

    public int getWormholes() {
      return wormholes;
    }

    public void incrementWormholes() {
      wormholes++;
    }

    public int getCellsVisible() {
      return cellsVisible;
    }

    public void incrementCellsVisible() {
      cellsVisible++;
    }

    public int getCellsInvisible() {
      return cellsInvisible;
    }

    public void incrementCellsInvisible() {
      cellsInvisible++;
    }

    public int getCellsFailedLeaf() {
      return cellsFailedLeaf;
    }

    public void incrementCellsFailedLeaf() {
      cellsFailedLeaf++;
    }

    public SortedMap<Integer, Integer> getIdHistogram() {
      return idHistogram;
    }

    public Vec3d getFirstWritten() {
      return firstWritten;
    }

    public void setFirstWritten(final Vec3d firstWritten) {
      this.firstWritten = firstWritten;
    }

    public Vec3d getLastWritten() {
      return lastWritten;
    }

    public void setLastWritten(final Vec3d lastWritten) {
      this.lastWritten = lastWritten;
    }
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
