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

  // --- hull-pinch detection (bot-ai-v3 B8/B11 regression fix) -------------------------

  /** Cell (x,z) with all neighbours open — hull-2 fits, cell is hull-navigable. */
  @Test
  public void hullNavigableOpenCellAllNeighboursOpen() {
    final boolean[][] g = grid(3);
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertTrue("open cell with open surroundings stays navigable", h[1][1]);
  }

  /** Vertical 1-cell-tall corridor: N+S walls → hull doesn't fit through. */
  @Test
  public void hullNotNavigableVerticalPinch() {
    final boolean[][] g = grid(3);
    g[0][1] = false; // N wall at (1, 0)
    g[2][1] = false; // S wall at (1, 2)
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertFalse("N+S pinch blocks hull at (1,1)", h[1][1]);
  }

  /** Horizontal 1-cell-wide corridor: W+E walls → hull doesn't fit through. */
  @Test
  public void hullNotNavigableHorizontalPinch() {
    final boolean[][] g = grid(3);
    g[1][0] = false; // W wall at (0, 1)
    g[1][2] = false; // E wall at (2, 1)
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertFalse("W+E pinch blocks hull at (1,1)", h[1][1]);
  }

  /** NW+SE diagonal walls: hull body extends into both corners. */
  @Test
  public void hullNotNavigableDiagonalPinchNwSe() {
    final boolean[][] g = grid(3);
    g[0][0] = false; // NW wall at (0, 0)
    g[2][2] = false; // SE wall at (2, 2)
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertFalse("NW+SE diagonal pinch blocks hull at (1,1)", h[1][1]);
  }

  /** NE+SW diagonal walls: matches the user-named pattern (x=3,y=1 + x=1,y=3). */
  @Test
  public void hullNotNavigableDiagonalPinchNeSw() {
    final boolean[][] g = grid(3);
    g[0][2] = false; // NE wall at (2, 0)
    g[2][0] = false; // SW wall at (0, 2)
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertFalse("NE+SW diagonal pinch blocks hull at (1,1)", h[1][1]);
  }

  /** A single wall on one side is fine — hull can still fit. */
  @Test
  public void hullNavigableWithSingleAdjacentWall() {
    final boolean[][] g = grid(3);
    g[0][1] = false; // N wall only
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertTrue("single N wall leaves hull fit (open S/W/E)", h[1][1]);
  }

  /** Walls themselves are never navigable. */
  @Test
  public void hullNotNavigableForWallCells() {
    final boolean[][] g = grid(3);
    g[1][1] = false;
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertFalse("wall cell stays non-navigable", h[1][1]);
  }

  /**
   * Out-of-bounds is NOT treated as a wall for pinch detection — map edges aren't physical
   * obstacles and the game handles bounds separately. So map-corner cells with only OOB
   * neighbours stay navigable; only in-bounds wall pairs trigger the pinch.
   */
  @Test
  public void hullNavigableAtCornerOfMapWhenOnlyOobNeighbours() {
    final boolean[][] g = grid(3);
    final boolean[][] h = NavGrids.hullNavigable(g);
    assertTrue("map corner stays passable when only OOB borders it", h[0][0]);
    assertTrue("map edge stays passable when only OOB borders it", h[0][1]);
  }
}
