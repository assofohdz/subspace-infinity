// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.es.AudioType;
import infinity.es.AudioTypes;
import infinity.es.Meta;
import infinity.es.Parent;
import infinity.BombLevel;
import infinity.BulletLevel;
import javax.annotation.Nonnull;

import java.util.concurrent.TimeUnit;

/**
 * Helpers for spawning short-lived audio entities.
 *
 * @author AFahrenholz
 */
public class GameSounds {

  private GameSounds() { /* utility class */ }

  //Create a sound for the flag type
  public static void createFlagSound(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioType.create(AudioTypes.FLAG, ed),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)),
        new SpawnPosition(phys.getGrid(), pos),
        new Parent(owner));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createBombSound(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final BombLevel level) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioTypes.fireBomb(ed, level),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)),
        new SpawnPosition(phys.getGrid(), pos),
        new Parent(owner));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createExplosionSound(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioType.create(AudioTypes.EXPLOSION2, ed),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)),
        new SpawnPosition(phys.getGrid(), pos),
        new Parent(owner));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static EntityId createSound(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final String audioType) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioType.create(audioType, ed),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)),
        new SpawnPosition(phys.getGrid(), pos),
        new Parent(owner));

    ed.setComponent(result, new Meta(createdTime));
    return result;
  }

  public static void createBulletSound(
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final Vec3d pos,
      final BulletLevel level) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioTypes.fireBullet(ed, level),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)),
        new SpawnPosition(phys.getGrid(), pos),
        new Parent(owner));

    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createBurstSound(
      final EntityData ed,
      @SuppressWarnings("unused") final EntityId owner,
      @SuppressWarnings("unused") final PhysicsSpace<?, ?> phys,
      final long createdTime,
      @SuppressWarnings("unused") final Vec3d pos) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioTypes.fireBurst(ed),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createPrizeSound(
      final @Nonnull EntityData ed,
      final long createdTime,
      final EntityId parent,
      final Vec3d loc,
      @Nonnull final PhysicsSpace phys) {
    final EntityId result = ed.createEntity();
    ed.setComponents(
        result,
        AudioTypes.pickupPrize(ed),
        new Parent(parent),
        new SpawnPosition(phys.getGrid(), loc),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createThorSound(
      final @Nonnull EntityData ed,
      final long createdTime,
      final EntityId parent,
      final Vec3d loc,
      @Nonnull final PhysicsSpace phys) {
    final EntityId result = ed.createEntity();
    ed.setComponents(
        result,
        AudioTypes.fireThor(ed),
        new Parent(parent),
        new SpawnPosition(phys.getGrid(), loc),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createMineSound(
      final EntityData ed,
      final EntityId requester,
      @Nonnull final PhysicsSpace phys,
      final long time,
      final Vec3d location,
      final BombLevel level) {
    final EntityId result = ed.createEntity();
    ed.setComponents(
        result,
        AudioTypes.fireMine(ed, level),
        new Parent(requester),
        new SpawnPosition(phys.getGrid(), location),
        new Decay(time, time + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(time));
  }
}
