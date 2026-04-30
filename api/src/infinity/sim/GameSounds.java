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
import infinity.Bombs;
import infinity.Guns;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Helpers for spawning short-lived audio entities.
 *
 * @author AFahrenholz
 */
public class GameSounds {

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
      final Bombs level) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioTypes.fire_bomb(ed, level),
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
        AudioTypes.explosion2(ed),
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
      final Guns level) {
    final EntityId result = ed.createEntity();

    ed.setComponents(
        result,
        AudioTypes.fire_bullet(ed, level),
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
        AudioTypes.fire_burst(ed),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createPrizeSound(
      final @NotNull EntityData ed,
      final long createdTime,
      EntityId parent,
      Vec3d loc,
      @NotNull PhysicsSpace phys) {
    final EntityId result = ed.createEntity();
    ed.setComponents(
        result,
        AudioTypes.pickup_prize(ed),
        new Parent(parent),
        new SpawnPosition(phys.getGrid(), loc),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createThorSound(
      final @NotNull EntityData ed,
      final long createdTime,
      EntityId parent,
      Vec3d loc,
      @NotNull PhysicsSpace phys) {
    final EntityId result = ed.createEntity();
    ed.setComponents(
        result,
        AudioTypes.fire_thor(ed),
        new Parent(parent),
        new SpawnPosition(phys.getGrid(), loc),
        new Decay(
            createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(createdTime));
  }

  public static void createMineSound(
      EntityData ed,
      EntityId requester,
      @NotNull PhysicsSpace phys,
      long time,
      Vec3d location,
      Bombs level) {
    final EntityId result = ed.createEntity();
    ed.setComponents(
        result,
        AudioTypes.fire_mine(ed, level),
        new Parent(requester),
        new SpawnPosition(phys.getGrid(), location),
        new Decay(time, time + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)));
    ed.setComponent(result, new Meta(time));
  }
}
