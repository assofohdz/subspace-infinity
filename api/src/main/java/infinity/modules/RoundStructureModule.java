// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/** Single-pick round-structure category. Tick-driven impls override {@link #tickRoundStructure} to emit {@code RoundEndPending} on the arena entity. */
public interface RoundStructureModule extends ArenaModule {

  /** Default no-op — event-driven impls (e.g. {@code crown-count-zero}) ignore the tick. */
  default void tickRoundStructure(final ArenaId arenaId, final SimTime time) {
    // intentionally empty — subclasses override when they need per-tick state.
  }
}
