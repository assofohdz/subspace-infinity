// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.density;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import infinity.ai.field.ScalarField;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ArenaDensityTest {

  private static final double EPS = 1e-9;

  private static ArenaDensity arena() {
    return new ArenaDensity(20, 20, 100, 100, 4);
  }

  private static int[] cell(final int x, final int z) {
    return new int[] {x, z};
  }

  @Test
  public void rebuildSplatsPerFreqAndTracksFreqs() {
    final ArenaDensity d = arena();
    d.rebuild(Map.of(1, List.of(cell(110, 110)), 2, List.of(cell(105, 105))));
    assertEquals(Set.of(1, 2), d.freqs());
    assertEquals(1.0, d.teamDensity(1).valueAt(10, 10), EPS);
    assertEquals(1.0, d.teamDensity(2).valueAt(5, 5), EPS);
    assertEquals("freq 1's field is empty where freq 2 sits", 0.0, d.teamDensity(1).valueAt(5, 5), EPS);
  }

  @Test
  public void unknownFreqReturnsZeroField() {
    final ArenaDensity d = arena();
    d.rebuild(Map.of(1, List.of(cell(110, 110))));
    final ScalarField none = d.teamDensity(99);
    assertEquals(0.0, none.valueAt(10, 10), EPS);
    assertFalse(d.freqs().contains(99));
  }

  @Test
  public void rebuildDropsFreqsNoLongerPresent() {
    final ArenaDensity d = arena();
    d.rebuild(Map.of(1, List.of(cell(110, 110)), 2, List.of(cell(105, 105))));
    d.rebuild(Map.of(1, List.of(cell(110, 110)))); // freq 2 left the arena
    assertEquals(Set.of(1), d.freqs());
    assertEquals("dropped freq reads zero", 0.0, d.teamDensity(2).valueAt(5, 5), EPS);
  }

  @Test
  public void rebuildClearsStaleAccumulation() {
    final ArenaDensity d = arena();
    d.rebuild(Map.of(1, List.of(cell(110, 110))));
    d.rebuild(Map.of(1, List.of(cell(105, 105)))); // same freq, ship moved
    assertEquals("old cell cleared", 0.0, d.teamDensity(1).valueAt(10, 10), EPS);
    assertTrue("new cell populated", d.teamDensity(1).valueAt(5, 5) > 0.0);
  }
}
