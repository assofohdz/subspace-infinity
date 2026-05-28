// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/**
 * Stateless helpers over a {@code passable[z][x]} grid (ADR-0011/0012): a Bresenham line-of-sight
 * raycast and a nearest-passable-cell search. LoS is the per-leaf raycast ADR-0012 calls for instead
 * of a {@code LineOfSightOracle} service; nearest-passable snaps a goal off a wall so a flow field
 * still builds when a quantized goal cell lands on solid tile.
 */
public final class NavGrids {

  private NavGrids() {}

  /** True if the straight line of cells from {@code (ax,az)} to {@code (bx,bz)} is all passable. */
  public static boolean lineOfSight(
      final boolean[][] passable, final int ax, final int az, final int bx, final int bz) {
    int x = ax;
    int z = az;
    final int dx = Math.abs(bx - ax);
    final int dz = Math.abs(bz - az);
    final int sx = ax < bx ? 1 : -1;
    final int sz = az < bz ? 1 : -1;
    int err = dx - dz;
    while (true) {
      if (!cell(passable, x, z)) {
        return false;
      }
      if (x == bx && z == bz) {
        return true;
      }
      final int e2 = 2 * err;
      if (e2 > -dz) {
        err -= dz;
        x += sx;
      }
      if (e2 < dx) {
        err += dx;
        z += sz;
      }
    }
  }

  /**
   * Cell {@code (x,z)} if passable; otherwise the nearest passable cell within Chebyshev radius
   * {@code maxRadius} (ring-by-ring scan), or {@code {x,z}} unchanged if none is found. Returns a
   * 2-element {@code [x,z]}.
   */
  public static int[] nearestPassable(
      final boolean[][] passable, final int x, final int z, final int maxRadius) {
    if (cell(passable, x, z)) {
      return new int[] {x, z};
    }
    for (int r = 1; r <= maxRadius; r++) {
      final int[] found = scanRing(passable, x, z, r);
      if (found != null) {
        return found;
      }
    }
    return new int[] {x, z};
  }

  /** First passable cell on the Chebyshev ring of radius {@code r} around {@code (x,z)}, or null. */
  private static int[] scanRing(final boolean[][] passable, final int x, final int z, final int r) {
    for (int dz = -r; dz <= r; dz++) {
      for (int dx = -r; dx <= r; dx++) {
        final boolean onPerimeter = Math.abs(dx) == r || Math.abs(dz) == r;
        if (onPerimeter && cell(passable, x + dx, z + dz)) {
          return new int[] {x + dx, z + dz};
        }
      }
    }
    return null;
  }

  /** In-bounds + passable at cell {@code (x,z)}; out-of-bounds counts as wall. */
  public static boolean passable(final boolean[][] grid, final int x, final int z) {
    return cell(grid, x, z);
  }

  /**
   * Per-cell clearance grid (multi-source BFS from walls + out-of-bounds): each navigable cell
   * stores {@code min(maxClearance, Chebyshev distance to nearest wall)}; wall cells store 0.
   * Used by {@code DijkstraDistanceField} as a soft-clearance cost penalty so flow fields prefer
   * centre-of-corridor without forbidding wall-adjacent cells (the diameter-2 hull can still
   * hug a wall when it has to). Replaces the hard {@link #erodeFootprint} erosion — that
   * over-blanked actually-flyable cells around walls and around multi-cell obstacle clusters
   * (the 2×2 medium asteroid). See bot-ai-v3 BACKLOG B8 + B11.
   */
  public static int[][] clearanceField(final boolean[][] passable, final int maxClearance) {
    final int h = passable.length;
    final int w = h == 0 ? 0 : passable[0].length;
    final int[][] out = new int[h][w];
    final java.util.Deque<int[]> queue = new java.util.ArrayDeque<>();
    seedFromWalls(passable, out, queue, w, h);
    while (!queue.isEmpty()) {
      relaxNeighbours(out, queue, maxClearance, w, h);
    }
    capUnreached(out, maxClearance, w, h);
    return out;
  }

  /** Seed BFS frontier with all wall cells (distance 0); mark navigable cells as -1 (unvisited). */
  private static void seedFromWalls(
      final boolean[][] passable,
      final int[][] out,
      final java.util.Deque<int[]> queue,
      final int w,
      final int h) {
    for (int z = 0; z < h; z++) {
      for (int x = 0; x < w; x++) {
        if (passable[z][x]) {
          out[z][x] = -1;
        } else {
          out[z][x] = 0;
          queue.add(new int[] {x, z});
        }
      }
    }
  }

  private static final int[] BFS_DX = {1, -1, 0, 0, 1, 1, -1, -1};
  private static final int[] BFS_DZ = {0, 0, 1, -1, 1, -1, 1, -1};

  /** Pop one frontier cell, expand to its 8 in-bounds unvisited neighbours, capped at maxClearance. */
  private static void relaxNeighbours(
      final int[][] out,
      final java.util.Deque<int[]> queue,
      final int maxClearance,
      final int w,
      final int h) {
    final int[] cell = queue.poll();
    final int cx = cell[0];
    final int cz = cell[1];
    final int next = out[cz][cx] + 1;
    if (next > maxClearance) {
      return;
    }
    for (int i = 0; i < BFS_DX.length; i++) {
      tryRelax(out, queue, cx + BFS_DX[i], cz + BFS_DZ[i], next, w, h);
    }
  }

