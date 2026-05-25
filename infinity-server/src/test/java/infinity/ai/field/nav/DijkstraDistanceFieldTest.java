// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import infinity.ai.field.FieldGradient;
import infinity.math.Vec2d;
import org.junit.Test;

public class DijkstraDistanceFieldTest {

  private static final double EPS = 1e-6;
  private static final double DIAG = Math.sqrt(2.0);

  /** All-passable {@code [y][x]} grid. */
  private static boolean[][] open(final int w, final int h) {
    final boolean[][] g = new boolean[h][w];
    for (final boolean[] row : g) {
      java.util.Arrays.fill(row, true);
    }
    return g;
  }

  @Test
  public void openGridDistances() {
    final DijkstraDistanceField f = new DijkstraDistanceField(0, 0, open(5, 5));
    assertEquals(0.0, f.valueAt(0, 0), EPS);
    assertEquals(1.0, f.valueAt(1, 0), EPS);
    assertEquals(1.0, f.valueAt(0, 1), EPS);
    assertEquals(DIAG, f.valueAt(1, 1), EPS);
    assertEquals(2.0, f.valueAt(2, 0), EPS);
  }

  @Test
  public void wallMakesRegionUnreachable() {
    // Full impassable column at x == 2 isolates x >= 3 from the goal at (0,0).
    final boolean[][] g = open(5, 5);
    for (int y = 0; y < 5; y++) {
      g[y][2] = false;
    }
    final DijkstraDistanceField f = new DijkstraDistanceField(0, 0, g);
    assertTrue(Double.isInfinite(f.valueAt(4, 0)));
    assertTrue(Double.isInfinite(f.valueAt(3, 2)));
    assertEquals(1.0, f.valueAt(1, 0), EPS); // near side still reachable
  }

  @Test
  public void noCornerCutting() {
    // Wall at (1,0); the cheap diagonal (0,0)->(1,1) must be rejected, forcing the
    // around path via (0,1) at cost 2.0 instead of the corner-cut sqrt(2).
    final boolean[][] g = open(3, 3);
    g[0][1] = false; // cell (x=1, y=0)
    final DijkstraDistanceField f = new DijkstraDistanceField(0, 0, g);
    assertTrue(Double.isInfinite(f.valueAt(1, 0)));
    assertEquals(2.0, f.valueAt(1, 1), EPS);
  }

  @Test
  public void impassableGoalYieldsNoField() {
    final boolean[][] g = new boolean[4][4]; // all false
    final DijkstraDistanceField f = new DijkstraDistanceField(1, 1, g);
    assertTrue(Double.isInfinite(f.valueAt(1, 1)));
    assertTrue(Double.isInfinite(f.valueAt(0, 0)));
  }

  @Test
  public void outOfBoundsIsInfinite() {
    final DijkstraDistanceField f = new DijkstraDistanceField(0, 0, open(3, 3));
    assertTrue(Double.isInfinite(f.valueAt(-1, 0)));
    assertTrue(Double.isInfinite(f.valueAt(3, 3)));
  }

  @Test
  public void gradientPointsTowardGoal() {
    final DijkstraDistanceField f = new DijkstraDistanceField(0, 0, open(5, 5));
    final Vec2d dir = new FieldGradient(f).directionAt(3, 3);
    assertTrue("x should head toward goal (-x)", dir.x < 0);
    assertTrue("y should head toward goal (-y)", dir.y < 0);
    assertEquals(1.0, dir.length(), EPS);
  }
}
