// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.jme3.math.ColorRGBA;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.Impulse;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import infinity.es.AudioType;
import infinity.es.AudioTypes;
import infinity.es.CollisionCategory;
import infinity.es.Delay;
import infinity.es.Meta;
import infinity.es.Parent;
import infinity.es.PointLightComponent;
import infinity.es.WeaponType;
import infinity.es.WeaponTypes;
import infinity.es.ship.actions.Thor;
import infinity.sim.specs.BombArgs;
import infinity.sim.specs.BulletArgs;
import infinity.sim.specs.BurstArgs;
import infinity.sim.specs.DelayedBombArgs;
import infinity.sim.specs.ExplosionArgs;
import infinity.sim.specs.MineArgs;
import infinity.sim.specs.RepelArgs;
import infinity.sim.specs.ThorArgs;
import java.util.concurrent.TimeUnit;

/** Factory methods for projectile weapons + explosion ghosts; structural composition only — tuning flows via spec records. @see ShipFactory @see MapFactory */
public final class WeaponFactory {

  private WeaponFactory() {}

  // Projectile PointLight colours + radii — tuned for readability in dim arenas.
  // Bombs / bullets / mines etc. all emit a short-range light that LightState picks up via the
  // PointLightComponent + BodyPosition filter; Decay automatically removes the light when the
  // projectile expires. Tune here to adjust the visual signature of each weapon type.
  private static final ColorRGBA BOMB_LIGHT = new ColorRGBA(1.0f, 0.45f, 0.2f, 1.0f);     // warm red — incoming
  private static final ColorRGBA BULLET_LIGHT = new ColorRGBA(1.0f, 0.95f, 0.6f, 1.0f);   // pale yellow — fast/many
  private static final ColorRGBA BURST_LIGHT = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);     // white — brief shrapnel
  private static final ColorRGBA THOR_LIGHT = new ColorRGBA(0.85f, 0.4f, 1.0f, 1.0f);     // violet — heavy weapon signal
  private static final ColorRGBA MINE_LIGHT = new ColorRGBA(0.25f, 0.5f, 1.0f, 1.0f);     // deep blue — static threat
  private static final ColorRGBA EXPLOSION_LIGHT = new ColorRGBA(1.0f, 0.65f, 0.25f, 1.0f); // bright orange — flash
  private static final ColorRGBA REPEL_LIGHT = new ColorRGBA(0.4f, 0.9f, 1.0f, 1.0f);     // cyan — push effect
  private static final float BOMB_LIGHT_RADIUS = 24f;
  private static final float BULLET_LIGHT_RADIUS = 8f;
  private static final float BURST_LIGHT_RADIUS = 12f;
  private static final float THOR_LIGHT_RADIUS = 40f;
  private static final float MINE_LIGHT_RADIUS = 16f;
  private static final float EXPLOSION_LIGHT_RADIUS = 48f;
  private static final float REPEL_LIGHT_RADIUS = 24f;

  public static EntityId createDelayedBomb(final EntityData ed, final DelayedBombArgs spec) {
    final EntityId lastDelayedBomb =
        WeaponFactory.createBomb(
            ed,
            new BombArgs(
                spec.owner(),
                spec.phys(),
                spec.createdTime(),
                spec.position(),
                spec.linearVelocity(),
                spec.decayMillis(),
                spec.shapeName(),
                spec.radius()));

    ed.setComponents(
        lastDelayedBomb,
        Delay.duration(
            spec.createdTime(), spec.scheduledMillis(), spec.delayedComponents(), Delay.SET));
    ed.setComponents(lastDelayedBomb, WeaponType.create(WeaponTypes.GRAVITYBOMB, ed));

    return lastDelayedBomb;
  }

  public static EntityId createBomb(final EntityData ed, final BombArgs spec) {
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
        WeaponType.create(WeaponTypes.BOMB, ed),
        new Impulse(spec.linearVelocity()),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(spec.owner()));

    ed.setComponent(lastBomb, new PointLightComponent(BOMB_LIGHT, BOMB_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastBomb, new Meta(spec.createdTime()));
    return lastBomb;
  }

  public static EntityId createBullet(final EntityData ed, final BulletArgs spec) {
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
        WeaponType.create(WeaponTypes.BULLET, ed),
        new Impulse(spec.linearVelocity()),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(spec.owner()));

    ed.setComponent(lastBullet, new PointLightComponent(BULLET_LIGHT, BULLET_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastBullet, new Meta(spec.createdTime()));

    return lastBullet;
  }

  // Explosion is for now only visual, so only object type and position
  public static EntityId createExplosion(final EntityData ed, final ExplosionArgs spec) {
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
    ed.setComponent(lastExplosion, new PointLightComponent(EXPLOSION_LIGHT, EXPLOSION_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastExplosion, new Meta(spec.createdTime()));

    return lastExplosion;
  }

  public static EntityId createBurst(final EntityData ed, final BurstArgs spec) {
    final EntityId lastBomb = ed.createEntity();

    ed.setComponents(
        lastBomb,
        ShapeInfo.create(infinity.es.ShapeNames.BURST, spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponType.create(WeaponTypes.BURST, ed),
        new Parent(spec.owner()));
    ed.setComponent(lastBomb, new PointLightComponent(BURST_LIGHT, BURST_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastBomb, new Meta(spec.createdTime()));
    return lastBomb;
  }

  public static EntityId createRepel(final EntityData ed, final RepelArgs spec) {
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
        AudioType.create(AudioTypes.REPEL, ed));

    ed.setComponent(lastWarpTo, new PointLightComponent(REPEL_LIGHT, REPEL_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastWarpTo, new Meta(spec.createdTime()));
    return lastWarpTo;
  }

  public static EntityId createThor(final EntityData ed, final ThorArgs spec) {
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
        WeaponType.create(WeaponTypes.THOR, ed),
        new Impulse(spec.attackVelocity()),
        new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES),
        new Parent(spec.owner()),
        new Thor());

    ed.setComponent(lastBomb, new PointLightComponent(THOR_LIGHT, THOR_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastBomb, new Meta(spec.createdTime()));

    return lastBomb;
  }

  public static EntityId createMine(final EntityData ed, final MineArgs spec) {
    EntityId lastMine = ed.createEntity();
    ed.setComponents(
        lastMine,
        ShapeInfo.create(spec.shapeName(), spec.radius(), ed),
        new SpawnPosition(spec.phys().getGrid(), spec.position()),
        Decay.duration(
            spec.createdTime(), TimeUnit.NANOSECONDS.convert(spec.decayMillis(), TimeUnit.MILLISECONDS)),
        WeaponType.create(WeaponTypes.MINE, ed),
        new Parent(spec.owner()));
    ed.setComponent(lastMine, new PointLightComponent(MINE_LIGHT, MINE_LIGHT_RADIUS, Vec3d.ZERO));
    ed.setComponent(lastMine, new Meta(spec.createdTime()));
    return lastMine;
  }
}
