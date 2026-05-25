// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/** A per-tile-grid float surface; coordinates are tile cells. See ADR-0012. */
public interface ScalarField {

  int width();

  int height();

  /** Value at cell (x,y); semantics field-specific. {@code POSITIVE_INFINITY} = unreachable / undefined. */
  double valueAt(int x, int y);
}
