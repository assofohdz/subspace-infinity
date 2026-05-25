// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pins {@link MapSystemLogic#derivePassability} — the axis-flip must mirror {@link LegacyMapProjector}. */
public class MapSystemLogicPassabilityTest {

  @Test
  public void emptyGridYieldsAllPassable() {
    final boolean[][] p = MapSystemLogic.derivePassability(new short[3][3]);
    assertEquals(3, p.length);
    assertEquals(3, p[0].length);
    for (final boolean[] row : p) {
      for (final boolean cell : row) {
        assertTrue("zero tiles are passable", cell);
      }
    }
  }

  @Test
  public void wallTileMapsToFlippedWorldCell() {
    // 3x3; extentX=extentZ=2. Projector places a solid block at world cell (cx,cz) reading
    // tiles[2-cx][2-cz]. So tiles[0][0] -> world cell (2,2); tiles[2][1] -> world cell (0,1).
    final short[][] tiles = new short[3][3];
    tiles[0][0] = 1; // wall
    tiles[2][1] = 1; // wall

    final boolean[][] p = MapSystemLogic.derivePassability(tiles); // p[cz][cx]

    assertFalse("tiles[0][0] -> world (2,2)", p[2][2]);
    assertFalse("tiles[2][1] -> world (1,0)", p[1][0]);
    assertTrue("tiles[2][2]=0 -> world (0,0) passable", p[0][0]);
    assertTrue("an untouched interior cell stays passable", p[1][1]);
  }

  @Test
  public void emptyTilesArrayYieldsEmptyGrid() {
    assertEquals(0, MapSystemLogic.derivePassability(new short[0][]).length);
  }

  @Test
  public void erodeClearanceShrinksByRadius_bordersAndWalls() {
    final boolean[][] open = new boolean[5][5];
    for (final boolean[] row : open) {
      java.util.Arrays.fill(row, true);
    }
    final boolean[][] eroded = MapSystemLogic.erodeClearance(open, 1);
    // Border cells erode (out-of-bounds counts as wall); only the interior 3x3 survives.
    assertFalse("corner erodes", eroded[0][0]);
    assertFalse("edge erodes", eroded[0][2]);
    assertTrue("interior centre clears", eroded[2][2]);
    assertTrue("interior cell clears", eroded[1][1]);
  }

  @Test
  public void erodeClearanceWallBlocksNeighbours() {
    final boolean[][] open = new boolean[7][7];
    for (final boolean[] row : open) {
      java.util.Arrays.fill(row, true);
    }
    open[3][3] = false; // a wall
    final boolean[][] eroded = MapSystemLogic.erodeClearance(open, 1);
    assertFalse("wall cell impassable", eroded[3][3]);
    assertFalse("cell adjacent to the wall erodes", eroded[2][3]);
    assertFalse("diagonal-adjacent erodes too (Chebyshev)", eroded[2][2]);
    assertTrue("two cells from the wall clears", eroded[1][3]);
  }

  @Test
  public void erodeClearanceZeroRadiusIsIdentity() {
    final boolean[][] grid = {{true, false}, {false, true}};
    assertSame(grid, MapSystemLogic.erodeClearance(grid, 0));
  }
}
