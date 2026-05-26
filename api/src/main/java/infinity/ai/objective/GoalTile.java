// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

/**
 * A single objective goal location in <em>cell space</em> (1 world unit = 1 nav cell), as absolute
 * world cells. Deliberately not a moss {@code com.simsilica.mworld.TileId}: an arena is one TileId
 * (1024×1024), so a TileId can't address a cell within it. Matches the cell-space choice already made
 * by {@code DistanceField} / {@code NavigateToTile}, keeping the api/ objective layer free of moss
 * world types. The nav layer subtracts the arena origin to index its arena-relative field grids.
 */
public record GoalTile(int worldCellX, int worldCellZ) {}
