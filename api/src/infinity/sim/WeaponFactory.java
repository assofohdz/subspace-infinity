// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.es.AudioTypes;
import infinity.es.CollisionCategory;
import infinity.es.Delay;
import infinity.es.Meta;
import infinity.es.Parent;
import infinity.es.WeaponTypes;
import infinity.es.ship.actions.Thor;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Factory methods for projectile weapons (bombs, bullets, mines, bursts,
 * thors, repels) and weapon-adjacent visual fallout (explosions). Carved
 * out of the legacy {@code GameEntities} grab-bag (arch-review tier 3
 * finding #9).
 *
 * <p>Tuning numbers (decay millis, weapon shape names, radii) flow in via
 * parameters — typically threaded by {@code WeaponsFireSystem} from the
 * per-arena ship/weapon {@code *Config} per Pattern 4. This factory
 * composes structural pieces only (ShapeInfo, SpawnPosition, Mass, Decay,
 * WeaponTypes, Impulse, CollisionCategory, Parent, Meta).
 *
 * @see ShipFactory
 * @see MapFactory
 */
public final class WeaponFactory {

  private WeaponFactory() {}

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
      final Set<EntityComponent> delayedComponents,
      final String shapeName,
      final double radius) {

    final EntityId lastDelayedBomb =
        WeaponFactory.createBomb(
            ed, owner, phys, createdTime, pos, linearVelocity, decayMillis, shapeName, radius);

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
      final String shapeName,
      final double radius) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(shapeName, radius, ed),
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
      final String shapeName,
      final double radius) {
    final EntityId lastBullet = ed.createEntity();

    ed.setComponents(
        lastBullet,
        ShapeInfo.create(shapeName, radius, ed),
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

  // Explosion is for now only visual, so only object type and position
  public static EntityId createExplosion(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final long decayMillis,
      final ShapeInfo shapeInfo) {
    final EntityId lastExplosion = ed.createEntity();

    // Explosion is a ghost
    ed.setComponents(
        lastExplosion, shapeInfo,
        new SpawnPosition(phys.getGrid(), pos),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)));
    ed.setComponent(lastExplosion, new Meta(createdTime));

    return lastExplosion;
  }

  public static EntityId createBurst(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      @SuppressWarnings("unused") final Vec3d linearVelocity,
      final long decayMillis,
      final double radius) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        // ViewTypes.burst(ed),
        ShapeInfo.create(infinity.es.ShapeNames.BURST, radius, ed),
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
      final Vec3d pos,
      final long decayMs,
      final double radius) {
    final EntityId lastWarpTo = ed.createEntity();

    ed.setComponents(
        lastWarpTo,
        ShapeInfo.create(infinity.es.ShapeNames.REPEL, radius, ed),
        new SpawnPosition(phys.getGrid(), pos),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(decayMs, TimeUnit.MILLISECONDS)),
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
      final long thorDecay,
      final double radius) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(infinity.es.ShapeNames.THOR, radius, ed),
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

  public static EntityId createMine(
      final EntityData ed,
      final EntityId requester,
      final PhysicsSpace physicsSpace,
      final long time,
      final Vec3d location,
      final long minedecay,
      final String mineShape,
      final double radius) {
    EntityId lastMine = ed.createEntity();
    ed.setComponents(lastMine,
        ShapeInfo.create(mineShape, radius, ed),
        new SpawnPosition(physicsSpace.getGrid(), location),
        Decay.duration(time, TimeUnit.NANOSECONDS.convert(minedecay, TimeUnit.MILLISECONDS)),
        WeaponTypes.mine(ed),
        new Parent(requester));
    ed.setComponent(lastMine, new Meta(time));
    return lastMine;
  }
}
