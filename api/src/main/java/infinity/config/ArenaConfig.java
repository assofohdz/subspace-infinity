// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.List;

/**
 * Typed binding for per-arena top-level settings; sister to fragment includes loaded by {@code SettingsSystem}.
 *
 * <p>{@code wallFriction} is applied directly to body linear velocity, NOT via mphys's rigid-body friction
 * model — the resolver's friction produces torque at off-center contact points, which would rotate ship
 * heading toward the wall (wrong for arcade physics). Range {@code [0, 1]}; {@code 0} = frictionless.
 * Normal-direction velocity stays under {@code BounceRestitution}'s control.
 *
 * <p>{@code friendlyFire} tri-state ({@code 0}=off, {@code 1}=bomb-splash-only, {@code 2}=all) is an
 * Infinity divergence — Subspace canon models FF with separate per-weapon flags.
 */
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
