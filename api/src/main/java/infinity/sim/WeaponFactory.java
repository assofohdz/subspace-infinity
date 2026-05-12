// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import infinity.es.AudioTypes;
import infinity.es.CollisionCategory;
import infinity.es.Delay;
import infinity.es.Meta;
import infinity.es.Parent;
import infinity.es.WeaponTypes;
import infinity.es.ship.actions.Thor;
import infinity.sim.specs.BombSpec;
import infinity.sim.specs.BulletSpec;
import infinity.sim.specs.BurstSpec;
import infinity.sim.specs.DelayedBombSpec;
import infinity.sim.specs.ExplosionSpec;
import infinity.sim.specs.MineSpec;
import infinity.sim.specs.RepelSpec;
import infinity.sim.specs.ThorSpec;
import java.util.concurrent.TimeUnit;

/** Factory methods for projectile weapons + explosion ghosts; structural composition only — tuning flows via spec records. @see ShipFactory @see MapFactory */
public final class WeaponFactory {

  private WeaponFactory() {}

  public static EntityId createDelayedBomb(final EntityData ed, final DelayedBombSpec spec) {
    final EntityId lastDelayedBomb =
        WeaponFactory.createBomb(
            ed,
            new BombSpec(
                spec.owner(),
                spec.phys(),
                spec.createdTime(),
                spec.position(),
                spec.linearVelocity(),
                spec.decayMillis(),
                spec.shapeName(),
                spec.radius()));

    ed.setComponents(
        lastDelayedBomb, new Delay(spec.scheduledMillis(), spec.delayedComponents(), Delay.SET));
    ed.setComponents(lastDelayedBomb, WeaponTypes.gravityBomb(ed));

    return lastDelayedBomb;
  }

  public static EntityId createBomb(final EntityData ed, final BombSpec spec) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(spec.shapeName(), spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Mass(5),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponTypes.bomb(ed),
        new Impulse(spec.linearVelocity()),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(spec.owner()));

    ed.setComponent(lastBomb, new Meta(spec.createdTime()));
    return lastBomb;
  }

  public static EntityId createBullet(final EntityData ed, final BulletSpec spec) {
    final EntityId lastBullet = ed.createEntity();

    ed.setComponents(
        lastBullet,
        ShapeInfo.create(spec.shapeName(), spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Mass(1),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponTypes.bullet(ed),
        new Impulse(spec.linearVelocity()),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(spec.owner()));

    ed.setComponent(lastBullet, new Meta(spec.createdTime()));

    return lastBullet;
  }

  // Explosion is for now only visual, so only object type and position
  public static EntityId createExplosion(final EntityData ed, final ExplosionSpec spec) {
    final EntityId lastExplosion = ed.createEntity();

    // Explosion is a ghost
    ed.setComponents(
        lastExplosion,
        spec.shapeInfo(),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)));
    ed.setComponent(lastExplosion, new Meta(spec.createdTime()));

    return lastExplosion;
  }

  public static EntityId createBurst(final EntityData ed, final BurstSpec spec) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        // ViewTypes.burst(ed),
        ShapeInfo.create(infinity.es.ShapeNames.BURST, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        // new PhysicsVelocity(new Vec3d(linearVelocity.x, linearVelocity.y)),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponTypes.burst(ed),
        // PhysicsMassTypes.normal_bullet(ed),
        // PhysicsShapes.burst(),
        new Parent(spec.owner())
        // new PointLightComponent(level.lightColor, level.lightRadius));
        );
    ed.setComponent(lastBomb, new Meta(spec.createdTime()));
    return lastBomb;
  }

  public static EntityId createRepel(final EntityData ed, final RepelSpec spec) {
    final EntityId lastWarpTo = ed.createEntity();

    ed.setComponents(
        lastWarpTo,
        ShapeInfo.create(infinity.es.ShapeNames.REPEL, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        new Parent(spec.owner()),
        AudioTypes.repel(ed));

    ed.setComponent(lastWarpTo, new Meta(spec.createdTime()));
    return lastWarpTo;
  }

  public static EntityId createThor(final EntityData ed, final ThorSpec spec) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(infinity.es.ShapeNames.THOR, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Mass(5),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponTypes.thor(ed),
        new Impulse(spec.attackVelocity()),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(spec.owner()),
        new Thor());

    ed.setComponent(lastBomb, new Meta(spec.createdTime()));

    return lastBomb;
  }

  public static EntityId createMine(final EntityData ed, final MineSpec spec) {
    EntityId lastMine = ed.createEntity();
    ed.setComponents(
        lastMine,
        ShapeInfo.create(spec.shapeName(), spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        Decay.duration(
            spec.createdTime(), TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponTypes.mine(ed),
        new Parent(spec.owner()));
    ed.setComponent(lastMine, new Meta(spec.createdTime()));
    return lastMine;
  }
}
