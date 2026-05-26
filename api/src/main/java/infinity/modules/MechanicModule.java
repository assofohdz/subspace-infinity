// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.sim.SimTime;
import infinity.ai.objective.ArenaObjective;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/**
 * Opt-in mechanic category. Per-tick state publishing happens in
 * {@link #tickMechanic} (default no-op for event-only mechanics).
 */
public interface MechanicModule extends ArenaModule {

  /** Default no-op — event-driven mechanics ignore the tick. */
  default void tickMechanic(final ArenaId arenaId, final SimTime time) {
    // intentionally empty
  }

  /**
   * The {@link ArenaObjective} this mechanic produces (ADR-0015), or {@code null} if it has none.
   * Lives here, not on {@link ArenaModule}: {@code infinity.sim} must not depend on {@code infinity.ai}
   * (layer rule), and only mechanic modules define a gametype objective.
   */
  default ArenaObjective objective() {
    return null;
  }
}
