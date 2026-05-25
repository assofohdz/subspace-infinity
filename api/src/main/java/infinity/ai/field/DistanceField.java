// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/** Dijkstra distance from a goal cell over passable tiles; the navigation specialization. See ADR-0011. */
public interface DistanceField extends ScalarField {

  /** All-unreachable zero-size field — the non-blocking default while a real field builds (ADR-0011). */
  DistanceField EMPTY = EmptyDistanceField.INSTANCE;

  // Goal exposed as cell coords (tile space) rather than the server-side TileId,
  // keeping the api/ field layer free of moss world types; the server maps TileId<->cell.
  int goalX();

  int goalY();
}
