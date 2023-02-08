package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Door;
import java.util.HashMap;

public class DoorSystem extends AbstractGameSystem {

  EntitySet doors;
  EntityData ed;
  HashMap<EntityId, ShapeInfo> doorShapes = new HashMap<EntityId, ShapeInfo>();

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
    doors = ed.getEntities(Door.class);
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
      if (door.getEndTime() < System.currentTimeMillis() && door.isOpen()) {
        closeDoor(e, door);
      } else if(door.getEndTime() < System.currentTimeMillis() && !door.isOpen()) {
        openDoor(e, door);
      }
    }
  }

  @Override
  public void stop() {
    // TODO Auto-generated method stub
  }

  //Create a method that will open the door by adding a ShapeInfo component taken from a cache
  public void openDoor(Entity doorEntity, Door door) {
    EntityId doorId = doorEntity.getId();
    if (doorShapes.containsKey(doorId)) {
      ed.setComponent(doorId, doorShapes.get(doorId));
      ed.setComponent(doorId, new Door(System.currentTimeMillis(),door.getInterval(), true));
    }
  }

  //Create a method that will close the door by removing the ShapeInfo component and caching it
  public void closeDoor(Entity doorEntity, Door door) {
    EntityId doorId = doorEntity.getId();
    ShapeInfo shape = ed.getComponent(doorId, ShapeInfo.class);
    if (shape != null) {
      doorShapes.put(doorId, shape);
      ed.removeComponent(doorId, ShapeInfo.class);
      ed.setComponent(doorId, new Door(System.currentTimeMillis(),door.getInterval(), false));
    }
  }
}
