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
import infinity.es.ship.BotShip;
import infinity.modules.ArenaModuleSet;
import infinity.modules.ArenaModuleSetLookup;
import infinity.modules.MechanicModule;
import infinity.modules.ModuleContext;
import infinity.modules.SpawnPlacementModule;
import infinity.modules.TeamSetupModule;
import infinity.sim.AIEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Per-tick tops the arena up with bot mobs (via {@link AIEntities#createMobShip}) toward a
 * target driven by the arena's {@code TeamSetupModule} ({@code teamSetup}):
 *
 * <ul>
 *   <li><b>Bounded</b> ({@link TeamSetupModule#fixedTeamCount()} present, e.g.
 *       two-fixed-teams): target = {@code fixedTeamCount × capacity} live ships (players +
 *       bots). Bots fill the seats humans don't and are <em>culled</em> when humans claim
 *       them. Per-team distribution is the team setup's job — this mechanic does not assign
 *       freqs.</li>
 *   <li><b>Unbounded</b> (FFA): target = {@code (roster size, else teams) + countPerPlayer ×
 *       players} live bots. Humans add freqs but don't displace bots; no cull.</li>
 * </ul>
 *
 * <p>Spawned bots are <em>not</em> freq-assigned here — the team setup claims them (it now
 * watches every ship, not just {@code PlayerShip}) and emits the {@code FrequencyChange}.
 * Auto-respawns after a bot dies (a {@link Dead} bot is excluded from the live count).
 * Tracks spawned bot ids and removes them on {@code onArenaUnload}.
 *
 * <p>Spawn loc: arena centre read from {@link ArenaMap}; first tick caches. No spawn happens
 * until {@code ArenaMap} is present (arena fully loaded).
 *
 * <p>Bots spawn with full canonical {@code ShipConfig} stats. Tuning a ship to be
 * one-shottable (e.g. for kill-test arenas) is a per-arena bullet-damage / ship-energy
 * config concern, not a runtime spawner responsibility.
 */
public class FillUpXTeams implements MechanicModule {

  /** Fallback lateral-offset wrap when no SpawnPlacementModule — keeps same-tick bots apart. */
  private static final int SPAWN_SPREAD = 8;

  private final EntityData ed;
  private final EntityId arenaEntity;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> phys;
  private final int teams;
  private final int countPerPlayer;
  private final int capacity;
  private final ArenaModuleSetLookup modules;
  // Expanded bots { } roster (ADR-0010/0014): hull pool cycled by spawn order. Empty ⇒ Javelin.
  private final List<Ship> roster;
  /** Set (not List) so per-tick prune on bot reap is O(1). */
  private final Set<EntityId> spawnedBots = new HashSet<>();
  /** Monotonic spawn counter — cycles the roster hull pool and offsets spawn position. */
  private int spawnCounter;
  private EntitySet arenaShips;
  private Vec3d cachedSpawnCenter;

  public FillUpXTeams(final ModuleContext ctx, final FillUpXTeamsConfig config) {
    this.ed = ctx.ed();
    this.arenaEntity = ctx.arenaEntity();
    this.arenaId = ctx.arenaId();
    this.phys = ctx.physics() == null ? null : ctx.physics().getPhysics();
    this.teams = config.effectiveTeams();
    this.countPerPlayer = config.countPerPlayer();
    this.capacity = config.effectiveCapacity();
    this.modules = ctx.modules();
    this.roster = ctx.bots().expandedRoster();
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
    arenaShips.applyChanges();
    pruneReapedBots();

    final int alivePlayers = countAlivePlayers();
    final List<EntityId> aliveBots = collectAliveBots();
    final OptionalInt fixedTeams = resolveFixedTeamCount();
    if (fixedTeams.isPresent()) {
      fillBounded(time.getTime(), fixedTeams.getAsInt(), alivePlayers, aliveBots);
    } else {
      fillUnbounded(time.getTime(), alivePlayers, aliveBots.size());
    }
  }

  /** Drop bots we've spawned whose entity was reaped ({@link BotShip} gone = entity removed). */
  private void pruneReapedBots() {
    spawnedBots.removeIf(id -> ed.getComponent(id, BotShip.class) == null);
  }

  /** Live humans (non-bot ships) in this arena. Dead excluded so a kill frees a seat. */
  private int countAlivePlayers() {
    int players = 0;
    for (final Entity e : arenaShips) {
      if (!arenaId.equals(e.get(ArenaId.class))
          || ed.getComponent(e.getId(), Dead.class) != null
          || ed.getComponent(e.getId(), BotShip.class) != null) {
        continue;
      }
      players++;
    }
    return players;
  }

  /**
   * Live bots = union of registered bots (in {@link #arenaShips} for this arena) and our
   * {@link #spawnedBots} tracker, minus {@link Dead}. Counting via the tracker — not the
   * ArenaId-gated set alone — is what stops a respawn storm: a stuck bot stops generating
   * arena-sensor contacts, so {@code ArenaMembershipSystem}'s exit-grace sweep strips its
   * ArenaId and it falls out of {@code arenaShips} while still alive. A bot whose ArenaId was
   * rewritten to a <em>different</em> arena is excluded — that arena's fill-up owns it.
   */
  private List<EntityId> collectAliveBots() {
    final Set<EntityId> bots = new LinkedHashSet<>();
    for (final Entity e : arenaShips) {
      final EntityId id = e.getId();
      if (arenaId.equals(e.get(ArenaId.class))
          && ed.getComponent(id, BotShip.class) != null
          && ed.getComponent(id, Dead.class) == null) {
        bots.add(id);
      }
    }
    for (final EntityId id : spawnedBots) {
      if (ed.getComponent(id, Dead.class) != null) {
        continue;
      }
      final ArenaId current = ed.getComponent(id, ArenaId.class);
      if (current == null || arenaId.equals(current)) {
        bots.add(id);
      }
    }
    return new ArrayList<>(bots);
  }

  /**
   * Bounded team-setup: keep {@code teamCount × capacity} live ships (players + bots). Bots
   * fill the deficit; surplus bots are culled when players claim the seats. The team setup
   * owns which team each bot lands on.
   */
  private void fillBounded(
      final long now,
      final int teamCount,
      final int alivePlayers,
      final List<EntityId> aliveBots) {
    final int target = teamCount * capacity;
    final int deficit = target - alivePlayers - aliveBots.size();
    for (int i = 0; i < deficit; i++) {
      spawnBot(now);
    }
    if (deficit < 0) {
      cullBots(aliveBots, Math.min(aliveBots.size(), -deficit));
    }
  }

  /**
   * FFA: keep a flat bot count alive — roster size (else {@code teams}) plus per-player
   * additive scaling. Humans add their own freqs but never displace bots.
   */
  private void fillUnbounded(final long now, final int players, final int liveBots) {
    final int base = roster.isEmpty() ? teams : roster.size();
    final int target = base + countPerPlayer * players;
    for (int i = liveBots; i < target; i++) {
      spawnBot(now);
    }
  }

  /** Remove {@code count} bots and untrack them; the team setup decrements counts on removal. */
  private void cullBots(final List<EntityId> aliveBots, final int count) {
    for (int i = 0; i < count; i++) {
      final EntityId bot = aliveBots.get(aliveBots.size() - 1 - i);
      ed.removeEntity(bot);
      spawnedBots.remove(bot);
    }
  }

  /** The arena team-setup's fixed team count, or empty (FFA / no team setup / no lookup). */
  private OptionalInt resolveFixedTeamCount() {
    if (modules == null) {
      return OptionalInt.empty();
    }
    final ArenaModuleSet set = modules.getModuleSet(arenaId);
    if (set == null) {
      return OptionalInt.empty();
    }
    return set.teamSetup().map(TeamSetupModule::fixedTeamCount).orElse(OptionalInt.empty());
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

  /** Spawn one bot and track it in {@link #spawnedBots}; bookkeeping wrapper over {@link #createBotEntity}. */
  protected final EntityId spawnBot(final long createdTimeNanos) {
    final EntityId bot = createBotEntity(createdTimeNanos);
    if (bot != null) {
      spawnedBots.add(bot);
    }
    return bot;
  }

  /** Physics-bound bot creation; test seam — override to record without the real factory. */
  protected EntityId createBotEntity(final long createdTimeNanos) {
    final int index = spawnCounter++;
    final Vec3d loc = resolveBotSpawn(index);
    // Hull cycles the bots { } roster pool; Javelin when unauthored. Freq is the team setup's job.
    final byte shipId =
        roster.isEmpty() ? Ship.JAVELIN.getId() : roster.get(index % roster.size()).getId();
    final EntityId bot =
        AIEntities.createMobShip(loc, ed, EntityId.NULL_ID, phys, createdTimeNanos, shipId);
    ed.setComponent(bot, arenaId);
    return bot;
  }

  /**
   * Honor the arena's {@code SpawnPlacementModule} when present (matches player respawn
   * behaviour); otherwise fall back to the arena-centre + a bounded lateral offset so
   * multiple bots spawning the same tick don't overlap. The freq is unknown until the team
   * setup claims the bot, so the spawn index stands in (placement reassigns on claim).
   */
  private Vec3d resolveBotSpawn(final int index) {
    if (modules != null) {
      final ArenaModuleSet set = modules.getModuleSet(arenaId);
      if (set != null) {
        final Optional<SpawnPlacementModule> placement = set.spawnPlacement();
        if (placement.isPresent()) {
          return placement.get().resolveSpawn(arenaId, index);
        }
      }
    }
    final int slot = index % SPAWN_SPREAD;
    return new Vec3d(
        cachedSpawnCenter.x + slot * 4.0, cachedSpawnCenter.y, cachedSpawnCenter.z);
  }
}
