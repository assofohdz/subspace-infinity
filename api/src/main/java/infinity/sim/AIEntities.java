// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.config.EngineConfig;
import infinity.es.Frequency;
import infinity.es.MobType;
import infinity.es.ProbeInfo;
import infinity.es.input.CharacterInput;
import infinity.es.ship.Player;
import infinity.sim.specs.ShipArgs;

public class AIEntities {

  private AIEntities() {
    // no instances
  }

  public static EntityId createMobShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final byte ship) {

    EntityId mob =
        ShipFactory.createShip(
            ed,
            new ShipArgs(
                spawnLoc, owner, phys, createdTime, ship, EngineConfig.DEFAULTS.shipRadius()));
    byte flags = 0x0;
    ed.setComponent(mob, new CharacterInput(new Vec3d(), new Quatd(), flags));
    ed.setComponent(mob, MobType.create("Mob", ed));
    ed.setComponent(mob, new Name("Mob-"+mob.getId()));
    ed.setComponent(mob, new ProbeInfo(new Vec3d(0, 0.1, 0.4), 0.3));
    ed.setComponent(mob, new Frequency(1));

    // Right now we create the entity with the player component, but we don't want the mob to be a
    // player
    ed.removeComponent(mob, Player.class);

    return mob;
  }
}
