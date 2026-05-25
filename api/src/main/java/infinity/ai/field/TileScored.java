// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/** A scored tile cell (arena-relative); e.g. a chokepoint ranked by its pinch ratio. See ADR-0012. */
public record TileScored(int x, int y, double score) {}
