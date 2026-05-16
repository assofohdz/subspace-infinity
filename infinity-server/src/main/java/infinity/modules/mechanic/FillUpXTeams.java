// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.mechanic;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.Ship;
import infinity.config.FillUpXTeamsConfig;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import infinity.sim.AIEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-tick keeps freqs {@code 0..teams-1} populated by spawning a Javelin mob
 * (via {@link AIEntities#createMobShip}) on any freq that currently has zero
 * non-{@link Dead} ships in this arena. Auto-respawns after a bot is reaped
 * (next tick after {@code DecaySystem} clears the corpse). Tracks spawned
 * bot ids and removes them on {@code onArenaUnload}.
 *
 * <p>FFA semantics today: one bot per freq. Team-v-team teamSetup modules
 * (F2e+) will need a {@code maxShipsPerTeam} accessor so this mechanic can
 * fill each team to capacity; until then the per-freq-target is hard-coded
 * to 1.
 *
 * <p>Spawn loc: arena centre read from {@link ArenaMap}; first tick caches.
 * No spawn happens until {@code ArenaMap} is present (arena fully loaded).
 */
public class FillUpXTeams implements MechanicModule {

  private final EntityData ed;
  private final EntityId arenaEntity;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> phys;
  private final int teams;
  private final List<EntityId> spawnedBots = new ArrayList<>();
  private EntitySet arenaShips;
  private Vec3d cachedSpawnCenter;

  public FillUpXTeams(final ModuleContext ctx, final FillUpXTeamsConfig config) {
    this.ed = ctx.ed();
    this.arenaEntity = ctx.arenaEntity();
    this.arenaId = ctx.arenaId();
    this.phys = ctx.physics() == null ? null : ctx.physics().getPhysics();
    this.teams = config.effectiveTeams();
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    arenaShips = ed.getEntities(ArenaId.class, Frequency.class);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    for (final EntityId bot : spawnedBots) {
      ed.removeEntity(bot);
    }
    spawnedBots.clear();
    if (arenaShips != null) {
      arenaShips.release();
      arenaShips = null;
    }
  }

  @Override
  public void tickMechanic(final ArenaId tickArenaId, final SimTime time) {
    if (arenaShips == null) {
      return; // before onArenaLoad
    }
    if (cachedSpawnCenter == null) {
      cachedSpawnCenter = readSpawnCenter();
      if (cachedSpawnCenter == null) {
        return; // ArenaMap not stamped yet
      }
    }
    arenaShips.applyChanges();
    final Set<Integer> presentFreqs = countOccupiedFreqs();
    for (int freq = 0; freq < teams; freq++) {
      if (presentFreqs.contains(freq)) {
        continue;
      }
      spawnBot(time.getTime(), freq);
    }
  }

  private Set<Integer> countOccupiedFreqs() {
    final Set<Integer> present = new HashSet<>();
    for (final Entity e : arenaShips) {
      if (!arenaId.equals(e.get(ArenaId.class))) {
        continue;
      }
      if (ed.getComponent(e.getId(), Dead.class) != null) {
        continue;
      }
      present.add(e.get(Frequency.class).getFrequency());
      if (present.size() >= teams) {
        return present; // enough; no need to walk the rest
      }
    }
    return present;
  }

  private Vec3d readSpawnCenter() {
    final ArenaMap map = ed.getComponent(arenaEntity, ArenaMap.class);
    if (map == null) {
      return null;
    }
    final Vec3d min = map.getMin();
    final Vec3d max = map.getMax();
    return new Vec3d(
        (min.x + max.x) / 2.0, InfinityConstants.GAMEPLAY_Y, (min.z + max.z) / 2.0);
  }

  /** Test seam — override to record spawn requests without invoking the physics-bound factory. */
  protected EntityId spawnBot(final long createdTimeNanos, final int freq) {
    // Per-freq lateral offset so multiple bots spawning the same tick don't overlap.
    final Vec3d loc = new Vec3d(
        cachedSpawnCenter.x + freq * 4.0,
        cachedSpawnCenter.y,
        cachedSpawnCenter.z);
    final EntityId bot = AIEntities.createMobShip(
        loc, ed, EntityId.NULL_ID, phys, createdTimeNanos, Ship.JAVELIN.getId());
    ed.setComponent(bot, arenaId);
    ed.setComponent(bot, new Frequency(freq));
    spawnedBots.add(bot);
    return bot;
  }
}
