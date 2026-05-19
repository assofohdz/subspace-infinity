// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/**
 * Single-pick category. Owns the lifecycle of the arena's team entities
 * ({@code TeamEntity}, {@code Frequency} on the team entity, {@code TeamMemberCount}) per
 * ADR-0008. Per-tick state-publishing happens in {@link #tickTeamSetup} (default no-op
 * for event-only impls).
 */
public interface TeamSetupModule extends ArenaModule {

  /** Default no-op — pure event-driven impls ignore the tick. */
  default void tickTeamSetup(final ArenaId arenaId, final SimTime time) {
    // intentionally empty
  }
}
