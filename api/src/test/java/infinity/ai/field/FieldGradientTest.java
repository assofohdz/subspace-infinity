// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import infinity.math.Vec2d;
import org.junit.Test;

public class FieldGradientTest {

  private static final double EPS = 1e-6;

  /** Array-backed field; {@code arr[y][x]}; out of bounds = unreachable. */
  private static ScalarField arrayField(final double[][] arr) {
    return new ScalarField() {
      @Override
      public int width() {
        return arr[0].length;
      }

      @Override
      public int height() {
        return arr.length;
      }

      @Override
      public double valueAt(final int x, final int y) {
        if (x < 0 || y < 0 || y >= arr.length || x >= arr[0].length) {
          return Double.POSITIVE_INFINITY;
        }
        return arr[y][x];
      }
    };
  }

  @Test
  public void pointsDownTheXRamp() {
    // value == x; lower x is "toward goal". Gradient (descent) points -x.
    final double[][] ramp = new double[5][5];
    for (int y = 0; y < 5; y++) {
      for (int x = 0; x < 5; x++) {
        ramp[y][x] = x;
      }
    }
    final Vec2d d = new FieldGradient(arrayField(ramp)).directionAt(2, 2);
    assertEquals(-1.0, d.x, EPS);
    assertEquals(0.0, d.y, EPS);
  }

  @Test
  public void pointsTowardDiagonalGoal() {
    // value == x + y; goal at (0,0). Descent points toward -x,-y.
    final double[][] f = new double[5][5];
    for (int y = 0; y < 5; y++) {
      for (int x = 0; x < 5; x++) {
        f[y][x] = x + y;
      }
    }
    final Vec2d d = new FieldGradient(arrayField(f)).directionAt(2, 2);
    final double inv = 1.0 / Math.sqrt(2.0);
    assertEquals(-inv, d.x, EPS);
    assertEquals(-inv, d.y, EPS);
    assertEquals(1.0, d.length(), EPS);
  }

  @Test
  public void flatFieldIsZero() {
    final double[][] flat = new double[3][3];
    assertEquals(Vec2d.ZERO, new FieldGradient(arrayField(flat)).directionAt(1, 1));
  }

  @Test
  public void unreachableCellIsZero() {
    final double[][] f = {{0, 0}, {0, 0}};
    f[0][0] = Double.POSITIVE_INFINITY;
    assertEquals(Vec2d.ZERO, new FieldGradient(arrayField(f)).directionAt(0, 0));
  }

  @Test
  public void doesNotCutCornerIntoDiagonalWall() {
    // Naive central difference at (1,1) points the descent up-right toward (2,0), but (2,0) is a wall:
    // the bot would wedge in the corner. It must collapse to the open orthogonal (right, toward the
    // lower-distance (2,1)) and route around — mirrors DijkstraDistanceField's no-corner-cutting.
    final double inf = Double.POSITIVE_INFINITY;
    final double[][] f = {
      {3, 2, inf},
      {3, 2, 1},
      {4, 4, 3}
    };
    final Vec2d d = new FieldGradient(arrayField(f)).directionAt(1, 1);
    assertEquals("route around the corner horizontally, not diagonally into the wall", 1.0, d.x, EPS);
    assertEquals(0.0, d.y, EPS);
  }

  @Test
  public void infiniteNeighbourStaysFinite() {
    // Wall neighbour must not produce NaN/Infinity in the gradient.
    final double[][] f = {
      {5, 5, 5},
      {Double.POSITIVE_INFINITY, 5, 1},
      {5, 5, 5}
    };
    final Vec2d d = new FieldGradient(arrayField(f)).directionAt(1, 1);
    assertTrue(Double.isFinite(d.x));
    assertTrue(Double.isFinite(d.y));
    assertTrue(d.length() <= 1.0 + EPS);
  }
}
