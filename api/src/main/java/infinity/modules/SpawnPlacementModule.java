// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.mathd.Vec3d;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;

/** Resolves the world-space spawn location for a player joining or respawning in this arena. */
public interface SpawnPlacementModule extends ArenaModule {

  /**
   * Returns a world-space {@link Vec3d} for the given freq joining the arena.
   * Implementations may sample a random offset (RandomRadiusSpawnPlacement) or
   * return a deterministic point. Coordinate-system conversions from arena-local
   * to world live on the implementation side; callers pass the result straight
   * to {@code WarpToChange} or session initial-spawn.
   */
  Vec3d resolveSpawn(ArenaId arenaId, int freq);
}
