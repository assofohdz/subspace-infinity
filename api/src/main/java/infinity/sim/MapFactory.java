// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.jme3.math.ColorRGBA;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.es.Bounty;
import infinity.es.CollisionCategory;
import infinity.es.Door;
import infinity.es.Flag;
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
import infinity.es.ship.actions.BrickSpan;
import infinity.sim.specs.AsteroidSpec;
import infinity.sim.specs.DoorSpec;
import infinity.sim.specs.Over5Spec;
import infinity.sim.specs.PrizeSpec;
import infinity.sim.specs.SpawnerCreateSpec;
import infinity.sim.specs.TurfStationaryFlagSpec;
import infinity.sim.specs.WarpEffectSpec;
import infinity.sim.specs.WormholeSpec;
import java.util.concurrent.TimeUnit;

/** Factory methods for map decoration, prizes/spawners, and ship-deployed map structures (bricks, decoys, portals). @see ShipFactory @see WeaponFactory */
public final class MapFactory {

  /** Default prize lifetime when a spawner doesn't specify its own. */
  public static final long PRIZE_DEFAULT_DECAY_MS = 20000;

  /** Default simultaneous-prize cap for no-cap spawner builders. */
  public static final int PRIZE_DEFAULT_MAX_COUNT = 10;

  /** Bounty granted on each kill. */
  public static final int BOUNTY_VALUE = 10;

  private MapFactory() {}

  public static EntityId createWormhole(final EntityData ed, final WormholeSpec spec) {
    final EntityId lastWormhole = ed.createEntity();

    final PhysicsSpace<?, ?> phys = spec.phys();
    final Vec3d pos = spec.position();
    final long createdTime = spec.createdTime();

    // Wormhome is also a ghost
    ed.setComponents(
        lastWormhole,
        ShapeInfo.create(ShapeNames.WORMHOLE, spec.scale(), ed),
        new Mass(0),
        new SpawnPosition(phys.getGrid(), pos),
        new GravityWell(spec.scale(), spec.force(), spec.gravityType()));
    ed.setComponent(lastWormhole, new Meta(createdTime));
    ed.setComponent(
        lastWormhole, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_WORMHOLES));

