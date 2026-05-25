// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import java.util.List;

/**
 * A named, reusable goal producer + intrinsic scorer. {@link #name()} is the kebab-case key
 * operators write in the synergy table (ADR-0014) and per-arena overlays; the planner multiplies
 * {@link #intrinsicScore} by the bot's derived weight for that name. See ADR-0013.
 */
public interface Behaviour {

  /** Stable kebab-case name; matches the {@code synergy { }} entry and {@link ArchetypeConfig} key. */
  String name();

  /** Candidate goals from current world state; empty when this behaviour has nothing to offer. */
  List<TacticalGoal> enumerate(Blackboard bb);

  /** Intrinsic utility of {@code goal} in {@code [0,1]}; the derived weight is applied by the planner. */
  double intrinsicScore(TacticalGoal goal, Blackboard bb);
}
