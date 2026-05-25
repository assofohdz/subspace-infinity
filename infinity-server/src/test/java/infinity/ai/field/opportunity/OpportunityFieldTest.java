// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.opportunity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class OpportunityFieldTest {

  private static final double EPS = 1e-9;

  /** 20x20, origin (50,50), radius 4. */
  private static OpportunityField field() {
    return new OpportunityField(20, 20, 50, 50, 4);
  }

  private static int[] cell(final int x, final int z) {
    return new int[] {x, z};
  }

  @Test
  public void peaksAtPrizeFallsOff() {
    final OpportunityField f = field();
    f.rebuild(List.of(cell(60, 60))); // world (60,60) -> relative (10,10)
    assertEquals(1.0, f.valueAt(10, 10), EPS);
    assertTrue(f.valueAt(11, 10) > 0.0);
    assertTrue(f.valueAt(11, 10) < 1.0);
    assertEquals("beyond radius is empty", 0.0, f.valueAt(15, 10), EPS);
  }

  @Test
  public void rebuildReplacesPriorPrizes() {
    final OpportunityField f = field();
    f.rebuild(List.of(cell(60, 60)));
    f.rebuild(List.of(cell(55, 55))); // prize moved / consumed + respawned
    assertEquals("old prize cleared", 0.0, f.valueAt(10, 10), EPS);
    assertEquals("new prize present", 1.0, f.valueAt(5, 5), EPS);
  }

  @Test
  public void multiplePrizesAccumulate() {
    final OpportunityField f = field();
    f.rebuild(List.of(cell(60, 60), cell(61, 60)));
    assertTrue("overlapping prizes raise the value", f.valueAt(60 - 50, 60 - 50) > 1.0);
  }

  @Test
  public void emptyRebuildClears() {
    final OpportunityField f = field();
    f.rebuild(List.of(cell(60, 60)));
    f.rebuild(List.of());
    assertEquals(0.0, f.valueAt(10, 10), EPS);
  }

  @Test
  public void outOfBoundsReadsZero() {
    final OpportunityField f = field();
    assertEquals(0.0, f.valueAt(-1, 0), EPS);
    assertEquals(0.0, f.valueAt(50, 50), EPS);
  }
}