    // Create a touch sensor for the wormhole that will warp the entities that touch it
    final EntityId warpTouch = ed.createEntity();
    ed.setComponent(warpTouch, new WarpTouch(spec.warpTargetLocation()));
    ed.setComponent(warpTouch, new Parent(lastWormhole));
    ed.setComponent(warpTouch, new Meta(createdTime));
    ed.setComponent(warpTouch, new Mass(0));
    ed.setComponent(warpTouch, new SpawnPosition(phys.getGrid(), pos));
    ed.setComponent(warpTouch, ShapeInfo.create(ShapeNames.WARP, 0.1, ed));
    ed.setComponent(warpTouch, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_WORMHOLES));

    return lastWormhole;
  }

  public static EntityId createDoor(final EntityData ed, final DoorSpec spec) {
    final EntityId lastDoor = ed.createEntity();
    ed.setComponents(
        lastDoor, new SpawnPosition(spec.phys().getGrid(), spec.position()), new Mass(0), new Door());
    ed.setComponent(lastDoor, new Meta(spec.createdTime()));
    // If owner is not null, then this door is a child of the owner
    if (spec.owner() != null) {
      ed.setComponent(lastDoor, new Parent(spec.owner()));
    }
    ed.setComponent(lastDoor, new Door(spec.createdTime(), spec.intervalTime()));

    return lastDoor;
  }

  /** OVER5 visual overlay entity; no gravity, no warp — just a sized animation overlay. */
  public static EntityId createOver5(final EntityData ed, final Over5Spec spec) {
    final EntityId lastOver5 = ed.createEntity();

    ed.setComponents(
        lastOver5,
        ShapeInfo.create(ShapeNames.OVER5, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()));
    ed.setComponent(lastOver5, new Meta(spec.createdTime()));

    return lastOver5;
  }

  /** Small asteroid with animation. */
  public static EntityId createAsteroidSmall(final EntityData ed, final AsteroidSpec spec) {
    final EntityId lastOver1 = ed.createEntity();

    ed.setComponents(
        lastOver1,
        ShapeInfo.create(ShapeNames.OVER1, spec.radius(), ed),
        new Mass(spec.mass()),
        new SpawnPosition(spec.phys().getGrid(), spec.position()));
    ed.setComponent(lastOver1, new Meta(spec.createdTime()));

    return lastOver1;
  }

  /** Medium asteroid with animation. */
  public static EntityId createAsteroidMedium(final EntityData ed, final AsteroidSpec spec) {
    final EntityId lastOver2 = ed.createEntity();

    ed.setComponents(
        lastOver2,
        ShapeInfo.create(ShapeNames.OVER2, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Mass(spec.mass()));
    ed.setComponent(lastOver2, new Meta(spec.createdTime()));

    return lastOver2;
  }

  public static EntityId createWarpEffect(final EntityData ed, final WarpEffectSpec spec) {
    final EntityId lastWarpTo = ed.createEntity();

    // Warp is a ghost
    ed.setComponents(
        lastWarpTo,
        ShapeInfo.create(ShapeNames.WARP, 0, ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)));
    ed.setComponent(lastWarpTo, new Meta(spec.createdTime()));

    if (spec.parent() != null) {
      ed.setComponent(lastWarpTo, new Parent(spec.parent()));
    }

    return lastWarpTo;
  }

  /** Creates a stationary, frequency-less flag for initial flag placement. */
  public static EntityId createTurfStationaryFlag(
      final EntityData ed, final TurfStationaryFlagSpec spec) {
    final EntityId lastFlag = ed.createEntity();

    ed.setComponents(
        lastFlag,
        ShapeInfo.create(ShapeNames.FLAG, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position().add(0.5, 0, 0.5)),
        new Flag());
    ed.setComponent(lastFlag, new Meta(spec.createdTime()));
    ed.setComponent(lastFlag, new Mass(0));
    ed.setComponent(lastFlag, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_SENSOR_FLAGS));

    if (spec.parent() != null) {
      ed.setComponent(lastFlag, new Parent(spec.parent()));
    }

    return lastFlag;
  }

  /** @deprecated World lighting is now baked via MOSS cell lightData; only dev tooling should use this dynamic jME-side light. */
  @Deprecated
  public static EntityId createLight(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    final EntityId lastLight = ed.createEntity();
    ed.setComponents(
        lastLight,
        new SpawnPosition(phys.getGrid(), pos),
        new PointLightComponent(ColorRGBA.White, CoreViewConstants.SHIPLIGHTRADIUS, Vec3d.ZERO),
        new Meta(createdTime));
    return lastLight;
  }

  /** Create a prize entity; non-positive {@code decayMillis} on the spec clamps up to {@link #PRIZE_DEFAULT_DECAY_MS}. */
  public static EntityId createPrize(final EntityData ed, final PrizeSpec spec) {
    final EntityId result = ed.createEntity();

    final long effectiveDecay =
        spec.decayMillis() > 0L ? spec.decayMillis() : PRIZE_DEFAULT_DECAY_MS;
    ed.setComponents(
        result,
        ShapeInfo.create(ShapeNames.PRIZE, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Bounty(BOUNTY_VALUE),
        PrizeType.create(spec.prizeType(), ed),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(effectiveDecay, TimeUnit.MILLISECONDS)));

    // Filter and mass goes hand in hand
    ed.setComponent(result, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_PRIZES));
    ed.setComponent(result, new Mass(1));
    ed.setComponent(result, new Gravity(0));
    ed.setComponent(result, new Meta(spec.createdTime()));
    if (spec.hidden()) {
      ed.setComponent(result, new infinity.es.Hidden());
    }
    return result;
  }

  /** Create a prize-spawner entity from {@link SpawnerCreateSpec}; effective cap is {@code maxCount + countPerPlayer × N}. */
  public static EntityId createSpawner(final EntityData ed, final SpawnerCreateSpec spec) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        // Possible to add model if we want the players to be able to see the spawner
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Spawner(
            spec.maxCount(),
            spec.spawnInterval(),
            spec.spawnOnRing(),
            Spawner.SpawnType.PRIZES,
            true,
            spec.prizeDecayMillis(),
            spec.countPerPlayer(),
            spec.radiusPerPlayer(),
            spec.regenBatch(),
            spec.hidden()),
        new SphereShape(spec.radius()));
    if (spec.weightOverrides() != null && !spec.weightOverrides().isEmpty()) {
      ed.setComponent(result, new PrizeWeightsOverride(spec.weightOverrides()));
    }
    ed.setComponent(result, new Meta(spec.createdTime()));
    return result;
  }

  /** Marker entity for a placed brick; {@link Decay} owns lifetime, {@link BrickSpan} carries wall length. */
  public static EntityId createBrick(
      final EntityData ed,
      final EntityId ship,
      final long createdTime,
      final int spanTiles,
      final long timeMs) {
    final EntityId brick = ed.createEntity();
    ed.setComponents(
        brick,
        new Parent(ship),
        new BrickSpan(spanTiles),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(timeMs, TimeUnit.MILLISECONDS)),
        new Meta(createdTime));
    return brick;
  }

  /** Marker entity for a placed decoy; {@link Decay} owns lifetime. */
  public static EntityId createDecoy(
      final EntityData ed, final EntityId ship, final long createdTime, final long aliveTimeMs) {
    final EntityId decoy = ed.createEntity();
    ed.setComponents(
        decoy,
        new Parent(ship),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(aliveTimeMs, TimeUnit.MILLISECONDS)),
        new Meta(createdTime));
    return decoy;
  }

  /** Marker entity for a placed portal; {@link Decay} owns lifetime. */
  public static EntityId createPortal(
      final EntityData ed, final EntityId ship, final long createdTime, final long activeTimeMs) {
    final EntityId portal = ed.createEntity();
    ed.setComponents(
        portal,
        new Parent(ship),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(activeTimeMs, TimeUnit.MILLISECONDS)),
        new Meta(createdTime));
    return portal;
  }
}
