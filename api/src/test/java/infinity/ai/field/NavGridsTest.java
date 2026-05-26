// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavGridsTest {

  /** {@code [z][x]} grid; {@code true} = passable. */
  private static boolean[][] grid(final int n) {
    final boolean[][] g = new boolean[n][n];
    for (final boolean[] row : g) {
      java.util.Arrays.fill(row, true);
    }
    return g;
  }

  @Test
  public void lineOfSightClearOnOpenGrid() {
    assertTrue(NavGrids.lineOfSight(grid(10), 0, 0, 9, 9));
    assertTrue(NavGrids.lineOfSight(grid(10), 2, 7, 8, 1));
  }

  @Test
  public void lineOfSightBlockedByWall() {
    final boolean[][] g = grid(10);
    g[5][5] = false; // wall on the diagonal between (0,0) and (9,9)
    assertFalse(NavGrids.lineOfSight(g, 0, 0, 9, 9));
  }

  @Test
  public void lineOfSightOutOfBoundsIsBlocked() {
    assertFalse(NavGrids.lineOfSight(grid(5), 2, 2, 7, 2)); // (7,2) off-grid = wall
  }

  @Test
  public void nearestPassableReturnsSelfWhenOpen() {
    assertArrayEquals(new int[] {3, 4}, NavGrids.nearestPassable(grid(8), 3, 4, 4));
  }

  @Test
  public void nearestPassableSnapsOffAWall() {
    final boolean[][] g = grid(8);
    g[4][4] = false; // wall at the requested cell
    final int[] snapped = NavGrids.nearestPassable(g, 4, 4, 4);
    assertTrue("snapped to an adjacent passable cell", NavGrids.passable(g, snapped[0], snapped[1]));
    assertTrue("within ring 1", Math.abs(snapped[0] - 4) <= 1 && Math.abs(snapped[1] - 4) <= 1);
  }

  @Test
  public void erodeFootprintKeepsTwoWideCorridor() {
    // cols 1,2 open; cols 0,3 walls — a diameter-2 (2x2) hull fills and traverses the 2-wide corridor.
    final boolean[][] g = new boolean[4][4];
    for (int z = 0; z < 4; z++) {
      g[z][1] = true;
      g[z][2] = true;
    }
    final boolean[][] e = NavGrids.erodeFootprint(g, 2);
    assertTrue("2-wide corridor anchor navigable", e[0][1]);
    assertTrue(e[1][1]);
  }

  @Test
  public void erodeFootprintRemovesOneWideSlot() {
    // only col 1 open — a 2x2 hull cannot fit anywhere, so the whole slot erodes out.
    final boolean[][] g = new boolean[4][4];
    for (int z = 0; z < 4; z++) {
      g[z][1] = true;
    }
    final boolean[][] e = NavGrids.erodeFootprint(g, 2);
    for (int z = 0; z < 4; z++) {
      for (int x = 0; x < 4; x++) {
        assertFalse("1-wide slot eroded out", e[z][x]);
      }
    }
  }

  @Test
  public void erodeFootprintBlocksDiagonalPinch() {
    // Open 3x3 with diagonal-corner walls (top-right + bottom-left): the 2x2 spanning the pinch hits a
    // wall, so the hull can't squeeze the corner — exactly the trench flag-slot case.
    final boolean[][] g = grid(3);
    g[0][2] = false; // wall at (x=2,z=0)
    g[2][0] = false; // wall at (x=0,z=2)
    final boolean[][] e = NavGrids.erodeFootprint(g, 2);
    assertFalse("2x2 through the pinch is blocked", e[0][1]);
    assertTrue("clear 2x2 corner stays navigable", e[0][0]);
  }

  @Test
  public void passableBoundsCheck() {
    assertTrue(NavGrids.passable(grid(4), 0, 0));
    assertFalse(NavGrids.passable(grid(4), -1, 0));
    assertFalse(NavGrids.passable(grid(4), 4, 0));
  }
}
