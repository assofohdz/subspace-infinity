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
import infinity.es.WarpTouch;
import infinity.systems.ArenaMembershipSystem;
import infinity.systems.ArenaSystem;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ContactSystem;
import infinity.systems.MapSystem;
import infinity.systems.WorldSystem;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.Health;
import infinity.es.ship.actions.WarpTo;
import com.simsilica.mworld.World;
import infinity.InfinityConstants;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandTriFunction;
import infinity.sim.MapFactory;
import infinity.sim.InfinityEntityBodyFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This system handles the warping of units. It is responsible for the implementation of the warp
 * command, and the warp touch component.
 *
 * @author Asser
 */
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
  private EntitySet warpToEntities;
  private EntitySet canWarp;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private InfinityEntityBodyFactory bodyFactory;

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    physicsSpace = requireSystem(MPhysSystem.class).getPhysicsSpace();

    bodyFactory = getSystem(InfinityEntityBodyFactory.class);

    warpTouchEntities = ed.getEntities(WarpTouch.class);
    warpToEntities = ed.getEntities(BodyPosition.class, WarpTo.class);

    canWarp = ed.getEntities(BodyPosition.class, Health.class);

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

    warpToEntities.release();
    warpToEntities = null;

    canWarp.release();
    canWarp = null;
  }

  @Override
  public void start() {
    // Auto generated method stub
  }

  @Override
  public void stop() {
    // Auto generated method stub
  }

  @Override
  public void update(SimTime tpf) {

    canWarp.applyChanges();
    warpTouchEntities.applyChanges();

    if (warpToEntities.applyChanges()) {
      for (Entity e : warpToEntities) {
        BodyPosition bodyPos = e.get(BodyPosition.class);
        Vec3d targetLocation = e.get(WarpTo.class).getTargetLocation();
        Vec3d originalLocation = bodyPos.getLastLocation();

        // This is the new method to teleport units
        physicsSpace.teleport(e.getId(), targetLocation, bodyPos.getLastOrientation());

        MapFactory.createWarpEffect(
            ed,
            new infinity.sim.specs.WarpEffectSpec(
                e.getId(), physicsSpace, tpf.getTime(), originalLocation, 1000));
        MapFactory.createWarpEffect(
            ed,
            new infinity.sim.specs.WarpEffectSpec(
                e.getId(), physicsSpace, tpf.getTime(), targetLocation, 1000));

        //         Ensure that the unit is not moving after the warp
        RigidBody<EntityId, MBlockShape> body = bodyFactory.getBody(e.getId());
        body.setLinearVelocity(Vec3d.ZERO);
        body.setRotationalVelocity(Vec3d.ZERO);
        body.setLinearAcceleration(Vec3d.ZERO);
        body.setRotationalAcceleration(0, 0, 0);
        body.clearAccumulators();

        // Reconcile arena membership through ArenaMembershipSystem (the sole writer
        // of ship-side ArenaId). The warp zeroed velocity above, so the body will
        // sleep and stop generating contacts immediately — without this the per-tick
        // exit-grace sweep fires a redundant "left arena" log a second after every
        // spawn-warp. Null destination → ship landed in
        // no-arena void; flush any current memberships so downstream consumers (esp.
        // ShipSpawnSystem's (ShipType, ArenaId) watcher) don't project a now-wrong
        // ShipConfig.
        final EntityId resolvedArenaEntityId =
            getSystem(ArenaSystem.class).findArenaEntityAt(targetLocation);
        final ArenaMembershipSystem membership = getSystem(ArenaMembershipSystem.class);
        if (resolvedArenaEntityId != null) {
          membership.markEntered(e.getId(), resolvedArenaEntityId);
        } else {
          membership.markLeft(e.getId());
        }

        ed.removeComponent(e.getId(), WarpTo.class);
      }
    }
  }

  /**
   * This method is called when a warp is requested by the player. It will warp the player to the
   * center of the arena.
   *
   * @param avatarId The entity id of the player avatar
   * @return A string that can be sent to the player's chat console
   */
  public String warpToCenter(EntityId avatarId) {
    Entity child = ed.getEntity(avatarId, BodyPosition.class);
    BodyPosition childBodyPos = child.get(BodyPosition.class);
    Vec3d lastLoc = childBodyPos.getLastLocation();

    Vec3d centerOfArena = getSystem(MapSystem.class).getCenterOfArena(lastLoc.x, lastLoc.z);
    WarpTo warpTo = new WarpTo(centerOfArena);
    ed.setComponent(child.getId(), warpTo);
    return "Warped to center of arena:" + centerOfArena;
  }

  /**
   * Lets entities request a warp to the center of the arena.
   *
   * @param avatarId requesting entity
   */
  public String commandRequestWarpToCenter(EntityId entityId, EntityId avatarId, Matcher matcher) {

    return warpToCenter(avatarId);
  }

  /**
   * Teleports the avatar to explicit world coordinates. Useful for verifying multi-map grids
   * where the target sits outside the current arena. Refuses destinations whose 3x3 cell
   * neighborhood (target + 8 X/Z neighbors on the gameplay plane) contains any non-empty cell —
   * a ship's collider is larger than one cell, and physics resolution of a near-wall teleport has
   * been observed to drift the ship off the gameplay plane.
   */
  public String commandTeleportWorld(EntityId entityId, EntityId avatarId, Matcher matcher) {
    final double x = Double.parseDouble(matcher.group(1));
    final double z = Double.parseDouble(matcher.group(2));
    final Vec3d target = new Vec3d(x, InfinityConstants.GAMEPLAY_Y, z);

    final Vec3d blocker = firstOccupiedNeighbor(target);
    if (blocker != null) {
      return "Cannot teleport: destination neighborhood is blocked at " + blocker;
    }

    ed.setComponent(avatarId, new WarpTo(target));
    return "Teleporting to world " + target;
  }

  /**
   * Teleports the avatar to arena-local coordinates within its <i>current</i> arena. Refuses
   * if the avatar has no {@code ArenaId} (no-arena void), if the named arena isn't loaded, or
   * if the resolved world coord fails the same neighbor-block check used by {@link
   * #commandTeleportWorld}. Arena-local convention: {@code (0, 0) = NW corner}, {@code
   * (1024, 1024) = SE corner} (see {@code ArenaSystem.arenaToWorld}).
   */
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

    // Resolve via the same translation used by spawn / ship-change paths so all
    // three flows agree on what arena-local coords mean.
    final ArenaMap map = arenaSystem.getArenaMap(arena.getArena());
    if (map == null) {
      return "Cannot teleport: arena '" + arena.getArena() + "' has no ArenaMap (not loaded?)";
    }
    final Vec3d target = ArenaSystem.arenaToWorld(map, localX, localZ);

    final Vec3d blocker = firstOccupiedNeighbor(target);
    if (blocker != null) {
      return "Cannot teleport: destination neighborhood is blocked at " + blocker;
    }

    ed.setComponent(avatarId, new WarpTo(target));
    return "Teleporting to arena " + arena.getArena() + " local (" + localX + ", " + localZ
        + ") = world " + target;
  }

  /**
   * Scan the 3x3 X/Z neighborhood around {@code target} on the gameplay plane and return the
   * first cell with a non-zero block type, or {@code null} if the whole 3x3 is clear. The target
   * itself is checked first so the most common "inside a wall" case reports the obvious cell.
   */
  private Vec3d firstOccupiedNeighbor(final Vec3d target) {
    final WorldSystem worldSystem = getSystem(WorldSystem.class);
    if (worldSystem == null) {
      return null;
    }
    final World world = worldSystem.getWorld();
    if (world == null) {
      return null;
    }
    // Check center first, then neighbors, so the error message points at the target cell when
    // the target itself is a wall (the common case).
    final int[][] offsets =
        new int[][] {{0, 0}, {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    for (final int[] off : offsets) {
      final Vec3d probe = new Vec3d(target.x + off[0], target.y, target.z + off[1]);
      // Type bits live in the low 20 bits of the cell int (MaskUtils.TYPE_MASK).
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

    // If body2 is null, then the contact is with the world and we should not handle this
    if (body2 == null) {
      return;
    }

    EntityId body1Id = body1.id;
    EntityId body2Id = body2.id;

    // Warp body1 if body2 is a warp touch entity
    if (warpTouchEntities.containsId(body2Id)) {
      WarpTouch warpTouch = warpTouchEntities.getEntity(body2Id).get(WarpTouch.class);
      WarpTo warpTo = new WarpTo(warpTouch.getTargetLocation());
      ed.setComponent(body1Id, warpTo);
    }
  }
}
