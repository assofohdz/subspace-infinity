package infinity;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.es.Frequency;
import infinity.es.MobType;
import infinity.es.ProbeInfo;
import infinity.es.input.CharacterInput;
import infinity.es.input.MovementInput;
import infinity.es.ship.Player;
import infinity.sim.GameEntities;

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
      byte ship) {

    EntityId mob = GameEntities.createShip(spawnLoc, ed, owner, phys, createdTime, ship);
    byte flags = 0x0;
    ed.setComponent(mob, new CharacterInput(new Vec3d(), new Quatd(), flags));
    ed.setComponent(mob, MobType.create("Mob", ed));
    ed.setComponent(mob, new Name("Mob-"+String.valueOf(mob.getId())));
    ed.setComponent(mob, new ProbeInfo(new Vec3d(0, 0.1, 0.4), 0.3));
    ed.setComponent(mob, new Frequency(1));

    // Right now we create the entity with the player component, but we don't want the mob to be a
    // player
    ed.removeComponent(mob, Player.class);

    return mob;
  }
}
