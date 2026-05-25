// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.chokepoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import infinity.ai.field.TileScored;
import java.util.List;
import org.junit.Test;

public class ChokepointAnalyzerTest {

  /** Two open rooms (left + right) joined by a 1-cell-wide horizontal corridor at z=10. */
  private static boolean[][] dumbbell() {
    final boolean[][] g = new boolean[21][21];
    // left room x[2..8], right room x[12..18], both z[5..15]; corridor only at z=10, x[9..11].
    for (int z = 5; z <= 15; z++) {
      for (int x = 2; x <= 8; x++) {
        g[z][x] = true;
      }
      for (int x = 12; x <= 18; x++) {
        g[z][x] = true;
      }
    }
    g[10][9] = true;
    g[10][10] = true;
    g[10][11] = true;
    return g;
  }

  @Test
  public void detectsTheCorridorPinch() {
    final List<TileScored> hot = new ChokepointAnalyzer(dumbbell(), 4).hottest(5);
    assertFalse("a 1-wide corridor between two rooms is a chokepoint", hot.isEmpty());
    // The hottest pinch should sit on the corridor row z=10, between the rooms (x in 9..11).
    final TileScored top = hot.get(0);
    assertEquals("chokepoint is on the corridor row", 10, top.y());
    assertTrue("chokepoint is in the corridor span", top.x() >= 9 && top.x() <= 11);
  }

  @Test
  public void openRoomHasNoChokepoint() {
    final boolean[][] g = new boolean[21][21];
    for (int z = 2; z <= 18; z++) {
      for (int x = 2; x <= 18; x++) {
        g[z][x] = true; // one big open room, no pinch
      }
    }
    assertTrue("a single open room has no narrow passage", new ChokepointAnalyzer(g, 4).hottest(5).isEmpty());
  }

  @Test
  public void dedupeAndTopNBound() {
    final List<TileScored> hot = new ChokepointAnalyzer(dumbbell(), 4).hottest(2);
    assertTrue("respects topN bound", hot.size() <= 2);
    // Corridor cells are adjacent — dedupe should collapse them to one representative.
    assertEquals("a single short corridor yields one deduped chokepoint", 1, hot.size());
  }

  @Test
  public void sortedByScoreDescending() {
    final List<TileScored> hot = new ChokepointAnalyzer(dumbbell(), 4).hottest(10);
    for (int i = 1; i < hot.size(); i++) {
      assertTrue("hottest first", hot.get(i - 1).score() >= hot.get(i).score());
    }
  }
}
