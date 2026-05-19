// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.spawnplacement;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import infinity.InfinityConstants;
import infinity.config.RandomRadiusConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.modules.ModuleContext;
import infinity.modules.SpawnPlacementModule;
import infinity.systems.SpawnCircleSampler;
import java.util.List;

/**
 * Samples uniformly in a disc around the configured {@code center} (or {@code centers[freq]}),
 * converts arena-local tiles to world coords via the same anchor as
 * {@code ArenaSpatialIndex.arenaToWorld} (NW corner mirrored onto {@link ArenaMap#getMax()}).
 * Per-freq centers wrap via {@code freq % centers.size()}; missing/blank → first entry.
 */
public final class RandomRadiusSpawnPlacement implements SpawnPlacementModule {

  private final EntityData ed;
  private final EntityId arenaEntityId;
  private final RandomRadiusConfig config;

  public RandomRadiusSpawnPlacement(final ModuleContext ctx, final RandomRadiusConfig config) {
    this.ed = ctx.ed();
    this.arenaEntityId = ctx.arenaEntity();
    this.config = config;
  }

  @Override
  public Vec3d resolveSpawn(final ArenaId arenaId, final int freq) {
    final ArenaMap map = ed.getComponent(arenaEntityId, ArenaMap.class);
    if (map == null) {
      throw new IllegalStateException(
          "RandomRadiusSpawnPlacement: arena " + arenaId.getArena() + " has no ArenaMap component");
    }
    final List<Integer> centerXz = config.centerFor(freq);
    final double[] sample =
        SpawnCircleSampler.sample(centerXz.get(0), centerXz.get(1), config.radius());
    return new Vec3d(
        map.getMax().x - sample[0],
        InfinityConstants.GAMEPLAY_Y,
        map.getMax().z - sample[1]);
  }
}
