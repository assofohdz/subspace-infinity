// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/**
 * Single-pick category. Owns the death→respawn cycle for player ships in this arena.
 * Per-tick {@link #tickRespawnPolicy} is the canonical hook (default no-op for impls
 * that drive entirely off event subscriptions).
 */
public interface RespawnPolicyModule extends ArenaModule {

  /** Default no-op — pure event-driven impls ignore the tick. */
  default void tickRespawnPolicy(final ArenaId arenaId, final SimTime time) {
    // intentionally empty
  }
}
