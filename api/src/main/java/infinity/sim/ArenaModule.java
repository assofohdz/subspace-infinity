// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import infinity.es.arena.ArenaId;
import infinity.modules.MatchOutcome;
import infinity.modules.RoundOutcome;

/** Lifecycle interface for arena modules per ADR-0008. Metadata lives on {@code ModuleDescriptor}. */
public interface ArenaModule {

  default void onArenaLoad(final ArenaId arenaId) {}

  default void onMatchStart(final ArenaId arenaId) {}

  default void onRoundStart(final ArenaId arenaId, final int roundNumber) {}

  default void onRoundEnd(
      final ArenaId arenaId, final int roundNumber, final RoundOutcome outcome) {}

  default void onMatchEnd(final ArenaId arenaId, final MatchOutcome outcome) {}

  default void onArenaUnload(final ArenaId arenaId) {}
}
