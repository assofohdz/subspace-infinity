/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.sim;

import com.jme3.math.ColorRGBA;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.Ship;
import infinity.es.AudioTypes;
import infinity.es.Bounty;
import infinity.es.CollisionCategory;
import infinity.es.Delay;
import infinity.es.Door;
import infinity.es.Flag;
import infinity.es.Frequency;
import infinity.es.Gold;
import infinity.es.GravityWell;
import infinity.es.Meta;
import infinity.es.Parent;
import infinity.es.PointLightComponent;
import infinity.es.PrizeType;
import infinity.es.PrizeWeightsOverride;
import infinity.es.ShapeNames;
import infinity.es.Spawner;
import infinity.es.SphereShape;
import infinity.es.WarpTouch;
import infinity.es.WeaponTypes;
import infinity.es.input.MovementInput;
import infinity.es.ship.CollidesWithLargeStatics;
import infinity.es.ship.Player;
import infinity.es.ship.ShipType;
import infinity.es.ship.actions.Thor;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for creating the common game entities used by the simulation. In cases where a
 * game entity may have multiple specific components or dependencies used to create it, it can be
 * more convenient to have a centralized factory method. Especially if those objects are widely
 * used. For entities with only a few components or that are created by one system and only consumed
 * by one other, then this is not necessarily true.
 */
public class GameEntities {

  private GameEntities() {}

  // TODO: All constants should come through the parameters - for now, they come from the constants
  // TODO: All parameters should be dumb types and should be the basis of the complex types used in
  // the backend
  public static EntityId createDelayedBomb(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final Vec3d linearVelocity,
      final long decayMillis,
      final long scheduledMillis,
      final HashSet<EntityComponent> delayedComponents,
      final String shapeName) {

    final EntityId lastDelayedBomb =
        GameEntities.createBomb(
            ed, owner, phys, createdTime, pos, linearVelocity, decayMillis, shapeName);

    ed.setComponents(lastDelayedBomb, new Delay(scheduledMillis, delayedComponents, Delay.SET));
    ed.setComponents(lastDelayedBomb, WeaponTypes.gravityBomb(ed));

    return lastDelayedBomb;
  }

  public static EntityId createBomb(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final Vec3d linearVelocity,
      final long decayMillis,
      final String shapeName) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(shapeName, CorePhysicsConstants.BOMBSIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Mass(5),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
        WeaponTypes.bomb(ed),
        new Impulse(linearVelocity),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(owner));

