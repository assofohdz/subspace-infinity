// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.respawn;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.config.CooldownRespawnConfig;
import infinity.config.EngineConfig;
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
import infinity.modules.SpawnPlacementModule;
import infinity.sim.ShipFactory;
import infinity.sim.specs.ShipArgs;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Like {@code InstantRespawn} but defers the respawn-spawn by a configurable wallclock delay.
 * Captures the dying ship's {@code ShipType} + {@code Frequency} + {@code Parent} at
 * {@link PlayerKilledEvent} fire time (before the reaper removes the entity); each pending
 * entry holds a sim-time deadline ({@code latestTickNanos + delayNanos}); the next
 * {@link #tickRespawnPolicy} whose time passes the deadline creates the fresh ship.
 *
 * <p>Edge case: when a kill fires before any {@link #tickRespawnPolicy} call has cached a
 * sim-time reference (unusual — would mean death before the first server tick after
 * arena-load), the deadline is stamped on the entry's first observed tick. Subsequent ticks
 * compare normally.
 */
public class CooldownRespawn implements RespawnPolicyModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> physics;
  private final ArenaModuleSetLookup modules;
  private final long delayNanos;
  private final List<PendingRespawn> pending = new ArrayList<>();
  /** Sim-time captured at the start of each {@link #tickRespawnPolicy}; sentinel {@code -1} = never ticked. */
  private long latestTickNanos = -1L;

  public CooldownRespawn(final ModuleContext ctx, final CooldownRespawnConfig config) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.physics = ctx.physics() == null ? null : ctx.physics().getPhysics();
    this.modules = ctx.modules();
    this.delayNanos = TimeUnit.SECONDS.toNanos(config.seconds());
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    EventBus.addListener(this, PlayerKilledEvent.playerKilled);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    EventBus.removeListener(this, PlayerKilledEvent.playerKilled);
    pending.clear();
  }

  /** EventBus reflective dispatch — name pattern is {@code on<EventTypeName>}. */
  public void onPlayerKilled(final PlayerKilledEvent event) {
    final EntityId victim = event.getVictim();
    final ArenaId victimArena = ed.getComponent(victim, ArenaId.class);
    if (victimArena == null || !arenaId.equals(victimArena)) {
      return;
    }
    if (ed.getComponent(victim, BotShip.class) != null) {
      return; // bot — fill-up-x-teams handles it
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
    final long deadline = latestTickNanos < 0 ? -1L : latestTickNanos + delayNanos;
    pending.add(new PendingRespawn(
        player, shipType.getType().getId(), f == null ? 0 : f.getFrequency(), deadline));
  }

  @Override
  public void tickRespawnPolicy(final ArenaId tickArenaId, final SimTime time) {
    latestTickNanos = time.getTime();
    if (pending.isEmpty()) {
      return;
    }
    final Iterator<PendingRespawn> it = pending.iterator();
    while (it.hasNext()) {
      final PendingRespawn req = it.next();
      // Late-bind any deadline stamped before the first tick was observed.
      final long effectiveDeadline =
          req.deadline() < 0 ? latestTickNanos + delayNanos : req.deadline();
      if (latestTickNanos < effectiveDeadline) {
        // Patch the entry if it carried the late-bind sentinel; future ticks compare directly.
        if (req.deadline() < 0) {
          it.remove();
          pending.add(new PendingRespawn(
              req.player(), req.shipByte(), req.freq(), effectiveDeadline));
        }
        continue;
      }
      respawn(req, latestTickNanos);
      it.remove();
    }
  }

  /** Create a fresh ship for the player, preserving ship type + freq + arena from the prior life. */
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

  /** Test seam — overridable so unit tests can short-circuit without wiring a placement module. */
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

  /** {@code deadline < 0} = "stamp on first observed tick" sentinel. */
  private record PendingRespawn(EntityId player, byte shipByte, int freq, long deadline) {}
}
