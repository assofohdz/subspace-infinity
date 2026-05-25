// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

/** What a bot is trying to accomplish this planner cycle; the BT dispatches on the exact record type. See ADR-0013. */
public sealed interface TacticalGoal permits Engage, Disengage, Search, NavigateToTile {}
