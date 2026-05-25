// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class FieldClampTest {

  private static final double EPS = 1e-9;

  private static ScalarField single(final double v) {
    return new ScalarField() {
      @Override
      public int width() {
        return 1;
      }

      @Override
      public int height() {
        return 1;
      }

      @Override
      public double valueAt(final int x, final int y) {
        return v;
      }
    };
  }

  @Test
  public void clampsAboveMax() {
    assertEquals(5.0, FieldClamp.of(single(42.0), 0.0, 5.0).valueAt(0, 0), EPS);
  }

  @Test
  public void clampsBelowMin() {
    assertEquals(0.0, FieldClamp.of(single(-3.0), 0.0, 5.0).valueAt(0, 0), EPS);
  }

  @Test
  public void passesThroughInRange() {
    assertEquals(2.5, FieldClamp.of(single(2.5), 0.0, 5.0).valueAt(0, 0), EPS);
  }

  @Test
  public void infinityPassesThrough() {
    // The unreachable/undefined signal must survive clamping, not collapse to max.
    assertEquals(
        Double.POSITIVE_INFINITY,
        FieldClamp.of(single(Double.POSITIVE_INFINITY), 0.0, 5.0).valueAt(0, 0),
        EPS);
  }

  @Test
  public void invertedRangeRejected() {
    assertThrows(IllegalArgumentException.class, () -> FieldClamp.of(single(1.0), 5.0, 0.0));
  }
}
