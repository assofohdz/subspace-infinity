// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/** A per-tile-grid float surface; coordinates are tile cells. See ADR-0012. */
public interface ScalarField {

  /** Zero-size, all-zero field — the non-blocking default for an accumulating field before it exists. */
  ScalarField EMPTY =
      new ScalarField() {
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
          return 0.0;
        }
      };

  int width();

  int height();

  /** Value at cell (x,y); semantics field-specific. {@code POSITIVE_INFINITY} = unreachable / undefined. */
  double valueAt(int x, int y);
}
