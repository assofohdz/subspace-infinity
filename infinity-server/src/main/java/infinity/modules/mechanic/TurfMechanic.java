// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import infinity.ai.objective.ArenaObjective;
import infinity.ai.objective.GoalTile;
import infinity.ai.objective.TurfObjective;
import infinity.es.Flag;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Turf (flag-occupancy) mechanic: produces a {@link TurfObjective} whose static goal tiles are the
 * map's stationary flags (ADR-0015), so bots hold the flag rather than only drifting toward it via
 * traffic heat. Flags are spawned by the map projector as orphan entities (no {@code ArenaId}/parent),
 * so they're scoped to this arena by world-bounds from the arena's {@link ArenaMap}.
 */
public final class TurfMechanic implements MechanicModule {

  private final EntityData ed;
  private final EntityId arenaEntity;
  private EntitySet flags;
  // Cached once flags are seen; an early empty call (flags not yet spawned) is not cached so it self-heals.
  private ArenaObjective cached;

  public TurfMechanic(final ModuleContext ctx) {
    this.ed = ctx.ed();
    this.arenaEntity = ctx.arenaEntity();
  }

  @Override
  public void onArenaLoad(final ArenaId arenaId) {
    if (this.flags != null) {
      this.flags.release();
    }
    this.flags = this.ed.getEntities(Flag.class, SpawnPosition.class);
  }

  @Override
  public void onArenaUnload(final ArenaId arenaId) {
    if (this.flags != null) {
      this.flags.release();
      this.flags = null;
    }
    this.cached = null;
  }

  @Override
  public ArenaObjective objective() {
    if (this.cached != null) {
      return this.cached;
    }
    final ArenaObjective built = buildObjective();
    if (!built.staticGoalTiles().isEmpty()) {
      this.cached = built;
    }
    return built;
  }

  private ArenaObjective buildObjective() {
    final ArenaMap map = this.ed.getComponent(this.arenaEntity, ArenaMap.class);
    if (this.flags == null || map == null) {
      return new TurfObjective(List.of());
    }
    this.flags.applyChanges();
    final Vec3d min = map.getMin();
    final Vec3d max = map.getMax();
    final List<GoalTile> tiles = new ArrayList<>();
    for (final Entity e : this.flags) {
      final Vec3d loc = e.get(SpawnPosition.class).getLocation();
      if (loc.x >= min.x && loc.x < max.x && loc.z >= min.z && loc.z < max.z) {
        tiles.add(new GoalTile((int) Math.floor(loc.x), (int) Math.floor(loc.z)));
      }
    }
    return new TurfObjective(tiles);
  }
}
