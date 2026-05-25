// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import infinity.math.Vec2d;

/** Derives a descent {@link GradientField} from any {@link ScalarField} via infinity-safe central differences. See ADR-0011. */
public final class FieldGradient implements GradientField {

  private static final double EPS = 1e-9;

  private final ScalarField source;

  public FieldGradient(final ScalarField source) {
    this.source = source;
  }

  @Override
  public Vec2d directionAt(final int x, final int y) {
    final double center = this.source.valueAt(x, y);
    if (!Double.isFinite(center)) {
      return Vec2d.ZERO;
    }
    // Ascent gradient via central differences; infinite/out-of-bounds neighbours
    // fall back to the centre value so the gradient never points into a wall.
    final double gx = (sample(x + 1, y, center) - sample(x - 1, y, center)) * 0.5;
    final double gy = (sample(x, y + 1, center) - sample(x, y - 1, center)) * 0.5;
    final Vec2d ascent = new Vec2d(gx, gy);
    if (ascent.lengthSq() < EPS) {
      return Vec2d.ZERO;
    }
    // Descent: toward lower value (toward the goal for a DistanceField).
    return ascent.normalize().mult(-1.0);
  }

  private double sample(final int x, final int y, final double fallback) {
    if (x < 0 || y < 0 || x >= this.source.width() || y >= this.source.height()) {
      return fallback;
    }
    final double v = this.source.valueAt(x, y);
    return Double.isFinite(v) ? v : fallback;
  }
}
