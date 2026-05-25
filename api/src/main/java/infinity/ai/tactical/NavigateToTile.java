// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import com.simsilica.mworld.TileId;

/**
 * Travel to {@code tile} along the flow-field gradient. Dormant until production navigation lands
 * (ADR-0011 slice #03): no baseline behaviour enumerates it, and its Execute branch falls through
 * when the bot has no {@link BotAiArenaContext}. See ADR-0013.
 */
public record NavigateToTile(TileId tile) implements TacticalGoal {}
