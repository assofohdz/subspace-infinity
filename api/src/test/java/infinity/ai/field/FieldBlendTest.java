// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FieldBlendTest {

  private static final double EPS = 1e-9;

  /** Constant-valued field of the given dimensions. */
  private static ScalarField constant(final int w, final int h, final double v) {
    return new ScalarField() {
      @Override
      public int width() {
        return w;
      }

      @Override
      public int height() {
        return h;
      }

      @Override
      public double valueAt(final int x, final int y) {
        return x >= 0 && y >= 0 && x < w && y < h ? v : Double.POSITIVE_INFINITY;
      }
    };
  }

  @Test
  public void weightedSum() {
    final ScalarField blend =
        FieldBlend.of(Weighted.of(constant(4, 4, 10.0), 1.0), Weighted.of(constant(4, 4, 2.0), -0.6));
    assertEquals(10.0 - 0.6 * 2.0, blend.valueAt(1, 1), EPS);
  }

  @Test
  public void dimensionsSpanLargestSource() {
    final ScalarField blend =
        FieldBlend.of(Weighted.of(constant(8, 3, 1.0), 1.0), Weighted.of(constant(2, 9, 1.0), 1.0));
    assertEquals(8, blend.width());
    assertEquals(9, blend.height());
  }

  @Test
  public void nonFinitePropagates() {
    // A reachable opportunity source must not rescue an unreachable nav cell.
    final ScalarField blend =
        FieldBlend.of(
            Weighted.of(constant(4, 4, Double.POSITIVE_INFINITY), 1.0),
            Weighted.of(constant(4, 4, 5.0), 1.0));
    assertFalse(Double.isFinite(blend.valueAt(1, 1)));
    assertEquals(Double.POSITIVE_INFINITY, blend.valueAt(1, 1), EPS);
  }

  @Test
  public void gradientOfBlendDescendsTheCombinedSurface() {
    // One x-ramp (value == x; descent points -x). Weighting a second copy at -1.0 cancels it
    // exactly (flat -> zero gradient); a weaker -0.5 leaves a net +0.5x ramp still descending -x.
    final ScalarField ramp = ramp();
    final var flat = new FieldGradient(FieldBlend.of(Weighted.of(ramp, 1.0), Weighted.of(ramp, -1.0)));
    assertTrue("equal-and-opposite weights cancel to flat", flat.directionAt(2, 2).length() < EPS);
    final var pulled =
        new FieldGradient(FieldBlend.of(Weighted.of(ramp, 1.0), Weighted.of(ramp, -0.5)));
    assertTrue("net +0.5x ramp still descends toward -x", pulled.directionAt(2, 2).x < 0.0);
  }

  @Test
  public void emptySourcesRejected() {
    assertThrows(IllegalArgumentException.class, FieldBlend::of);
  }

  private static ScalarField ramp() {
    final double[][] r = new double[5][5];
    for (int y = 0; y < 5; y++) {
      for (int x = 0; x < 5; x++) {
        r[y][x] = x;
      }
    }
    return array(r);
  }

  private static ScalarField array(final double[][] arr) {
    return new ScalarField() {
      @Override
      public int width() {
        return arr[0].length;
      }

      @Override
      public int height() {
        return arr.length;
      }

      @Override
      public double valueAt(final int x, final int y) {
        if (x < 0 || y < 0 || y >= arr.length || x >= arr[0].length) {
          return Double.POSITIVE_INFINITY;
        }
        return arr[y][x];
      }
    };
  }
}
