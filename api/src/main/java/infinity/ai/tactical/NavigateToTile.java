// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

/**
 * Travel to a world cell {@code (cellX, cellZ)} along the flow-field gradient (ADR-0011/0013).
 * Cell-precise (1 world unit = 1 nav cell), <em>not</em> a moss {@code TileId} — an arena is one
 * 1024-unit tile, so a TileId can't address a cell within it. {@code SteerToGoalTile} converts to
 * the arena-relative grid via the context origin. Produced by {@code FollowTrafficBehaviour}.
 */
public record NavigateToTile(int cellX, int cellZ) implements TacticalGoal {}
