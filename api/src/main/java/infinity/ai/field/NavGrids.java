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
   * Hull-aware routing grid (ADR-0011): a cell {@code (x,z)} is navigable iff the {@code size×size}
   * footprint anchored there is fully open — so an agent of that footprint (the diameter-2 ship =
   * {@code size 2}) can occupy it. Out-of-bounds counts as wall. This is Minkowski erosion: 2-wide
   * corridors stay usable (the 2×2 fits) but 1-wide slots and diagonal corner-pinches the hull can't
   * enter are removed, so the flow field never plans a point-route the ship can't follow. Build the
   * Dijkstra field on this grid; keep the raw grid for physical {@code passableAt}/line-of-sight.
   */
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
