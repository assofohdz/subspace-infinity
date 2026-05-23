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
import infinity.es.ChangeTarget;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import infinity.modules.ArenaModuleSet;
import infinity.modules.ArenaModuleSetLookup;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import infinity.modules.SpawnPlacementModule;
import infinity.sim.AIEntities;
import java.util.Optional;
import java.util.HashSet;
import java.util.Iterator;
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
  private final int countPerPlayer;
  private final ArenaModuleSetLookup modules;
  /** Set (not List) so per-tick prune on bot reap is O(1). */
  private final Set<EntityId> spawnedBots = new HashSet<>();
  private final Set<EntityId> pendingNerf = new HashSet<>();
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
        pendingNerf.remove(removed.getId());
      }
    }
    drainPendingNerf();
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

  /**
   * Once {@link EnergyStats} is projected, emit canonical-writer change-entities
   * to clamp Energy + max so warbird bullets one-shot. Direct setComponent
   * would violate ADR-0001's single-canonical-writer rule for Energy /
   * EnergyStats (those are owned by EnergySystem / EnergyStatsSystem).
   *
   * <p>Skips Dead bots — their EnergyStats projection survives until reap, but
   * applying changes to a corpse is wasted work + the EnergySystem.applyDelta
   * Dead-gate would no-op anyway.
   */
  private void drainPendingNerf() {
    final Iterator<EntityId> it = pendingNerf.iterator();
    while (it.hasNext()) {
      final EntityId bot = it.next();
      if (ed.getComponent(bot, Dead.class) != null) {
        it.remove();
        continue;
      }
      final EnergyStats existing = ed.getComponent(bot, EnergyStats.class);
      if (existing == null) {
        continue; // ShipSpawnSystem hasn't projected yet — retry next tick
      }
      emitNerfChanges(bot, existing);
      it.remove();
    }
  }

  /** Emits one Change-entity per Energy / EnergyStats field needed to clamp the bot to {@link #BOT_HP}. */
  private void emitNerfChanges(final EntityId bot, final EnergyStats existing) {
    emitEnergyClamp(bot);
    emitStatsClamp(bot, existing);
  }

  private void emitEnergyClamp(final EntityId bot) {
    final infinity.es.ship.Energy current = ed.getComponent(bot, infinity.es.ship.Energy.class);
    final int delta = BOT_HP - (current == null ? 0 : current.getEnergy());
    if (delta == 0) {
      return;
    }
    final EntityId h = ed.createEntity();
    ed.setComponents(h, ChangeTarget.self(bot), new EnergyChange(delta));
  }

  private void emitStatsClamp(final EntityId bot, final EnergyStats existing) {
    final Integer dMax = nonZeroOrNull(BOT_HP - existing.max());
    final Integer dHardMax = nonZeroOrNull(BOT_HP - existing.hardMax());
    final Double dRps = nonZeroOrNull(-existing.rechargePerSecond());
    final Double dRmax = nonZeroOrNull(-existing.rechargeMax());
    final Double dRup = nonZeroOrNull(-existing.rechargeUpgrade());
    if (dMax == null && dHardMax == null && dRps == null && dRmax == null && dRup == null) {
      return;
    }
    final EntityId h = ed.createEntity();
    ed.setComponents(
        h,
        ChangeTarget.self(bot),
        new EnergyStatsChange(dMax, dHardMax, null, dRps, dRmax, dRup));
  }

  private static Integer nonZeroOrNull(final int v) {
    return v == 0 ? null : v;
  }

  private static Double nonZeroOrNull(final double v) {
    return Double.compare(v, 0.0) == 0 ? null : v;
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
    final EntityId bot = AIEntities.createMobShip(
        loc, ed, EntityId.NULL_ID, phys, createdTimeNanos, Ship.JAVELIN.getId());
    ed.setComponent(bot, arenaId);
    ed.setComponent(bot, new Frequency(freq));
    spawnedBots.add(bot);
    pendingNerf.add(bot);
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
