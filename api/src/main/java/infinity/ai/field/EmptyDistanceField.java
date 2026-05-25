// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/**
 * Zero-size, all-unreachable {@link DistanceField} — the non-blocking {@code getNow} default while a
 * real field builds asynchronously (ADR-0011). Every cell is {@code +∞}, so {@link FieldGradient}
 * yields {@link Vec2d#ZERO} everywhere and the steering layer falls through to reactive avoidance
 * with no crash. Use the {@link DistanceField#EMPTY} singleton.
 */
final class EmptyDistanceField implements DistanceField {

  static final EmptyDistanceField INSTANCE = new EmptyDistanceField();

  private EmptyDistanceField() {}

  @Override
  public int width() {
    return 0;
  }

  @Override
  public int height() {
    return 0;
  }

  @Override
  public double valueAt(final int x, final int y) {
    return Double.POSITIVE_INFINITY;
  }

  @Override
  public int goalX() {
    return -1;
  }

  @Override
  public int goalY() {
    return -1;
  }
}
