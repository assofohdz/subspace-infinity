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
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import infinity.sim.AIEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
 *
 * <p>Bots are nerfed after {@code ShipSpawnSystem} projects the canonical Ship
 * stats: {@link Energy} clamped to {@link #BOT_HP} and {@link EnergyStats}
 * rewritten so the cap can't recharge above {@code BOT_HP} and regen is zero
 * (no self-heal between shots). Picks up the bot the tick after spawn via a
 * {@code pendingNerf} drain. Tunable defaults to one-shot from a warbird
 * bullet (~520 dmg) — adjust {@code BOT_HP} when testing other weapon damage.
 */
public class FillUpXTeams implements MechanicModule {

  /** Bot max energy after the post-spawn nerf — well below warbird bullet damage (~520). */
  private static final int BOT_HP = 100;

  private final EntityData ed;
  private final EntityId arenaEntity;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> phys;
  private final int teams;
  private final List<EntityId> spawnedBots = new ArrayList<>();
  private final Set<EntityId> pendingNerf = new HashSet<>();
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
    drainPendingNerf();
    final Set<Integer> presentFreqs = countOccupiedFreqs();
    for (int freq = 0; freq < teams; freq++) {
      if (presentFreqs.contains(freq)) {
        continue;
      }
      spawnBot(time.getTime(), freq);
    }
  }

  /** Once {@link EnergyStats} is projected, clamp Energy + max so warbird bullets one-shot. */
  private void drainPendingNerf() {
    final Iterator<EntityId> it = pendingNerf.iterator();
    while (it.hasNext()) {
      final EntityId bot = it.next();
      final EnergyStats existing = ed.getComponent(bot, EnergyStats.class);
      if (existing == null) {
        continue; // ShipSpawnSystem hasn't projected yet — retry next tick
      }
      ed.setComponent(bot, new Energy(BOT_HP));
      ed.setComponent(bot, new EnergyStats(BOT_HP, BOT_HP, 0, 0.0, 0.0, 0.0));
      it.remove();
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
    pendingNerf.add(bot);
    return bot;
  }
}
