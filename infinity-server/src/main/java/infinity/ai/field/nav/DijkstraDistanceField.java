// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.nav;

import infinity.ai.field.DistanceField;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Single-source Dijkstra distance field from a goal cell over a passability grid, 8-connected
 * with octile costs and no-corner-cutting (a diagonal step is rejected unless both shared
 * orthogonal neighbours are passable). Built eagerly at construction. The grid is indexed
 * {@code passable[y][x]}; one tile = one world unit. See ADR-0011.
 */
public final class DijkstraDistanceField implements DistanceField {

  private static final double DIAGONAL = Math.sqrt(2.0);
  private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
  private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

  private final int width;
  private final int height;
  private final int goalX;
  private final int goalY;
  private final boolean[][] passable;
  private final double[] dist;

  /**
   * @param passable row-major {@code [y][x]} traversability grid — pass a footprint-eroded grid
   *     ({@code NavGrids.erodeFootprint}) for hull-aware routing.
   */
  public DijkstraDistanceField(final int goalX, final int goalY, final boolean[][] passable) {
    this.height = passable.length;
    this.width = this.height == 0 ? 0 : passable[0].length;
    this.goalX = goalX;
    this.goalY = goalY;
    this.passable = passable;
    this.dist = new double[this.width * this.height];
    java.util.Arrays.fill(this.dist, Double.POSITIVE_INFINITY);
    if (passable(goalX, goalY)) {
      compute(goalX, goalY);
    }
  }

  private void compute(final int gx, final int gy) {
    final Queue<long[]> open = new PriorityQueue<>((a, b) -> Double.compare(bits(a), bits(b)));
    this.dist[index(gx, gy)] = 0.0;
    open.add(pack(gx, gy, 0.0));
    while (!open.isEmpty()) {
      final long[] node = open.poll();
      final int x = (int) node[0];
      final int y = (int) node[1];
      final double d = bits(node);
      if (d > this.dist[index(x, y)]) {
        continue; // stale heap entry
      }
      for (int i = 0; i < DX.length; i++) {
        relax(x, y, DX[i], DY[i], d, open);
      }
    }
  }

  private void relax(
      final int x, final int y, final int dx, final int dy, final double d, final Queue<long[]> open) {
    final int nx = x + dx;
    final int ny = y + dy;
    if (!passable(nx, ny)) {
      return;
    }
    final boolean diagonal = dx != 0 && dy != 0;
    if (diagonal && (!passable(x + dx, y) || !passable(x, y + dy))) {
      return; // no corner cutting
    }
    final double nd = d + (diagonal ? DIAGONAL : 1.0);
    final int ni = index(nx, ny);
    if (nd < this.dist[ni]) {
      this.dist[ni] = nd;
      open.add(pack(nx, ny, nd));
    }
  }

  @Override
  public double valueAt(final int x, final int y) {
    return inBounds(x, y) ? this.dist[index(x, y)] : Double.POSITIVE_INFINITY;
  }

  @Override
  public int width() {
    return this.width;
  }

  @Override
  public int height() {
    return this.height;
  }

  @Override
  public int goalX() {
    return this.goalX;
  }

  @Override
  public int goalY() {
    return this.goalY;
  }

  private boolean passable(final int x, final int y) {
    return inBounds(x, y) && this.passable[y][x];
  }

  private boolean inBounds(final int x, final int y) {
    return x >= 0 && y >= 0 && x < this.width && y < this.height;
  }

  private int index(final int x, final int y) {
    return y * this.width + x;
  }

  // Heap entry packs (x, y, distance) to keep allocation to one small array per push.
  private static long[] pack(final int x, final int y, final double d) {
    return new long[] {x, y, Double.doubleToRawLongBits(d)};
  }

  private static double bits(final long[] node) {
    return Double.longBitsToDouble(node[2]);
  }
}
