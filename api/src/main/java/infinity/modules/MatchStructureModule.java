// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/**
 * Single-pick match-structure category. Dispatcher asks {@link #shouldMatchEnd}
 * at each round-end; if {@code true}, fires {@code onMatchEnd} on every module
 * and starts a new match. Default {@code false} preserves the {@code continuous}
 * shape — rounds keep iterating forever within match 1.
 */
public interface MatchStructureModule extends ArenaModule {

  /** Default no-op = {@code continuous} semantics. */
  default boolean shouldMatchEnd(final ArenaId arenaId, final RoundOutcome roundOutcome) {
    return false;
  }
}
