// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

import infinity.math.Vec2d;

/** Per-cell unit direction toward lower scalar value; {@link Vec2d#ZERO} at minima / undefined cells. See ADR-0011. */
public interface GradientField {

  Vec2d directionAt(int x, int y);
}
