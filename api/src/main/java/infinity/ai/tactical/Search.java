// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

/** Idle drift / patrol when no target is in view — the always-eligible fallback goal. See ADR-0013. */
public record Search() implements TacticalGoal {}
