// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.es.arena.ArenaId;
import javax.annotation.Nullable;

/**
 * Sibling-module resolver passed via {@link ModuleContext}. Lets modules in one arena reach
 * other modules in the same arena (e.g. {@code InstantRespawn} asks the arena's
 * {@code SpawnPlacementModule} for a respawn coord). Returns {@code null} when the arena has
 * no module set loaded.
 */
@FunctionalInterface
public interface ArenaModuleSetLookup {

  @Nullable
  ArenaModuleSet getModuleSet(ArenaId arenaId);
}
