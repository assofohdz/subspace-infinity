// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/**
 * Weighted sum of {@link ScalarField}s — {@code Σ weightᵢ · sourceᵢ.valueAt(x,y)} — used to compose
 * "navigate to G but avoid threats / seek opportunity" surfaces (ADR-0012). Dimensions span the
 * largest source; out-of-range cells read each source's own {@code +∞} per the field convention.
 *
 * <p>Non-finite propagates: if any source is undefined ({@code +∞}, e.g. an unreachable
 * {@link DistanceField} cell) the blended value is {@code +∞} — undefined anywhere is undefined
 * here, so {@link FieldGradient} yields {@code ZERO} and steering won't route through it. Consumers
 * that want a reachable goal nudged by a finite field should blend <em>gradients</em> per the
 * ADR-0012 composite example, not scalars, so a still-building field degrades to no-nudge instead
 * of poisoning the whole surface.
 */
public final class FieldBlend implements ScalarField {

  private final Weighted[] sources;
  private final int width;
  private final int height;

  private FieldBlend(final Weighted[] sources) {
    this.sources = sources;
    int w = 0;
    int h = 0;
    for (final Weighted s : sources) {
      w = Math.max(w, s.field().width());
      h = Math.max(h, s.field().height());
    }
    this.width = w;
    this.height = h;
  }

  public static FieldBlend of(final Weighted... sources) {
    if (sources.length == 0) {
      throw new IllegalArgumentException("FieldBlend needs at least one source");
    }
    return new FieldBlend(sources.clone());
  }

  @Override
  public int width() {
    return this.width;
  }

  @Override
  public int height() {
    return this.height;
  }

  @Override
  public double valueAt(final int x, final int y) {
    double sum = 0.0;
    for (final Weighted s : this.sources) {
      final double v = s.field().valueAt(x, y);
      if (!Double.isFinite(v)) {
        return Double.POSITIVE_INFINITY;
      }
      sum += s.weight() * v;
    }
    return sum;
  }
}
