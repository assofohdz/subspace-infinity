// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import infinity.math.Vec2d;

/**
 * Descent {@link GradientField} over any {@link ScalarField}: steers toward the lower-distance
 * <em>passable</em> neighbours (8-connected, no corner-cutting), weighted by how much lower each is.
 * Unlike a central difference, it can never point into a wall — walls are simply skipped — and at a
 * closest-approach cell (no neighbour is lower) it returns {@link Vec2d#ZERO} (stop) instead of
 * pressing into the obstacle. See ADR-0011.
 */
public final class FieldGradient implements GradientField {

  private static final double EPS = 1e-9;
  private static final double DIAGONAL = Math.sqrt(2.0);
  private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
  private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

  private final ScalarField source;

  public FieldGradient(final ScalarField source) {
    this.source = source;
  }

  @Override
  public Vec2d directionAt(final int x, final int y) {
    final double center = this.source.valueAt(x, y);
    if (!Double.isFinite(center)) {
      return Vec2d.ZERO;
    }
    double ax = 0.0;
    double az = 0.0;
    for (int i = 0; i < DX.length; i++) {
      final int dx = DX[i];
      final int dy = DY[i];
      if (!stepAllowed(x, y, dx, dy)) {
        continue;
      }
      final double descent = center - this.source.valueAt(x + dx, y + dy);
      if (descent <= 0.0) {
        continue; // neighbour is not closer to the goal
      }
      final double len = dx != 0 && dy != 0 ? DIAGONAL : 1.0;
      ax += dx / len * descent;
      az += dy / len * descent;
    }
    final Vec2d acc = new Vec2d(ax, az);
    return acc.lengthSq() < EPS ? Vec2d.ZERO : acc.normalize();
  }

  /** Neighbour is reachable (in-bounds, passable) and, for diagonals, doesn't cut a wall corner. */
  private boolean stepAllowed(final int x, final int y, final int dx, final int dy) {
    if (!finiteAt(x + dx, y + dy)) {
      return false; // wall or out of bounds
    }
    return dx == 0 || dy == 0 || (finiteAt(x + dx, y) && finiteAt(x, y + dy));
  }

  private boolean finiteAt(final int x, final int y) {
    return x >= 0
        && y >= 0
        && x < this.source.width()
        && y < this.source.height()
        && Double.isFinite(this.source.valueAt(x, y));
  }
}
