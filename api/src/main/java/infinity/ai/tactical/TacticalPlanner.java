// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import javax.annotation.Nullable;

/**
 * Picks one {@link TacticalGoal} for a bot from its {@link ArchetypeConfig} weights and current
 * blackboard state. Pure w.r.t. the blackboard — the caller (BotBrainSystem) owns the per-bot
 * cadence timer and writes the result via {@link Blackboard#setCurrentGoal}. See ADR-0013.
 */
public interface TacticalPlanner {

  /**
   * The winning goal (max {@code intrinsicScore × derivedWeight}, with additive stickiness over
   * {@link Blackboard#currentGoal()}), or {@code null} when no behaviour offered a candidate.
   */
  @Nullable
  TacticalGoal select(Blackboard bb, ArchetypeConfig archetype);
}
