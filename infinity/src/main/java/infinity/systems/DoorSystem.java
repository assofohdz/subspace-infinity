package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Door;
import java.util.HashMap;

public class DoorSystem extends AbstractGameSystem {

  EntitySet doors;
  EntityData ed;
  HashMap<EntityId, ShapeInfo> doorShapes = new HashMap<EntityId, ShapeInfo>();
  private World world;

  /**
   * The door system is responsible for opening and closing doors. We can simply remove the shape of
   * the door and it should remove both physics and rendering.
   */
  public DoorSystem() {
    // TODO Auto-generated constructor stub
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    doors = ed.getEntities(Door.class, SpawnPosition.class);

    world = getSystem(World.class);
  }

  @Override
  protected void terminate() {
    // Release the entity set
    doors.release();
    doors = null;
  }

  @Override
  public void start() {
    // TODO Auto-generated method stub
  }

  @Override
  public void update(SimTime time) {
    // Based on the door delay setting, we'll open and close doors by removing and adding the shape
    // component.
    // This will remove the door from the physics and rendering systems.
    // We'll also need to add a sound effect for opening and closing doors.
    doors.applyChanges();

    for (Entity e : doors) {
      Door door = e.get(Door.class);
      SpawnPosition pos = e.get(SpawnPosition.class);
//      if (door.getEndTime() < System.currentTimeMillis() && door.isOpen()) {
//        closeDoorWorld(e, door, pos);
//      } else if(door.getEndTime() < System.currentTimeMillis() && !door.isOpen()) {
//        openDoorWorld(e, door, pos);
//      }
      if (door.getEndTime() < System.currentTimeMillis()) {
        openOrCloseDoor(e.getId(), door, pos, door.isOpen());
      }
    }
  }

  @Override
  public void stop() {
    // TODO Auto-generated method stub
  }

  private void openOrCloseDoor(EntityId entityId, Door door, SpawnPosition pos, boolean open) {
    ed.setComponent(entityId, new Door(System.currentTimeMillis(),door.getInterval(), !door.isOpen()));
    world.setWorldCell(pos.getLocation(), door.isOpen() ? 10 : 0);
//    if (door.isOpen()){;
//      ShapeInfo shape = ed.getComponent(entityId, ShapeInfo.class);
//      doorShapes.put(entityId, shape);
//      ed.removeComponent(entityId, ShapeInfo.class);
//    } else {
//      ed.setComponent(entityId, doorShapes.get(entityId));
//    }
  }
}
