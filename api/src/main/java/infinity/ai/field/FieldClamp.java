// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/**
 * Clamps a source {@link ScalarField}'s finite values into {@code [min, max]} (ADR-0012) — caps a
 * runaway accumulation (e.g. a threat spike from a deathball) so one source can't dominate a
 * {@link FieldBlend}. Non-finite ({@code +∞}) passes through unchanged to preserve the
 * unreachable/undefined signal.
 */
public final class FieldClamp implements ScalarField {

  private final ScalarField source;
  private final double min;
  private final double max;

  private FieldClamp(final ScalarField source, final double min, final double max) {
    this.source = source;
    this.min = min;
    this.max = max;
  }

  public static FieldClamp of(final ScalarField source, final double min, final double max) {
    if (min > max) {
      throw new IllegalArgumentException("min " + min + " > max " + max);
    }
    return new FieldClamp(source, min, max);
  }

  @Override
  public int width() {
    return this.source.width();
  }

  @Override
  public int height() {
    return this.source.height();
  }

  @Override
  public double valueAt(final int x, final int y) {
    final double v = this.source.valueAt(x, y);
    if (!Double.isFinite(v)) {
      return v;
    }
    return Math.min(this.max, Math.max(this.min, v));
  }
}
