// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.threat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ArenaThreatTest {

  private static final double EPS = 1e-9;

  private static ArenaThreat arena() {
    final boolean[][] open = new boolean[21][21];
    for (final boolean[] row : open) {
      Arrays.fill(row, true);
    }
    return new ArenaThreat(21, 21, 0, 0, 5, open);
  }

  private static int[] cell(final int x, final int z) {
    return new int[] {x, z};
  }

  @Test
  public void perFreqThreatAndFreqTracking() {
    final ArenaThreat t = arena();
    t.rebuild(Map.of(1, List.of(cell(10, 10)), 2, List.of(cell(4, 4))));
    assertEquals(Set.of(1, 2), t.freqs());
    assertEquals(1.0, t.threat(1).valueAt(10, 10), EPS);
    assertTrue(t.threat(2).valueAt(4, 4) > 0.0);
    assertEquals("freq 1 poses no threat where only freq 2 sits", 0.0, t.threat(1).valueAt(4, 4), EPS);
  }

  @Test
  public void unknownFreqIsSafe() {
    final ArenaThreat t = arena();
    t.rebuild(Map.of(1, List.of(cell(10, 10))));
    assertEquals(0.0, t.threat(99).valueAt(10, 10), EPS);
  }

  @Test
  public void rebuildDropsDepartedFreq() {
    final ArenaThreat t = arena();
    t.rebuild(Map.of(1, List.of(cell(10, 10)), 2, List.of(cell(4, 4))));
    t.rebuild(Map.of(1, List.of(cell(10, 10))));
    assertEquals(Set.of(1), t.freqs());
    assertEquals(0.0, t.threat(2).valueAt(4, 4), EPS);
  }
}
