// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.List;

/** Typed binding for per-arena top-level settings; sister to fragment includes loaded by {@code SettingsSystem}. {@code friendlyFire} is an Infinity tri-state divergence from Subspace canon. */
public record ArenaConfig(
    String mapFile,
    String shipsScript,
    int spawnX,
    int spawnZ,
    List<String> fragmentIncludes,
    double wallFriction,
    List<SpawnerSpec> spawners,
    int friendlyFire) {

  /** Empty fallback — spawn at arena center, frictionless walls, friendly-fire off. */
  public static final ArenaConfig EMPTY =
      new ArenaConfig("", "", 512, 512, List.of(), 0.0, List.of(), 0);
}
