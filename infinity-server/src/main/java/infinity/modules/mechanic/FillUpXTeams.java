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
import infinity.modules.ArenaModuleSet;
import infinity.modules.ArenaModuleSetLookup;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import infinity.modules.SpawnPlacementModule;
import infinity.sim.AIEntities;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-tick keeps freqs {@code 0..teams-1} populated by spawning a bot mob
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
 *
 * <p>Bots spawn with full canonical {@code ShipConfig} stats. Tuning a ship to be
 * one-shottable (e.g. for kill-test arenas) is a per-arena bullet-damage / ship-energy
 * config concern, not a runtime spawner responsibility.
 */
public class FillUpXTeams implements MechanicModule {

  private final EntityData ed;
  private final EntityId arenaEntity;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> phys;
  private final int teams;
  private final int countPerPlayer;
  private final ArenaModuleSetLookup modules;
  /** Set (not List) so per-tick prune on bot reap is O(1). */
  private final Set<EntityId> spawnedBots = new HashSet<>();
  private EntitySet arenaShips;
  private Vec3d cachedSpawnCenter;

  public FillUpXTeams(final ModuleContext ctx, final FillUpXTeamsConfig config) {
    this.ed = ctx.ed();
    this.arenaEntity = ctx.arenaEntity();
    this.arenaId = ctx.arenaId();
    this.phys = ctx.physics() == null ? null : ctx.physics().getPhysics();
    this.teams = config.effectiveTeams();
    this.countPerPlayer = config.countPerPlayer();
    this.modules = ctx.modules();
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    if (arenaShips != null) {
      arenaShips.release();
    }
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
    if (arenaShips.applyChanges()) {
      // Prune our bookkeeping for any bot whose entity was reaped this tick —
      // their MobType + ArenaId + Frequency are gone, so they no longer
      // satisfy this set's filter.
      for (final Entity removed : arenaShips.getRemovedEntities()) {
        spawnedBots.remove(removed.getId());
      }
    }
    final int effectiveSpawnCount = teams + countPerPlayer * countActivePlayers();
    final Set<Integer> presentFreqs = countOccupiedFreqs();
    for (int freq = 0; freq < effectiveSpawnCount; freq++) {
      if (presentFreqs.contains(freq)) {
        continue;
      }
      spawnBot(time.getTime(), freq);
    }
  }

  /**
   * Count {@link PlayerShip}-marked entities in this arena. Used by the
   * {@code countPerPlayer} additive scaling per {@code player-scaling.md}. Walks
   * {@link #arenaShips} (already filtered by {@link ArenaId} + {@link Frequency}) and
   * checks {@link PlayerShip} membership per entity.
   */
  private int countActivePlayers() {
    if (countPerPlayer == 0 || arenaShips == null) {
      return 0;
    }
    final String arenaName = arenaId.getArena();
    int count = 0;
    for (final Entity e : arenaShips) {
      if (ed.getComponent(e.getId(), Dead.class) != null) {
        continue;
      }
      final ArenaId shipArena = e.get(ArenaId.class);
      if (shipArena == null || !arenaName.equals(shipArena.getArena())) {
        continue;
      }
      if (ed.getComponent(e.getId(), infinity.es.ship.PlayerShip.class) != null) {
        count++;
      }
    }
    return count;
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
    final Vec3d loc = resolveBotSpawn(freq);
    // Interim multi-hull demo: map freq → a distinct hull (freqs 0..7 → ships 1..8, cycling
    // beyond). Lets the ffa-bots smoke arena put one of each hull on the map so capability-
    // derived behaviour divergence is observable. Superseded by the per-arena `bots { }`
    // authoring in bot-ai-v2 slice #01.
    final byte shipId = (byte) (freq % Ship.values().length + 1);
    final EntityId bot =
        AIEntities.createMobShip(loc, ed, EntityId.NULL_ID, phys, createdTimeNanos, shipId);
    ed.setComponent(bot, arenaId);
    ed.setComponent(bot, new Frequency(freq));
    spawnedBots.add(bot);
    return bot;
  }

  /**
   * Honor the arena's {@code SpawnPlacementModule} when present (matches player respawn
   * behaviour); otherwise fall back to the arena-centre + per-freq lateral offset so
   * multiple bots spawning the same tick don't overlap.
   */
  private Vec3d resolveBotSpawn(final int freq) {
    if (modules != null) {
      final ArenaModuleSet set = modules.getModuleSet(arenaId);
      if (set != null) {
        final Optional<SpawnPlacementModule> placement = set.spawnPlacement();
        if (placement.isPresent()) {
          return placement.get().resolveSpawn(arenaId, freq);
        }
      }
    }
    return new Vec3d(
        cachedSpawnCenter.x + freq * 4.0, cachedSpawnCenter.y, cachedSpawnCenter.z);
  }
}
