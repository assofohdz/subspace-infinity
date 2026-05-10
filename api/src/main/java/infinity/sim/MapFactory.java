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

/**
 * Factory methods for map decoration (doors, wormholes, asteroids, flags,
 * lights, warp effects), prize/spawner entities, and ship-deployed map
 * structures (bricks, decoys, portals — placed by ships but living on
 * the map). Carved out of the legacy {@code GameEntities} grab-bag
 * (arch-review tier 3 finding #9).
 *
 * <p>Per-call inputs flow through parameter records under
 * {@code infinity.sim.specs.*Spec} (BACKLOG #2). Production server code
 * threads {@code engineConfigSystem.get().*Radius()} into the spec;
 * module / test callers can use {@code EngineConfig.DEFAULTS.*Radius()}.
 *
 * @see ShipFactory
 * @see WeaponFactory
 */
public final class MapFactory {

  /**
   * Default lifetime for prizes when a spawner doesn't specify its own.
   * Per-spawner {@code prizeDecayMillis} on {@link infinity.es.Spawner}
   * overrides this. Pattern 4 candidate — promote to per-arena typed
   * config when a real per-arena requirement materializes.
   */
  public static final long PRIZE_DEFAULT_DECAY_MS = 20000;

  /**
   * Default {@code maxCount} for the no-cap {@link
   * #createSpawner(EntityData, SpawnerCreateSpec)} convenience builders —
   * the simultaneous-prize cap when callers don't supply an explicit value.
   */
  public static final int PRIZE_DEFAULT_MAX_COUNT = 10;

  /**
   * Bounty granted on each kill, stamped onto the slain ship's
   * {@link infinity.es.Bounty} component when the prize entity is created.
   * Pattern 4 candidate.
   */
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

  /**
   * OVER5 visual overlay entity at a position. Distinct from {@link #createWormhole} —
   * no gravity, no warp behavior, just a sized animation overlay. Pairs with
   * {@code SISpatialFactory.createOver5} on the client side.
   */
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

  /**
   * Creates a flag that is stationary and can be picked up by a player. This is used for the
   * initial flag placement. To start off with, the flag does not have a frequency.
   */
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
    ed.setComponents(
        lastLight,
        new SpawnPosition(phys.getGrid(), pos),
        new PointLightComponent(ColorRGBA.White, CoreViewConstants.SHIPLIGHTRADIUS, Vec3d.ZERO),
        new Meta(createdTime));
    return lastLight;
  }

  /**
   * Create a prize entity at the spec's position. Called by {@code PrizeSystem}
   * from {@code spawnBounty} and the death-drop path; prize-type weighting and
   * per-spawner TTL selection happen there.
   *
   * <p>Non-positive {@code decayMillis} on the spec are clamped up to the
   * global default {@link #PRIZE_DEFAULT_DECAY_MS} so a misconfigured Groovy
   * spec can't accidentally produce zero-decay prizes that vanish on the
   * next tick. The spec carries the {@code hidden} flag (set {@code false}
   * for visible prizes); when {@code true} the spawned prize gets an
   * {@link infinity.es.Hidden} marker so the client filters it out of
   * rendering — server-side state (collision, pickup, applier dispatch,
   * decay) is unaffected.
   */
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

  /**
   * Create a prize-spawner entity from {@link SpawnerCreateSpec}. Used by
   * {@code ArenaSystem} when materializing the per-arena {@code spawners}
   * block declared in {@code arena.groovy}, and by dev/debug entry points
   * (e.g. {@code BasicEnvironment}) that build a no-cap, defaulted spawner.
   *
   * <p>Effective per-tick cap is {@code spec.maxCount() + countPerPlayer ×
   * playersInArena}; see {@link SpawnerCreateSpec} for the full field
   * documentation including Slice-8d scaling/visibility knobs.
   */
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
            Spawner.SpawnType.Prizes,
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

  /**
   * Compose the marker entity for a placed brick. Lifecycle is owned by
   * {@link Decay}: when the deadline passes, the canonical decay reaper
   * deletes the entity.
   *
   * <p>Slice 3 ships plumbing only — the marker carries the span +
   * decay deadline but no shape, no contact handler, no client visual.
   * The follow-up "make bricks solid" slice consumes {@link BrickSpan}
   * to spawn the per-tile wall geometry, registers a brick collision
   * filter, and adds the client visual. This factory is the seam
   * those consumers will read from.
   *
   * @param ship parent ship that placed the brick
   * @param createdTime spawn time in ns (matches {@link com.simsilica.sim.SimTime#getTime})
   * @param spanTiles wall length in tiles (from {@code BrickConfig.spanTiles})
   * @param timeMs brick lifetime in ms (from {@code BrickConfig.timeMs})
   */
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

  /**
   * Compose the marker entity for a placed decoy. Lifecycle is owned by
   * {@link Decay}: when the deadline passes, the canonical decay reaper
   * deletes the entity.
   *
   * <p>Slice 4 ships plumbing only — the marker carries the decay
   * deadline + parent linkage but no shape, no radar visibility, no
   * fake-ship behaviour. The follow-up "decoy as radar fake" slice will
   * extend this factory with the canonical Subspace mechanic (a phantom
   * ship on enemy radar that mimics the placer's heading).
   *
   * @param ship parent ship that placed the decoy
   * @param createdTime spawn time in ns (matches {@link com.simsilica.sim.SimTime#getTime})
   * @param aliveTimeMs decoy lifetime in ms (from {@code DecoyConfig.aliveTimeMs})
   */
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

  /**
   * Compose the marker entity for a placed portal. Lifecycle is owned by
   * {@link Decay}: when the deadline passes, the canonical decay reaper
   * deletes the entity.
   *
   * <p>Slice 5 ships plumbing only — the marker carries the decay
   * deadline + parent linkage but no shape, no contact handler, no
   * client visual, no warp-to-portal action. The follow-up "warp to
   * placed portal" slice consumes this marker entity to drive the
   * canonical Subspace mechanic (ship within {@code WarpRadiusLimit} of
   * its own portal can teleport to it).
   *
   * @param ship parent ship that placed the portal
   * @param createdTime spawn time in ns (matches {@link com.simsilica.sim.SimTime#getTime})
   * @param activeTimeMs portal lifetime in ms (from {@code PortalConfig.activeTimeMs})
   */
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
