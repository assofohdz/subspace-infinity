// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.respawn;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import infinity.es.CrownHolder;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BotShip;
import infinity.es.ship.ShipType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ArenaModuleSet;
import infinity.modules.ArenaModuleSetLookup;
import infinity.modules.ModuleContext;
import infinity.modules.RespawnPolicyModule;
import infinity.modules.RoundOutcome;
import infinity.modules.SpawnPlacementModule;
import infinity.sim.ShipFactory;
import infinity.sim.specs.ShipArgs;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * KOTH respawn policy: queue capture-on-kill (same shape as {@code InstantRespawn}) but the
 * drain is gated on the arena still having at least one {@link CrownHolder}. Once all crowns
 * are out, the dead stay ghosts until the next round-end. The queue is force-drained on
 * {@code onRoundEnd} so the next round starts with every player respawned (and the
 * {@code Crowns} mechanic's {@code onRoundStart} redistributes crowns immediately after).
 *
 * <p>Tests subclass to override {@link #spawnShip} + {@link #resolveSpawn}, mirroring
 * {@code CooldownRespawn}'s test seam.
 */
public class LockoutNoCrownRespawn implements RespawnPolicyModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> physics;
  private final ArenaModuleSetLookup modules;
  private final EntitySet crownsInArena;
  private final Deque<PendingRespawn> pending = new ArrayDeque<>();

  public LockoutNoCrownRespawn(final ModuleContext ctx) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.physics = ctx.physics() == null ? null : ctx.physics().getPhysics();
    this.modules = ctx.modules();
    this.crownsInArena = ed.getEntities(ArenaId.class, CrownHolder.class);
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    EventBus.addListener(this, PlayerKilledEvent.playerKilled);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    EventBus.removeListener(this, PlayerKilledEvent.playerKilled);
    crownsInArena.release();
    pending.clear();
  }

  @Override
  public void onRoundEnd(
      final ArenaId arenaIdParam, final int roundNumber, final RoundOutcome outcome) {
    // Force-drain at round-end so everyone who got locked out last round spawns for the new
    // round. Spawning happens before the next tickRespawnPolicy; Crowns.onRoundStart fires
    // after this in the lifecycle-dispatcher sequence and distributes crowns to the fresh ships.
    while (!pending.isEmpty()) {
      respawn(pending.poll(), 0L);
    }
  }

  /** EventBus reflective dispatch — name pattern is {@code on<EventTypeName>}. */
  public void onPlayerKilled(final PlayerKilledEvent event) {
    final EntityId victim = event.getVictim();
    final ArenaId victimArena = ed.getComponent(victim, ArenaId.class);
    if (victimArena == null || !arenaId.equals(victimArena)) {
      return;
    }
    if (ed.getComponent(victim, BotShip.class) != null) {
      return; // bot
    }
    final Parent parent = ed.getComponent(victim, Parent.class);
    final ShipType shipType = ed.getComponent(victim, ShipType.class);
    if (parent == null || shipType == null) {
      return;
    }
    final EntityId player = parent.getParentEntityId();
    if (player == null) {
      return;
    }
    final Frequency f = ed.getComponent(victim, Frequency.class);
    pending.add(new PendingRespawn(
        player, shipType.getType().getId(), f == null ? 0 : f.getFrequency()));
  }

  @Override
  public void tickRespawnPolicy(final ArenaId tickArenaId, final SimTime time) {
    if (pending.isEmpty()) {
      return;
    }
    if (!hasAnyCrownInArena()) {
      return; // gate closed — locked out until round-end
    }
    while (!pending.isEmpty()) {
      respawn(pending.poll(), time.getTime());
    }
  }

  private boolean hasAnyCrownInArena() {
    crownsInArena.applyChanges();
    for (final Entity holder : crownsInArena) {
      if (arenaId.equals(holder.get(ArenaId.class))) {
        return true;
      }
    }
    return false;
  }

  private void respawn(final PendingRespawn req, final long createdTimeNanos) {
    final Vec3d spawnLoc = resolveSpawn(req.freq());
    if (spawnLoc == null) {
      return;
    }
    spawnShip(req.player(), req.shipByte(), req.freq(), spawnLoc, createdTimeNanos);
  }

  /** Test seam — override to record spawn requests without invoking the physics-bound factory. */
  protected EntityId spawnShip(
      final EntityId player,
      final byte shipByte,
      final int freq,
      final Vec3d spawnLoc,
      final long createdTimeNanos) {
    final EntityId newShip = ShipFactory.createPlayerShip(
        ed,
        new ShipArgs(
            spawnLoc, player, physics, createdTimeNanos, shipByte,
            EngineConfig.DEFAULTS.shipRadius()));
    ed.setComponent(newShip, arenaId);
    if (freq != 0) {
      ed.setComponent(newShip, new Frequency(freq));
    }
    return newShip;
  }

  /** Test seam — short-circuits the spawn-placement module lookup in unit tests. */
  protected Vec3d resolveSpawn(final int freq) {
    if (modules == null) {
      return null;
    }
    final ArenaModuleSet set = modules.getModuleSet(arenaId);
    if (set == null) {
      return null;
    }
    final Optional<SpawnPlacementModule> placement = set.spawnPlacement();
    return placement.map(m -> m.resolveSpawn(arenaId, freq)).orElse(null);
  }

  private record PendingRespawn(EntityId player, byte shipByte, int freq) {}
}
