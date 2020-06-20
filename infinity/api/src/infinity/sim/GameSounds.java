/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import infinity.es.ship.weapons.BombLevelEnum;
import infinity.es.ship.weapons.GunLevelEnum;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author AFahrenholz
 */
public class GameSounds {
    
    public static void createBombSound(EntityData ed, EntityId owner, PhysicsSpace phys, long createdTime,
            Vec3d pos, BombLevelEnum level) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.fire_bomb(ed, level),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new SpawnPosition(phys.getGrid(), pos),
                new Parent(owner));
        ed.setComponent(result, new Meta(createdTime));
    }

    public static void createExplosionSound(EntityData ed, EntityId owner, PhysicsSpace phys, long createdTime,
            Vec3d pos) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.explosion2(ed),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new SpawnPosition(phys.getGrid(), pos),
                new Parent(owner));
        ed.setComponent(result, new Meta(createdTime));
    }

    public static EntityId createSound(EntityData ed, EntityId owner, PhysicsSpace phys, long createdTime,
            Vec3d pos, String audioType) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioType.create(audioType, ed),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new SpawnPosition(phys.getGrid(), pos),
                new Parent(owner));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }
    
    public static void createBulletSound(EntityData ed, EntityId owner, PhysicsSpace phys, long createdTime,
            Vec3d pos, GunLevelEnum level) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.fire_bullet(ed, level),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new SpawnPosition(phys.getGrid(), pos),
                new Parent(owner));

        ed.setComponent(result, new Meta(createdTime));

    }
    
    public static void createBurstSound(EntityData ed, EntityId owner, PhysicsSpace phys, long createdTime,
            Vec3d pos) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.fire_burst(ed),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)) //Three seconds to play the sound
        );
        ed.setComponent(result, new Meta(createdTime));
    }

}
