// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.respawn;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.arena.ArenaId;
import infinity.es.ship.PlayerShip;
import infinity.es.ship.ShipType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.modules.ArenaModuleSet;
import infinity.modules.ArenaModuleSetLookup;
import infinity.modules.ModuleContext;
import infinity.modules.RespawnPolicyModule;
import infinity.modules.SpawnPlacementModule;
import infinity.sim.ShipFactory;
import infinity.sim.specs.ShipArgs;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * On a {@link PlayerKilledEvent} for a player ship in this arena, captures the dying ship's
 * {@code ShipType} + {@code Frequency} + {@code Parent} (the player), then queues a respawn.
 * On the next {@link #tickRespawnPolicy} the queued spawns create fresh ship entities via
 * {@link ShipFactory#createPlayerShip} — the old ship reaps via the canonical
 * {@code DeathSystem} → {@code Decay} flow, and {@code AvatarSystem} rebinds
 * {@code CurrentShip} on the player to the new ship next tick (see player-vs-ship-identity
 * PRD slice P2).
 *
 * <p>Captures data at event-fire time (before the reaper removes the dying entity) — using a
 * post-Decay {@code EntitySet} would race the reaper and miss components. Bots (no
 * {@link PlayerShip} marker) are ignored; their respawn is the {@code FillUpXTeams}
 * mechanic's job.
 */
public final class InstantRespawn implements RespawnPolicyModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final PhysicsSpace<?, ?> physics;
  private final ArenaModuleSetLookup modules;
  private final Deque<PendingRespawn> pending = new ArrayDeque<>();

  public InstantRespawn(final ModuleContext ctx) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.physics = ctx.physics() == null ? null : ctx.physics().getPhysics();
    this.modules = ctx.modules();
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
    if (ed.getComponent(victim, PlayerShip.class) == null) {
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
    pending.add(new PendingRespawn(
        player, shipType.getType().getId(), f == null ? 0 : f.getFrequency()));
  }

  @Override
  public void tickRespawnPolicy(final ArenaId tickArenaId, final SimTime time) {
    while (!pending.isEmpty()) {
      respawn(pending.poll(), time.getTime());
    }
  }

  /** Create a fresh ship for the player, preserving ship type + freq + arena from the prior life. */
  private void respawn(final PendingRespawn req, final long createdTimeNanos) {
    final Vec3d spawnLoc = resolveSpawn(req.freq());
    if (spawnLoc == null) {
      return; // spawn placement missing — log already happens in ArenaSpatialIndex
    }
    final EntityId newShip = ShipFactory.createPlayerShip(
        ed,
        new ShipArgs(
            spawnLoc, req.player(), physics, createdTimeNanos, req.shipByte(),
            EngineConfig.DEFAULTS.shipRadius()));
    ed.setComponent(newShip, arenaId);
    if (req.freq() != 0) {
      ed.setComponent(newShip, new Frequency(req.freq()));
    }
  }

  /** Delegate to the arena's active {@link SpawnPlacementModule}; {@code null} if none loaded. */
  private Vec3d resolveSpawn(final int freq) {
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

  /** Captured at event-fire time; drained next tick. */
  private record PendingRespawn(EntityId player, byte shipByte, int freq) {}
}
