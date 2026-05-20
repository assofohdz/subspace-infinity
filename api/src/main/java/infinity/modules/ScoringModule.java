// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/**
 * Layered category. Per-tick contribution hook is {@link #tickContributions} —
 * event-driven scoring modules (e.g. {@code KillPointsScoring} on
 * {@code PlayerKilledEvent}) inherit the default no-op; tick-driven scoring
 * (e.g. {@code FlagHoldTimeScoring}) overrides it to emit per-tick
 * {@code *ScoreChange} intents.
 */
public interface ScoringModule extends ArenaModule {

  /** Default no-op — event-driven scoring modules ignore the tick. */
  default void tickContributions(final ArenaId arenaId, final SimTime time) {
    // intentionally empty
  }
}
