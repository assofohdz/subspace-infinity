// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.World;
import infinity.InfinityConstants;
import infinity.map.MapTypes;
import infinity.sim.CoreViewConstants;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wall-run light-emitter generator. Scans a decoded .lvl tile grid for straight wall runs of
 * at least {@link CoreViewConstants#WALL_LIGHT_MIN_RUN} tiles (horizontal or vertical) and
 * writes light-emitter cells ({@link InfinityConstants#LIGHT_EMITTER_BLOCK_TYPE}) above them.
 *
 * <p>The emitter cells carry packed light values on their {@code BlockType} that
 * {@code LightUtils.recalculateLighting} flood-fills into neighbour cells' {@code lightData},
 * which the tile shader reads via vertex colors. A "wall" is any tile id in the standard
 * solid range ({@code vieNormalStart..vieNormalEnd}); branches off the run are tolerated —
 * only the straight axis is measured.
 *
 * <p>Extracted from {@code MapSystem} to keep the host system focused on map lifecycle
 * (load/unload/swap) while the visual-decoration strategy lives here as a standalone,
 * independently testable strategy. This is the single canonical writer for
 * {@link InfinityConstants#LIGHT_EMITTER_BLOCK_TYPE} cells (per
 * <a href="../../../../../.claude/rules/replacement-as-mutation.md">RaM</a>).
 */
public final class WallLightDecorator {

  private static final Logger log = LoggerFactory.getLogger(WallLightDecorator.class);

  private WallLightDecorator() {
    // utility class — pure-function wall-run decoration; state-free
  }

  /**
   * Decorate a decoded tile grid with light-emitter cells along long straight wall runs.
   * Mutates {@code world} (writes {@link InfinityConstants#LIGHT_EMITTER_BLOCK_TYPE}) and
   * {@code coordinates} (records every emitter location so the corresponding unload
   * pass can clear them).
   *
   * @param tiles        decoded {@code [x][z]} tile-id grid (square; reads are flipped to
   *                     match {@link LegacyMapProjector#project}'s coordinate frame)
   * @param arenaOffset  world-space offset to add to each cell location
   * @param world        target {@link World} (cell-write target)
   * @param coordinates  set populated with every world-cell location written by this pass
   */
  public static void decorate(
      final short[][] tiles,
      final Vec3d arenaOffset,
      final World world,
      final Set<Vec3d> coordinates) {
    final int sx = tiles.length;
    final int sz = tiles[0].length;
    final boolean[][] wall = buildWallMask(tiles, sx, sz);
    final int wallTiles = countTrue(wall);

    final int minRun = CoreViewConstants.WALL_LIGHT_MIN_RUN;
    final int spacing = Math.max(1, CoreViewConstants.WALL_LIGHT_SPACING);
    final int lightY = (int) Math.round(CoreViewConstants.WALL_LIGHT_PLANE_Y);

    final int[] horizontalCounters = scanWallRunsAxis(
        wall, sx, sz, true, minRun, spacing, lightY, arenaOffset, world, coordinates);
    final int horizontalLights = horizontalCounters[0];
    final int longestHorizontal = horizontalCounters[1];

    final int[] verticalCounters = scanWallRunsAxis(
        wall, sx, sz, false, minRun, spacing, lightY, arenaOffset, world, coordinates);
    final int verticalLights = verticalCounters[0];
    final int longestVertical = verticalCounters[1];

    if (log.isInfoEnabled()) {
      log.info("Wall-run light emitters: wallTiles={} horiz={} vert={} (min run {}); "
              + "longest horiz={} longest vert={} at offset={}",
          wallTiles, horizontalLights, verticalLights, minRun,
          longestHorizontal, longestVertical, arenaOffset);
    }
  }

  private static boolean[][] buildWallMask(final short[][] tiles, final int sx, final int sz) {
    final boolean[][] wall = new boolean[sx][sz];
    final int extentX = sx - 1;
    final int extentZ = sz - 1;
    for (int x = 0; x < sx; x++) {
      for (int z = 0; z < sz; z++) {
        final short s = tiles[extentX - x][extentZ - z];
        wall[x][z] = s >= MapTypes.vieNormalStart && s <= MapTypes.vieNormalEnd;
      }
    }
    return wall;
  }

  private static int countTrue(final boolean[][] wall) {
    int count = 0;
    for (final boolean[] row : wall) {
      for (final boolean cell : row) {
        if (cell) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Scan one axis of {@code wall} for runs of contiguous {@code true} cells and emit a
   * light-emitter block above each run that meets {@code minRun}. Lights are spaced
   * approximately {@code spacing} tiles apart along the run.
   *
   * @param horizontal when {@code true}, scan rows (constant z, varying x); when
   *                   {@code false}, scan columns (constant x, varying z)
   * @return a 2-element array {@code [lightCount, longestRunLength]}
   */
  private static int[] scanWallRunsAxis(
      final boolean[][] wall, final int sx, final int sz, final boolean horizontal,
      final int minRun, final int spacing, final int lightY,
      final Vec3d arenaOffset, final World world, final Set<Vec3d> coordinates) {
    // Mutable accumulator: [0] = longestRun, [1] = lightsEmitted. Mapped to the documented
    // {lights, longestRun} return order at exit.
    final int[] runStats = {0, 0};
    final int outerLimit = horizontal ? sz : sx;
    final int innerLimit = horizontal ? sx : sz;
    for (int outer = 0; outer < outerLimit; outer++) {
      int inner = 0;
      while (inner < innerLimit) {
        inner = processRunAt(wall, horizontal, outer, inner, innerLimit,
            minRun, spacing, lightY, arenaOffset, world, coordinates, runStats);
      }
    }
    return new int[] {runStats[1], runStats[0]};
  }

  /**
   * Inner step of {@link #scanWallRunsAxis}: if a wall run starts at {@code (outer, inner)},
   * measure it, update {@code runStats}, optionally emit lights, and return the next
   * {@code inner} position past the run. If no run starts here, just advance {@code inner}
   * by one.
   *
   * @param runStats {@code [0]} = longest-run-length so far (mutated);
   *                 {@code [1]} = lights-emitted so far (mutated)
   */
  private static int processRunAt(
      final boolean[][] wall, final boolean horizontal,
      final int outer, final int inner, final int innerLimit,
      final int minRun, final int spacing, final int lightY,
      final Vec3d arenaOffset, final World world, final Set<Vec3d> coordinates,
      final int[] runStats) {
    if (!MapSystemLogic.isRunStart(wall, horizontal, outer, inner)) {
      return inner + 1;
    }
    final int len = MapSystemLogic.measureWallRun(wall, horizontal, outer, inner, innerLimit);
    if (len > runStats[0]) {
      runStats[0] = len;
    }
    if (len >= minRun) {
      runStats[1] += emitLightsAlongRun(
          horizontal, outer, inner, len, spacing, lightY, arenaOffset, world, coordinates);
    }
    return inner + Math.max(len, 1);
  }

  /** Emit light-emitter cells along the run; returns how many lights were placed. */
  private static int emitLightsAlongRun(
      final boolean horizontal, final int outer, final int inner, final int len,
      final int spacing, final int lightY,
      final Vec3d arenaOffset, final World world, final Set<Vec3d> coordinates) {
    final int count = Math.max(1, (int) Math.round((double) len / spacing));
    for (int i = 0; i < count; i++) {
      final int along = inner + (int) Math.floor((i + 0.5) * len / count);
      final Vec3d pos = horizontal
          ? new Vec3d(along, lightY, outer).add(arenaOffset)
          : new Vec3d(outer, lightY, along).add(arenaOffset);
      world.setWorldCell(pos, InfinityConstants.LIGHT_EMITTER_BLOCK_TYPE);
      coordinates.add(pos);
    }
    return count;
  }
}
