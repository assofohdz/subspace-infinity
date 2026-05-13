// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.WarpTouch;
import infinity.systems.ArenaMembershipSystem;
import infinity.systems.ArenaSystem;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ContactSystem;
import infinity.systems.MapSystem;
import infinity.systems.WorldSystem;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.Energy;
import infinity.es.ship.actions.WarpToChange;
import com.simsilica.mworld.World;
import infinity.InfinityConstants;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandTriFunction;
import infinity.sim.MapFactory;
import infinity.sim.internal.InfinityEntityBodyFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Canonical drain for {@link WarpToChange} (ADR 0001) — also handles warp-touch (wormhole) contacts and the {@code ~warpCenter} / {@code ~tpworld} / {@code ~tparena} chat commands. See {@code .claude/rules/replacement-as-mutation.md}. */
public class WarpSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  static Logger log = LoggerFactory.getLogger(WarpSystem.class);
  private final Pattern requestWarpToCenter = Pattern.compile("\\~warpCenter");
  private final Pattern requestTeleportWorld =
      Pattern.compile("\\~tpworld\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)");
  private final Pattern requestTeleportArena =
      Pattern.compile("\\~tparena\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)");
  private EntityData ed;
  private EntitySet warpTouchEntities;
  private EntitySet warpToChanges;
  private EntitySet canWarp;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private InfinityEntityBodyFactory bodyFactory;

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    physicsSpace = requireSystem(MPhysSystem.class).getPhysicsSpace();

    bodyFactory = getSystem(InfinityEntityBodyFactory.class);

    warpTouchEntities = ed.getEntities(WarpTouch.class);
    warpToChanges = ed.getEntities(WarpToChange.class, ChangeTarget.class);

    canWarp = ed.getEntities(BodyPosition.class, Energy.class);

    // Register consuming methods for patterns
    getSystem(InfinityChatHostedService.class)
        .registerPatternTriConsumer(
            requestWarpToCenter,
            "The command to warp to the center of the arena is ~warpCenter",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::commandRequestWarpToCenter));

    getSystem(InfinityChatHostedService.class)
        .registerPatternTriConsumer(
            requestTeleportWorld,
            "Teleport to world coordinates: ~tpworld <x> <z>",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::commandTeleportWorld));

    getSystem(InfinityChatHostedService.class)
        .registerPatternTriConsumer(
            requestTeleportArena,
            "Teleport to arena-local coordinates within the ship's current arena:"
                + " ~tparena <x> <z>",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::commandTeleportArena));

    getSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {
    warpTouchEntities.release();
    warpTouchEntities = null;

    warpToChanges.release();
    warpToChanges = null;

    canWarp.release();
    canWarp = null;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void update(SimTime tpf) {

    canWarp.applyChanges();
    warpTouchEntities.applyChanges();
    warpToChanges.applyChanges();

    drainWarpToChanges(tpf);
  }

  /** Per-target fold = last-write-wins; one-shot only (no Decay reversal). */
  private void drainWarpToChanges(final SimTime tpf) {
    final Map<EntityId, Vec3d> targetLocByShip = new LinkedHashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : warpToChanges.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final Vec3d target = added.get(WarpToChange.class).target();
      if (ct == null || ct.target() == null || target == null) {
        oneShotHolders.add(added.getId());
        continue;
      }
      targetLocByShip.put(ct.target(), target);
      oneShotHolders.add(added.getId());
    }

    for (final Map.Entry<EntityId, Vec3d> entry : targetLocByShip.entrySet()) {
      applyWarp(entry.getKey(), entry.getValue(), tpf);
    }

    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }
  }

  private void applyWarp(final EntityId shipId, final Vec3d targetLocation, final SimTime tpf) {
    final BodyPosition bodyPos = ed.getComponent(shipId, BodyPosition.class);
    if (bodyPos == null) {
      return;
    }
    final Vec3d originalLocation = bodyPos.getLastLocation();

    physicsSpace.teleport(shipId, targetLocation, bodyPos.getLastOrientation());

    MapFactory.createWarpEffect(
        ed,
        new infinity.sim.specs.WarpEffectSpec(
            shipId, physicsSpace, tpf.getTime(), originalLocation, 1000));
    MapFactory.createWarpEffect(
        ed,
        new infinity.sim.specs.WarpEffectSpec(
            shipId, physicsSpace, tpf.getTime(), targetLocation, 1000));

    // Zero motion so the body sleeps immediately — prevents redundant "left arena" log after spawn-warp.
    final RigidBody<EntityId, MBlockShape> body = bodyFactory.getBody(shipId);
    body.setLinearVelocity(Vec3d.ZERO);
    body.setRotationalVelocity(Vec3d.ZERO);
    body.setLinearAcceleration(Vec3d.ZERO);
    body.setRotationalAcceleration(0, 0, 0);
    body.clearAccumulators();

    // Reconcile ArenaId via ArenaMembershipSystem (sole writer) so ShipSpawnSystem doesn't reproject stale config.
    final EntityId resolvedArenaEntityId =
        getSystem(ArenaSystem.class).findArenaEntityAt(targetLocation);
    final ArenaMembershipSystem membership = getSystem(ArenaMembershipSystem.class);
    if (resolvedArenaEntityId != null) {
      membership.markEntered(shipId, resolvedArenaEntityId);
    } else {
      membership.markLeft(shipId);
    }
  }

  /** Warp the avatar to the arena center; returns chat-console feedback. */
  public String warpToCenter(EntityId avatarId) {
    Entity child = ed.getEntity(avatarId, BodyPosition.class);
    BodyPosition childBodyPos = child.get(BodyPosition.class);
    Vec3d lastLoc = childBodyPos.getLastLocation();

    Vec3d centerOfArena = getSystem(MapSystem.class).getCenterOfArena(lastLoc.x, lastLoc.z);
    emitWarpTo(child.getId(), child.getId(), centerOfArena);
    return "Warped to center of arena:" + centerOfArena;
  }

  private void emitWarpTo(final EntityId shipId, final EntityId source, final Vec3d target) {
    final EntityId h = ed.createEntity();
    ed.setComponents(h, new ChangeTarget(shipId, source), new WarpToChange(target));
  }

  public String commandRequestWarpToCenter(EntityId entityId, EntityId avatarId, Matcher matcher) {
    return warpToCenter(avatarId);
  }

  /** Teleport to world coords; refuses if the 3x3 target neighborhood contains any non-empty cell. */
  public String commandTeleportWorld(EntityId entityId, EntityId avatarId, Matcher matcher) {
    final double x = Double.parseDouble(matcher.group(1));
    final double z = Double.parseDouble(matcher.group(2));
    final Vec3d target = new Vec3d(x, InfinityConstants.GAMEPLAY_Y, z);

    final Vec3d blocker = firstOccupiedNeighbor(target);
    if (blocker != null) {
      return "Cannot teleport: destination neighborhood is blocked at " + blocker;
    }

    emitWarpTo(avatarId, avatarId, target);
    return "Teleporting to world " + target;
  }

  /** Teleport to arena-local coords (0,0)=NW; same neighbor-block guard as {@link #commandTeleportWorld}. */
  public String commandTeleportArena(EntityId entityId, EntityId avatarId, Matcher matcher) {
    final ArenaId arena = ed.getComponent(avatarId, ArenaId.class);
    if (arena == null) {
      return "Cannot teleport: ship is in no-arena void (no ArenaId)";
    }
    final ArenaSystem arenaSystem = getSystem(ArenaSystem.class);
    if (arenaSystem == null) {
      return "Cannot teleport: ArenaSystem unavailable";
    }
    final double localX = Double.parseDouble(matcher.group(1));
    final double localZ = Double.parseDouble(matcher.group(2));

    final ArenaMap map = arenaSystem.getArenaMap(arena.getArena());
    if (map == null) {
      return "Cannot teleport: arena '" + arena.getArena() + "' has no ArenaMap (not loaded?)";
    }
    final Vec3d target = ArenaSystem.arenaToWorld(map, localX, localZ);

    final Vec3d blocker = firstOccupiedNeighbor(target);
    if (blocker != null) {
      return "Cannot teleport: destination neighborhood is blocked at " + blocker;
    }

    emitWarpTo(avatarId, avatarId, target);
    return "Teleporting to arena " + arena.getArena() + " local (" + localX + ", " + localZ
        + ") = world " + target;
  }

  /** Returns first occupied cell in target's 3x3 X/Z neighborhood (target first), or null if clear. */
  private Vec3d firstOccupiedNeighbor(final Vec3d target) {
    final WorldSystem worldSystem = getSystem(WorldSystem.class);
    if (worldSystem == null) {
      return null;
    }
    final World world = worldSystem.getWorld();
    if (world == null) {
      return null;
    }
    final int[][] offsets =
        new int[][] {{0, 0}, {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    for (final int[] off : offsets) {
      final Vec3d probe = new Vec3d(target.x + off[0], target.y, target.z + off[1]);
      // MaskUtils.TYPE_MASK = low 20 bits.
      if ((world.getWorldCell(probe) & 0x000fffff) != 0) {
        return probe;
      }
    }
    return null;
  }

  @Override
  public void newContact(Contact contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    if (body2 == null) {
      return;
    }

    EntityId body1Id = body1.id;
    EntityId body2Id = body2.id;

    // body2Id as source distinguishes wormhole-driven warps from chat-command warps in attribution reactors.
    if (warpTouchEntities.containsId(body2Id)) {
      final WarpTouch warpTouch = warpTouchEntities.getEntity(body2Id).get(WarpTouch.class);
      emitWarpTo(body1Id, body2Id, warpTouch.getTargetLocation());
    }
  }
}
