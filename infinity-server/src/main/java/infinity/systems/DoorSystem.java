// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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

/** Opens/closes doors based on the per-door delay setting. */
public class DoorSystem extends AbstractGameSystem {

  EntitySet doors;
  EntityData ed;
  private World world;

  public DoorSystem() {
    // no-arg ctor — wiring happens in initialize()
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    doors = ed.getEntities(Door.class, SpawnPosition.class);

    world = getSystem(World.class);
  }

  @Override
  protected void terminate() {
    doors.release();
    doors = null;
  }

  @Override
  public void start() {
    // no-op: lifecycle hook unused; EntitySet wiring happens in initialize()
  }

  @Override
  public void update(SimTime time) {
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
    // no-op: lifecycle hook unused; cleanup happens in terminate()
  }

  private void openOrCloseDoor(EntityId entityId, Door door, SpawnPosition pos) {
    boolean open = world.getWorldCell(pos.getLocation()) == 0;
    ed.setComponent(
        entityId, new Door(System.currentTimeMillis(), door.getInterval()));

    world.setWorldCell(pos.getLocation(), open ? 10 : 0);
  }
}