    ed.setComponent(lastBomb, new Meta(createdTime));
    return lastBomb;
  }

  public static EntityId createBullet(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final Vec3d linearVelocity,
      final long decayMillis,
      final String shapeName) {
    final EntityId lastBullet = ed.createEntity();

    ed.setComponents(
        lastBullet,
        ShapeInfo.create(shapeName, CorePhysicsConstants.BULLETSIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Mass(1),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
        WeaponTypes.bullet(ed),
        new Impulse(linearVelocity),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(owner));

    ed.setComponent(lastBullet, new Meta(createdTime));

    return lastBullet;
  }

  /*
   * public static EntityId createMapTile(String tileSet, short tileIndex, Vec3d
   * pos, Convex c, double invMass, String tileType, EntityData ed, Ini settings,
   * long createdTime, PhysicsSpace phys) { EntityId lastTileInfo =
   * ed.createEntity();
   *
   * ed.setComponents(lastTileInfo, TileType.create(tileType, tileSet, tileIndex,
   * ed), ViewTypes.mapTile(ed), new SpawnPosition(phys.getGrid(), pos),
   * PhysicsMassTypes.infinite(ed), PhysicsShapes.mapTile(c));
   * ed.setComponent(lastTileInfo, new Meta(createdTime));
   *
   * return lastTileInfo; }
   */
  // Explosion is for now only visual, so only object type and position
  public static EntityId createExplosion(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final long decayMillis,
      final ShapeInfo shapeInfo){
    final EntityId lastExplosion = ed.createEntity();

    // Explosion is a ghost
    ed.setComponents(
        lastExplosion,shapeInfo,
        new SpawnPosition(phys.getGrid(), pos),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)));
    ed.setComponent(lastExplosion, new Meta(createdTime));

    return lastExplosion;
  }

  public static EntityId createWormhole(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double force,
      final String gravityType,
      final Vec3d warpTargetLocation,
      final double scale) {
    final EntityId lastWormhole = ed.createEntity();

    // Wormhome is also a ghost
    ed.setComponents(
        lastWormhole,
        ShapeInfo.create(ShapeNames.WORMHOLE, scale, ed),
        new Mass(0),
        new SpawnPosition(phys.getGrid(), pos),
        new GravityWell(scale, force, gravityType));
    ed.setComponent(lastWormhole, new Meta(createdTime));
    ed.setComponent(
        lastWormhole, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_WORMHOLES));

    // Create a touch sensor for the wormhole that will warp the entities that touch it
    final EntityId warpTouch = ed.createEntity();
    ed.setComponent(warpTouch, new WarpTouch(warpTargetLocation));
    ed.setComponent(warpTouch, new Parent(lastWormhole));
    ed.setComponent(warpTouch, new Meta(createdTime));
    ed.setComponent(warpTouch, new Mass(0));
    ed.setComponent(warpTouch, new SpawnPosition(phys.getGrid(), pos));
    ed.setComponent(warpTouch, ShapeInfo.create(ShapeNames.WARP, 0.1, ed));
    ed.setComponent(warpTouch, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_WORMHOLES));

    return lastWormhole;
  }

  public static EntityId createDoor(
      final EntityData ed,
      EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final long intervalTime,
      final Vec3d pos) {
    final EntityId lastDoor = ed.createEntity();
    ed.setComponents(lastDoor, new SpawnPosition(phys.getGrid(), pos), new Mass(0), new Door());
    ed.setComponent(lastDoor, new Meta(createdTime));
    // If owner is not null, then this door is a child of the owner
    if (owner != null) {
      ed.setComponent(lastDoor, new Parent(owner));
    }
    // ed.setComponent(lastDoor, ShapeInfo.create(ShapeNames.DOOR, CorePhysicsConstants.DOORWIDTH,
    // ed));
    ed.setComponent(lastDoor, new Door(createdTime, intervalTime));

    return lastDoor;
  }

  public static EntityId createWormhole2(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos
      // , final double force,
      //                                   final double gravityRadius, final String gravityType
      ) {
    final EntityId lastOver5 = ed.createEntity();

    ed.setComponents(
        lastOver5,
        ShapeInfo.create(ShapeNames.OVER5, CorePhysicsConstants.OVER5SIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos)
        // , new GravityWell(gravityRadius, force, gravityType)
        );
    ed.setComponent(lastOver5, new Meta(createdTime));

    return lastOver5;
  }

  /**
   * Small asteroid with animation.
   *
   * @param ed the entitydata set to create the entity in
   * @return the entityid of the created entity
   */
  public static EntityId createAsteroidSmall(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double mass) {
    final EntityId lastOver1 = ed.createEntity();

    ed.setComponents(
        lastOver1,
        ShapeInfo.create(ShapeNames.OVER1, CorePhysicsConstants.OVER1SIZERADIUS, ed),
        new Mass(mass),
        new SpawnPosition(phys.getGrid(), pos));
    ed.setComponent(lastOver1, new Meta(createdTime));

    return lastOver1;
  }

  /**
   * Medium asteroid with animation.
   *
   * @param ed the entitydata set to create the entity in
   * @return the entityid of the created entity
   */
  public static EntityId createAsteroidMedium(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double mass) {
    final EntityId lastOver2 = ed.createEntity();

    ed.setComponents(
        lastOver2,
        ShapeInfo.create(ShapeNames.OVER2, CorePhysicsConstants.OVER2SIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Mass(mass));
    ed.setComponent(lastOver2, new Meta(createdTime));

    return lastOver2;
  }

  public static EntityId createWarpEffect(
      final EntityData ed,
      final EntityId parent,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final long decayMillis) {
    final EntityId lastWarpTo = ed.createEntity();

    // Warp is a ghost
    ed.setComponents(
        lastWarpTo,
        ShapeInfo.create(ShapeNames.WARP, 0, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)));
    ed.setComponent(lastWarpTo, new Meta(createdTime));

    if (parent != null) {
      ed.setComponent(lastWarpTo, new Parent(parent));
    }

    return lastWarpTo;
  }

  /**
   * Creates a flag that is stationary and can be picked up by a player. This is used for the
   * initial flag placement. To start off with, the flag does not have a frequency.
   *
   * @param ed the entitydata set to create the entity in
   * @param parent the parent of the flag
   * @param phys the physics space
   * @param createdTime the time the flag was created
   * @param pos the position of the flag
   * @return the entityid of the created entity
   */
  public static EntityId createTurfStationaryFlag(
      final EntityData ed,
      final EntityId parent,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    final EntityId lastFlag = ed.createEntity();

    ed.setComponents(
        lastFlag,
        ShapeInfo.create(ShapeNames.FLAG, CorePhysicsConstants.FLAGSIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos.add(0.5, 0, 0.5)),
        new Flag());
    ed.setComponent(lastFlag, new Meta(createdTime));
    ed.setComponent(lastFlag, new Mass(0));
    ed.setComponent(lastFlag, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_SENSOR_FLAGS));

    if (parent != null) {
      ed.setComponent(lastFlag, new Parent(parent));
    }

    return lastFlag;
  }

  /**
   * @deprecated World lighting is now baked via MOSS cell lightData / the
   *     {@code LIGHT_EMITTER_BLOCK_TYPE} block type. This method still creates a
   *     point-light ECS entity with {@link PointLightComponent} + {@link SpawnPosition}
   *     for callers that want dynamic jME-side lights (e.g. dev tooling), but the
   *     world tile shader no longer reads jME lights.
   */
  @Deprecated
  public static EntityId createLight(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    final EntityId lastLight = ed.createEntity();
    ed.setComponents(lastLight,
        new SpawnPosition(phys.getGrid(), pos),
        new PointLightComponent(ColorRGBA.White, CoreViewConstants.SHIPLIGHTRADIUS,
            Vec3d.ZERO),
        new Meta(createdTime));
    return lastLight;
  }

  public static EntityId createShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      byte ship) {
    final EntityId result = ed.createEntity();

    ed.setComponent(result, new Parent(owner));
    ed.setComponent(result, new ShipType(Ship.getShip(ship)));

    ed.setComponent(result, ShapeNames.createShip(ship, ed));

    SpawnPosition sp = new SpawnPosition(phys.getGrid(), spawnLoc);
    ed.setComponent(result, sp);

    Mass m = new Mass(1);
    ed.setComponent(result, m);

    Gravity g = Gravity.ZERO;
    ed.setComponent(result, g);

    ed.setComponent(result, new Gold(0));

    // All tunable per-ship stats (Energy/Health/Recharge/Thrust/Speed/Rotation
    // movement triples, drag/turn/bounce feel, radar range, and the
    // bomb/gun/mine/burst/thor/repel weapon + inventory groups) are projected
    // by ShipSpawnSystem from the per-arena ShipConfig — see Pattern 4 in
    // .claude/rules/config-pattern.md and CONTEXT.md. createShip composes the
    // structural pieces only (Parent, ShipType, ShapeNames, SpawnPosition,
    // Mass, Gravity, Gold, CollisionCategory, CollidesWithLargeStatics, Meta);
    // the spawn system writes the tunable components on the next tick when
    // the ship enters its arena.

    ed.setComponent(
        result, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS));

    // Opt in to the coarse large-static contact pass — ships are the only dynamic
    // bodies whose pairs ArenaMembershipSystem actually cares about. Other
    // dynamics (projectiles, sensor probes) deliberately stay opted out.
    ed.setComponent(result, new CollidesWithLargeStatics());

    ed.setComponent(result, new Meta(createdTime));
    return result;
  }

  public static EntityId createPlayerShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      byte ship) {

    EntityId result = createShip(spawnLoc, ed, owner, phys, createdTime, ship);

    ed.setComponent(result, new Player());
    ed.setComponent(result, new Name("player"));

    ed.setComponent(result, new Frequency(1));

    ed.setComponent(
        result,
        new PointLightComponent(
            new ColorRGBA(3.5f, 3.5f, 3.5f, 1.0f),
            CoreViewConstants.SHIPLIGHTRADIUS,
            CoreViewConstants.SHIPLIGHTOFFSET));

    byte flags = 0x0;
    ed.setComponent(result, new MovementInput(new Vec3d(), new Quatd(), flags));

    return result;

  }

  public static EntityId createPrize(
      final EntityData ed,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final String prizeType) {
    return createPrize(ed, phys, createdTime, pos, prizeType, CoreGameConstants.PRIZEDECAY);
  }

  /**
   * Like {@link #createPrize(EntityData, PhysicsSpace, long, Vec3d, String)},
   * but with a per-prize lifetime override. Used by {@code PrizeSystem} when
   * the source spawner carries a {@code PrizeDecayMillis} component populated
   * from {@code PrizeSpawnerSpec.ttlMillis}. The shorter signature delegates
   * here with the global {@code CoreGameConstants.PRIZEDECAY} default.
   *
   * @param decayMillis prize lifetime; non-positive values are clamped up to
   *     the global default so a misconfigured Groovy spec can't accidentally
   *     produce zero-decay prizes that vanish on the next tick
   */
  public static EntityId createPrize(
      final EntityData ed,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final String prizeType,
      final long decayMillis) {
    final EntityId result = ed.createEntity();

    final long effectiveDecay = decayMillis > 0L ? decayMillis : CoreGameConstants.PRIZEDECAY;
    ed.setComponents(
        result,
        ShapeInfo.create(ShapeNames.PRIZE, CorePhysicsConstants.PRIZESIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Bounty(CoreGameConstants.BOUNTYVALUE),
        PrizeType.create(prizeType, ed),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(effectiveDecay, TimeUnit.MILLISECONDS)));

    // Filter and mass goes hand in hand
    ed.setComponent(result, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_PRIZES));
    ed.setComponent(result, new Mass(1));
    ed.setComponent(result, new Gravity(0));
    ed.setComponent(result, new Meta(createdTime));
    return result;
  }

  public static EntityId createWeightedPrizeSpawner(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double spawnInterval,
      final boolean spawnOnRing,
      final double radius) {
    return createWeightedPrizeSpawner(
        ed,
        owner,
        phys,
        createdTime,
        pos,
        spawnInterval,
        spawnOnRing,
        radius,
        CoreGameConstants.PRIZEMAXCOUNT,
        0L,
        Map.of());
  }

  /**
   * Like {@link #createWeightedPrizeSpawner(EntityData, EntityId, PhysicsSpace,
   * long, Vec3d, double, boolean, double)}, but with explicit {@code maxCount}
   * (number of prizes simultaneously alive from this spawner), per-spawner
   * {@code prizeDecayMillis} (lifetime imprinted on each prize this spawner
   * produces, stored on {@link Spawner#getSpawnedDecayMillis()}) and a sparse
   * {@code weightOverrides} map (per-spawner overrides on top of the arena's
   * {@code [PrizeWeight]} defaults — see
   * {@link infinity.es.PrizeWeightsOverride}). Used by {@code ArenaSystem}
   * when materializing the per-arena {@code prizeSpawners} block declared in
   * {@code arena.groovy}.
   *
   * @param maxCount target number of prizes alive at once (the existing
   *     {@code Spawner.maxCount} field). The shorter signature uses
   *     {@code CoreGameConstants.PRIZEMAXCOUNT}.
   * @param prizeDecayMillis per-prize TTL stored in the {@code Spawner}'s
   *     {@code spawnedDecayMillis} field. {@code 0} (or any non-positive
   *     value) means "use the global {@code CoreGameConstants.PRIZEDECAY}".
   * @param weightOverrides per-spawner prize-type weight overrides. Empty map
   *     ({@code Map.of()}) = "no overrides; use arena defaults". When
   *     non-empty, a {@link infinity.es.PrizeWeightsOverride} component is
   *     attached so {@code PrizeSystem} merges these atop the arena defaults
   *     at selection time.
   */
  public static EntityId createWeightedPrizeSpawner(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double spawnInterval,
      final boolean spawnOnRing,
      final double radius,
      final int maxCount,
      final long prizeDecayMillis,
      final Map<String, Integer> weightOverrides) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        // Possible to add model if we want the players to be able to see the spawner
        new SpawnPosition(phys.getGrid(), pos),
        new Spawner(
            maxCount,
            spawnInterval,
            spawnOnRing,
            Spawner.SpawnType.Prizes,
            true,
            prizeDecayMillis),
        new SphereShape(radius));
    if (weightOverrides != null && !weightOverrides.isEmpty()) {
      ed.setComponent(result, new PrizeWeightsOverride(weightOverrides));
    }
    ed.setComponent(result, new Meta(createdTime));
    return result;
  }

  public static EntityId createBurst(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      @SuppressWarnings("unused") final Vec3d linearVelocity,
      final long decayMillis) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        // ViewTypes.burst(ed),
        ShapeInfo.create(ShapeNames.BURST, CorePhysicsConstants.BURSTSIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        // new PhysicsVelocity(new Vec3d(linearVelocity.x, linearVelocity.y)),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
        WeaponTypes.burst(ed),
        // PhysicsMassTypes.normal_bullet(ed),
        // PhysicsShapes.burst(),
        new Parent(owner)
        // new PointLightComponent(level.lightColor, level.lightRadius));
        );
    ed.setComponent(lastBomb, new Meta(createdTime));
    return lastBomb;
  }

  public static EntityId createRepel(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    final EntityId lastWarpTo = ed.createEntity();

    ed.setComponents(
        lastWarpTo,
        ShapeInfo.create(ShapeNames.REPEL, CorePhysicsConstants.REPELRADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Decay(
            createdTime,
            createdTime
                + TimeUnit.NANOSECONDS.convert(
                    CoreViewConstants.REPELDECAY, TimeUnit.MILLISECONDS)),
        new Parent(owner),
        AudioTypes.repel(ed));

    ed.setComponent(lastWarpTo, new Meta(createdTime));
    return lastWarpTo;
  }

  public static EntityId createThor(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      @SuppressWarnings("unused") final Vec3d attackVelocity,
      final long thorDecay) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(ShapeNames.THOR, CorePhysicsConstants.THORSIZERADIUS, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Mass(5),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(thorDecay, TimeUnit.MILLISECONDS)),
        WeaponTypes.thor(ed),
        new Impulse(attackVelocity),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(owner),
        new Thor());

    ed.setComponent(lastBomb, new Meta(createdTime));

    return lastBomb;
  }

  public static EntityId createMine(EntityData ed, EntityId requester, PhysicsSpace physicsSpace, long time, Vec3d location, long minedecay, String mineShape) {
    EntityId lastMine = ed.createEntity();
    ed.setComponents(lastMine,
        ShapeInfo.create(mineShape, CorePhysicsConstants.MINESIZERADIUS, ed),
        new SpawnPosition(physicsSpace.getGrid(), location),
        Decay.duration(time, TimeUnit.NANOSECONDS.convert(minedecay, TimeUnit.MILLISECONDS)),
        WeaponTypes.mine(ed),
        new Parent(requester));
    ed.setComponent(lastMine, new Meta(time));
    return lastMine;
  }
}
