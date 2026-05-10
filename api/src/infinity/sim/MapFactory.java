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
import infinity.config.EngineConfig;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Factory methods for map decoration (doors, wormholes, asteroids, flags,
 * lights, warp effects), prize/spawner entities, and ship-deployed map
 * structures (bricks, decoys, portals — placed by ships but living on
 * the map). Carved out of the legacy {@code GameEntities} grab-bag
 * (arch-review tier 3 finding #9).
 *
 * <p>The "backward-compat overload" idiom — primary takes new param,
 * no-arg form forwards via {@link EngineConfig#DEFAULTS} — is preserved
 * at the per-method level. Module callers use the no-arg form;
 * production server code threads
 * {@code engineConfigSystem.get().*Radius()} through the explicit-radius
 * overload.
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
   * Default {@code maxCount} for the no-arg-cap {@link
   * #createSpawner(EntityData, EntityId, PhysicsSpace, long,
   * Vec3d, double, boolean, double)} overload — the simultaneous-prize cap
   * for spawners that don't take an explicit value.
   */
  public static final int PRIZE_DEFAULT_MAX_COUNT = 10;

  /**
   * Bounty granted on each kill, stamped onto the slain ship's
   * {@link infinity.es.Bounty} component when the prize entity is created.
   * Pattern 4 candidate.
   */
  public static final int BOUNTY_VALUE = 10;

  private MapFactory() {}

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
      final EntityId owner,
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
    ed.setComponent(lastDoor, new Door(createdTime, intervalTime));

    return lastDoor;
  }

  /**
   * OVER5 visual overlay entity at a position. Distinct from {@link #createWormhole} —
   * no gravity, no warp behavior, just a sized animation overlay. Pairs with
   * {@code SISpatialFactory.createOver5} on the client side.
   *
   * <p>Backward-compat overload — uses {@link EngineConfig#DEFAULTS} radius.
   * Module callers use this form; production server code threads
   * {@code engineConfigSystem.get().over5Radius()} through the explicit-radius
   * overload below.
   */
  public static EntityId createOver5(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    return createOver5(
        ed, owner, phys, createdTime, pos, EngineConfig.DEFAULTS.over5Radius());
  }

  /** OVER5 visual overlay entity with explicit radius — see backward-compat overload above. */
  public static EntityId createOver5(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double radius) {
    final EntityId lastOver5 = ed.createEntity();

    ed.setComponents(
        lastOver5,
        ShapeInfo.create(ShapeNames.OVER5, radius, ed),
        new SpawnPosition(phys.getGrid(), pos));
    ed.setComponent(lastOver5, new Meta(createdTime));

    return lastOver5;
  }

  /**
   * Small asteroid with animation.
   *
   * <p>Backward-compat overload — uses {@link EngineConfig#DEFAULTS} radius.
   * Module callers use this form; production server code threads
   * {@code engineConfigSystem.get().over1Radius()} through the explicit-radius
   * overload below.
   *
   * @param ed the entitydata set to create the entity in
   * @return the entityid of the created entity
   */
  public static EntityId createAsteroidSmall(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double mass) {
    return createAsteroidSmall(
        ed, owner, phys, createdTime, pos, mass, EngineConfig.DEFAULTS.over1Radius());
  }

  /** Small asteroid with explicit radius — see backward-compat overload above. */
  public static EntityId createAsteroidSmall(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double mass,
      final double radius) {
    final EntityId lastOver1 = ed.createEntity();

    ed.setComponents(
        lastOver1,
        ShapeInfo.create(ShapeNames.OVER1, radius, ed),
        new Mass(mass),
        new SpawnPosition(phys.getGrid(), pos));
    ed.setComponent(lastOver1, new Meta(createdTime));

    return lastOver1;
  }

  /**
   * Medium asteroid with animation.
   *
   * <p>Backward-compat overload — uses {@link EngineConfig#DEFAULTS} radius.
   * Module callers use this form; production server code threads
   * {@code engineConfigSystem.get().over2Radius()} through the explicit-radius
   * overload below.
   *
   * @param ed the entitydata set to create the entity in
   * @return the entityid of the created entity
   */
  public static EntityId createAsteroidMedium(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double mass) {
    return createAsteroidMedium(
        ed, owner, phys, createdTime, pos, mass, EngineConfig.DEFAULTS.over2Radius());
  }

  /** Medium asteroid with explicit radius — see backward-compat overload above. */
  public static EntityId createAsteroidMedium(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double mass,
      final double radius) {
    final EntityId lastOver2 = ed.createEntity();

    ed.setComponents(
        lastOver2,
        ShapeInfo.create(ShapeNames.OVER2, radius, ed),
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
      final Vec3d pos,
      final double radius) {
    final EntityId lastFlag = ed.createEntity();

    ed.setComponents(
        lastFlag,
        ShapeInfo.create(ShapeNames.FLAG, radius, ed),
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

  /**
   * Create a prize entity at {@code pos}. Called by {@code PrizeSystem} from
   * {@code spawnBounty}; prize-type weighting and per-spawner TTL selection
   * happen there.
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
      final long decayMillis,
      final double radius) {
    return createPrize(ed, phys, createdTime, pos, prizeType, decayMillis, false, radius);
  }

  /**
   * Like {@link #createPrize(EntityData, PhysicsSpace, long, Vec3d, String,
   * long, double)} but with an explicit {@code hidden} flag. When {@code true} the
   * spawned prize gets an {@link infinity.es.Hidden} marker so the client
   * filters it out of rendering; server-side state (collision, pickup,
   * applier dispatch, decay) is unaffected. Used by {@code PrizeSystem} when
   * a spawner is declared {@code hidden: true} in the arena's
   * {@code spawners} block.
   */
  public static EntityId createPrize(
      final EntityData ed,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final String prizeType,
      final long decayMillis,
      final boolean hidden,
      final double radius) {
    final EntityId result = ed.createEntity();

    final long effectiveDecay = decayMillis > 0L ? decayMillis : PRIZE_DEFAULT_DECAY_MS;
    ed.setComponents(
        result,
        ShapeInfo.create(ShapeNames.PRIZE, radius, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Bounty(BOUNTY_VALUE),
        PrizeType.create(prizeType, ed),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(effectiveDecay, TimeUnit.MILLISECONDS)));

    // Filter and mass goes hand in hand
    ed.setComponent(result, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_PRIZES));
    ed.setComponent(result, new Mass(1));
    ed.setComponent(result, new Gravity(0));
    ed.setComponent(result, new Meta(createdTime));
    if (hidden) {
      ed.setComponent(result, new infinity.es.Hidden());
    }
    return result;
  }

  public static EntityId createSpawner(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final double spawnInterval,
      final boolean spawnOnRing,
      final double radius) {
    return createSpawner(
        ed,
        owner,
        phys,
        createdTime,
        pos,
        spawnInterval,
        spawnOnRing,
        radius,
        PRIZE_DEFAULT_MAX_COUNT,
        0L,
        Map.of(),
        0,
        0.0,
        1,
        false);
  }

  /**
   * Like {@link #createSpawner(EntityData, EntityId, PhysicsSpace,
   * long, Vec3d, double, boolean, double)}, but with explicit {@code maxCount}
   * (number of prizes simultaneously alive from this spawner), per-spawner
   * {@code prizeDecayMillis} (lifetime imprinted on each prize this spawner
   * produces, stored on {@link Spawner#getSpawnedDecayMillis()}), a sparse
   * {@code weightOverrides} map (per-spawner overrides on top of the arena's
   * {@code [PrizeWeight]} defaults — see
   * {@link infinity.es.PrizeWeightsOverride}), and the four Slice-8d
   * scaling/visibility knobs ({@code countPerPlayer}, {@code radiusPerPlayer},
   * {@code regenBatch}, {@code hidden}). Used by {@code ArenaSystem} when
   * materializing the per-arena {@code spawners} block declared in
   * {@code arena.groovy}.
   *
   * @param maxCount base number of prizes alive at once. Effective cap is
   *     {@code maxCount + countPerPlayer × playersInArena}.
   * @param prizeDecayMillis per-prize TTL stored in the {@code Spawner}'s
   *     {@code spawnedDecayMillis} field. {@code 0} (or any non-positive
   *     value) means "use the global {@code PRIZE_DEFAULT_DECAY_MS}".
   * @param weightOverrides per-spawner prize-type weight overrides. Empty map
   *     ({@code Map.of()}) = "no overrides; use arena defaults".
   * @param countPerPlayer additive count scaling per active player in the
   *     spawner's arena; {@code 0} disables count scaling.
   * @param radiusPerPlayer additive radius scaling per active player, in
   *     world units; {@code 0.0} disables radius scaling.
   * @param regenBatch number of prizes to spawn per {@code spawnInterval}
   *     when below the effective cap; {@code 1} preserves pre-Slice-8d
   *     cadence.
   * @param hidden when {@code true}, spawned prizes get an
   *     {@link infinity.es.Hidden} marker so the client doesn't render
   *     them.
   */
  public static EntityId createSpawner(
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
      final Map<String, Integer> weightOverrides,
      final int countPerPlayer,
      final double radiusPerPlayer,
      final int regenBatch,
      final boolean hidden) {
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
            prizeDecayMillis,
            countPerPlayer,
            radiusPerPlayer,
            regenBatch,
            hidden),
        new SphereShape(radius));
    if (weightOverrides != null && !weightOverrides.isEmpty()) {
      ed.setComponent(result, new PrizeWeightsOverride(weightOverrides));
    }
    ed.setComponent(result, new Meta(createdTime));
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
      final EntityData ed,
      final EntityId ship,
      final long createdTime,
      final long aliveTimeMs) {
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
      final EntityData ed,
      final EntityId ship,
      final long createdTime,
      final long activeTimeMs) {
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
