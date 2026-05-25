// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.threat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class ThreatFieldTest {

  private static final double EPS = 1e-9;

  private static boolean[][] open(final int n) {
    final boolean[][] g = new boolean[n][n];
    for (final boolean[] row : g) {
      Arrays.fill(row, true);
    }
    return g;
  }

  /** 21x21 open grid, origin (0,0), threat radius 5. */
  private static ThreatField field(final boolean[][] passable) {
    return new ThreatField(passable[0].length, passable.length, 0, 0, 5, passable);
  }

  @Test
  public void peaksAtShipFallsOffWithRange() {
    final ThreatField f = field(open(21));
    f.splatWorld(10, 10, 1.0);
    assertEquals("max danger at the ship cell", 1.0, f.valueAt(10, 10), EPS);
    assertTrue("nearer cell is more dangerous than farther", f.valueAt(12, 10) > f.valueAt(14, 10));
    assertEquals("beyond range is safe", 0.0, f.valueAt(16, 10), EPS);
  }

  @Test
  public void wallOcclusionZeroesThreatBehindCover() {
    final boolean[][] g = open(21);
    // Vertical wall column at x=12 spanning the rows around the ship/target line.
    for (int z = 5; z <= 15; z++) {
      g[z][12] = false;
    }
    final ThreatField f = field(g);
    f.splatWorld(10, 10, 1.0); // ship at (10,10), open side
    assertTrue("cell on the ship's side is threatened", f.valueAt(11, 10) > 0.0);
    assertEquals("cell directly behind the wall is safe", 0.0, f.valueAt(13, 10), EPS);
  }

  @Test
  public void multipleEnemiesAccumulate() {
    final ThreatField f = field(open(21));
    f.splatWorld(10, 10, 1.0);
    final double single = f.valueAt(11, 10);
    f.splatWorld(11, 10, 1.0); // a second ship adjacent
    assertTrue("two overlapping ships raise the danger", f.valueAt(11, 10) > single);
  }

  @Test
  public void clearResets() {
    final ThreatField f = field(open(21));
    f.splatWorld(10, 10, 1.0);
    f.clear();
    assertEquals(0.0, f.valueAt(10, 10), EPS);
  }

  @Test
  public void outOfBoundsReadsZero() {
    final ThreatField f = field(open(21));
    assertEquals(0.0, f.valueAt(-1, 0), EPS);
    assertEquals(0.0, f.valueAt(99, 99), EPS);
  }
}
