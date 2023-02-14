package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Door;

/** This system will open and close doors based on the delay setting. */
public class DoorSystem extends AbstractGameSystem {

  EntitySet doors;
  EntityData ed;
  private World world;

  /** Creates a new DoorSystem. */
  public DoorSystem() {
    // Auto-generated constructor stub
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
    // Auto-generated method stub
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
      if (door.getEndTime() < System.currentTimeMillis()) {
        openOrCloseDoor(e.getId(), door, pos);
      }
    }
  }

  @Override
  public void stop() {
    // Auto-generated method stub
  }

  private void openOrCloseDoor(EntityId entityId, Door door, SpawnPosition pos) {
    boolean open = world.getWorldCell(pos.getLocation()) == 0;
    ed.setComponent(
        entityId, new Door(System.currentTimeMillis(), door.getInterval()));

    world.setWorldCell(pos.getLocation(), open ? 10 : 0);
  }
}
