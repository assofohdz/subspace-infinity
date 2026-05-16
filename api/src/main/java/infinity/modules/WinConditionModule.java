// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;
import java.util.Optional;

/**
 * Two-role interface per ADR-0008:
 * <ul>
 *   <li><b>Terminator (optional):</b> {@link #checkTermination} returns a reason
 *       string when this winCondition wants to end the round. Default no-op —
 *       decider-only impls (e.g. {@code HighestScoreWinCondition}) ignore.
 *   <li><b>Decider (required):</b> {@link #declareWinner} returns the winning
 *       freq, or {@link WinnerDeclaration#UNDECIDED} to abstain. Dispatcher
 *       aggregates votes first-non-UNDECIDED-wins per ADR-0008.
 * </ul>
 */
public interface WinConditionModule extends ArenaModule {

  default Optional<String> checkTermination(final ArenaId arenaId) {
    return Optional.empty();
  }

  WinnerDeclaration declareWinner(ArenaId arenaId);
}
