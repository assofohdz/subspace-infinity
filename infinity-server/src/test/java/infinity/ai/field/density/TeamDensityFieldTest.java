// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.density;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TeamDensityFieldTest {

  private static final double EPS = 1e-9;

  /** 20x20 grid, world origin (100,100), kernel radius 4. */
  private static TeamDensityField field() {
    return new TeamDensityField(20, 20, 100, 100, 4);
  }

  @Test
  public void splatPeaksAtCenterAndFallsOff() {
    final TeamDensityField f = field();
    f.splatWorld(110, 110, 1.0); // world (110,110) -> relative (10,10)
    final double center = f.valueAt(10, 10);
    assertEquals("linear kernel peaks at 1.0 at the ship cell", 1.0, center, EPS);
    assertTrue("one cell out is lower", f.valueAt(11, 10) < center);
    assertTrue("one cell out is positive", f.valueAt(11, 10) > 0.0);
  }

  @Test
  public void zeroBeyondKernelRadius() {
    final TeamDensityField f = field();
    f.splatWorld(110, 110, 1.0);
    assertEquals("5 cells away (> radius 4) is untouched", 0.0, f.valueAt(15, 10), EPS);
  }

  @Test
  public void originRelativeIndexing() {
    final TeamDensityField f = field();
    f.splatWorld(100, 100, 1.0); // world == origin -> relative (0,0)
    assertEquals(1.0, f.valueAt(0, 0), EPS);
  }

  @Test
  public void outOfBoundsReadsZeroNotInfinity() {
    final TeamDensityField f = field();
    assertEquals(0.0, f.valueAt(-1, 5), EPS);
    assertEquals(0.0, f.valueAt(100, 100), EPS);
  }

  @Test
  public void accumulatesMultipleShips() {
    final TeamDensityField f = field();
    f.splatWorld(110, 110, 1.0);
    final double one = f.valueAt(10, 10);
    f.splatWorld(110, 110, 1.0);
    assertEquals("second ship at the same cell doubles the peak", 2.0 * one, f.valueAt(10, 10), EPS);
  }

  @Test
  public void clearResetsTheGrid() {
    final TeamDensityField f = field();
    f.splatWorld(110, 110, 1.0);
    f.clear();
    assertEquals(0.0, f.valueAt(10, 10), EPS);
  }

  @Test
  public void splatNearEdgeStaysInBounds() {
    final TeamDensityField f = field();
    f.splatWorld(101, 101, 1.0); // relative (1,1); kernel reaches negative cells — must not throw
    assertEquals(1.0, f.valueAt(1, 1), EPS);
    assertTrue(f.valueAt(0, 0) > 0.0);
  }
}