  /** Stamp distance + enqueue if {@code (nx,nz)} is in-bounds and unvisited. */
  private static void tryRelax(
      final int[][] out,
      final java.util.Deque<int[]> queue,
      final int nx,
      final int nz,
      final int next,
      final int w,
      final int h) {
    if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
      return;
    }
    if (out[nz][nx] == -1) {
      out[nz][nx] = next;
      queue.add(new int[] {nx, nz});
    }
  }

  /** Unreached interior (no wall within {@code maxClearance}) → cap at {@code maxClearance}. */
  private static void capUnreached(final int[][] out, final int maxClearance, final int w, final int h) {
    for (int z = 0; z < h; z++) {
      for (int x = 0; x < w; x++) {
        if (out[z][x] == -1) {
          out[z][x] = maxClearance;
        }
      }
    }
  }

  /**
   * Hull-pinch detection — returns a copy of {@code passable} with cells the hull-2 ship can't
   * physically occupy marked impassable. Four 3×3 pinch patterns block a cell (centred at the
   * pattern's middle):
   * <ul>
   *   <li>N+S walls (vertical 1-cell-tall corridor — hull is 2 tall, doesn't fit).</li>
   *   <li>W+E walls (horizontal 1-cell-wide corridor — hull is 2 wide, doesn't fit).</li>
   *   <li>NW+SE diagonal walls (hull body extends diagonally into both walls).</li>
   *   <li>NE+SW diagonal walls (tight diagonal slot).</li>
   * </ul>
   * Out-of-bounds cells do NOT count as walls for pinch purposes (map edges are not actual
   * blockers); the broader OOB-as-wall handling stays in {@link #passable} for LoS / Dijkstra.
   * Used as the routing grid in {@code ArenaSpatialFields}; the raw grid still drives LoS +
   * wall-repulsion. See bot-ai-v3 B8 follow-up.
   */
  public static boolean[][] hullNavigable(final boolean[][] passable) {
    final int h = passable.length;
    final int w = h == 0 ? 0 : passable[0].length;
    final boolean[][] out = new boolean[h][w];
    for (int z = 0; z < h; z++) {
      for (int x = 0; x < w; x++) {
        out[z][x] = passable[z][x] && !pinched(passable, x, z, w, h);
      }
    }
    return out;
  }

  /** True if (x,z) is open but pinched between walls in any of the four hull-blocking patterns. */
  private static boolean pinched(
      final boolean[][] passable, final int x, final int z, final int w, final int h) {
    final boolean n = wallInBounds(passable, x, z - 1, w, h);
    final boolean s = wallInBounds(passable, x, z + 1, w, h);
    final boolean ww = wallInBounds(passable, x - 1, z, w, h);
    final boolean ee = wallInBounds(passable, x + 1, z, w, h);
    final boolean nw = wallInBounds(passable, x - 1, z - 1, w, h);
    final boolean ne = wallInBounds(passable, x + 1, z - 1, w, h);
    final boolean sw = wallInBounds(passable, x - 1, z + 1, w, h);
    final boolean se = wallInBounds(passable, x + 1, z + 1, w, h);
    return (n && s) || (ww && ee) || (nw && se) || (ne && sw);
  }

  /** In-bounds + impassable; OOB returns {@code false} (treated as not-a-wall for pinch logic). */
  private static boolean wallInBounds(
      final boolean[][] passable, final int x, final int z, final int w, final int h) {
    return z >= 0 && z < h && x >= 0 && x < w && !passable[z][x];
  }

  /**
   * @deprecated Hard footprint erosion over-blanks actually-flyable cells near walls and around
   *     multi-cell obstacle clusters. Migrate to {@link #clearanceField} + a cost-weighted
   *     {@code DijkstraDistanceField}, plus {@link #hullNavigable} to mask cells the hull can't
   *     physically fit. See bot-ai-v3 BACKLOG B8.
   */
  @Deprecated
  public static boolean[][] erodeFootprint(final boolean[][] passable, final int size) {
    final int h = passable.length;
    final int w = h == 0 ? 0 : passable[0].length;
    final boolean[][] out = new boolean[h][w];
    for (int z = 0; z < h; z++) {
      for (int x = 0; x < w; x++) {
        out[z][x] = footprintOpen(passable, x, z, size);
      }
    }
    return out;
  }

  /** Whether the {@code size×size} block anchored at {@code (x,z)} (toward +x/+z) is all passable. */
  private static boolean footprintOpen(
      final boolean[][] passable, final int x, final int z, final int size) {
    for (int dz = 0; dz < size; dz++) {
      for (int dx = 0; dx < size; dx++) {
        if (!cell(passable, x + dx, z + dz)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean cell(final boolean[][] passable, final int x, final int z) {
    return z >= 0 && z < passable.length && x >= 0 && x < passable[0].length && passable[z][x];
  }
}
