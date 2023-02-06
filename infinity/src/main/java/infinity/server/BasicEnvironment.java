/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.server;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.sim.GameEntities;
import infinity.systems.InfinityTimeSystem;
import infinity.systems.MapSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides some standard entity factories as well as setting up an initial test environment.
 *
 * @author Paul Speed
 */
public class BasicEnvironment extends AbstractGameSystem {

  static Logger log = LoggerFactory.getLogger(BasicEnvironment.class);

  private EntityData ed;
  private PhysicsSpace<?, ?> phys;
  private long time;
  private World world;

  /** Creates a new BasicEnvironment. This should be loaded as the last game system. */
  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class, true);
    phys = getSystem(PhysicsSpace.class, true);
    world = super.getManager().get(World.class);

    this.time = getSystem(InfinityTimeSystem.class).getTime();

    getSystem(MapSystem.class).loadMap(EntityId.NULL_ID, "trench.lvl");
    Vec3d squarePos = new Vec3d(-10, 1, 10);

    // Create a square around the flag
    world.setWorldCell(squarePos.add(1, 0, 1), 10);
    world.setWorldCell(squarePos.add(0, 0, 1), 10);
    world.setWorldCell(squarePos.add(-1, 0, 1), 10);
    world.setWorldCell(squarePos.add(1, 0, 0), 10);
    world.setWorldCell(squarePos.add(-1, 0, 0), 10);
    world.setWorldCell(squarePos.add(1, 0, -1), 10);
    world.setWorldCell(squarePos.add(0, 0, -1), 10);
    world.setWorldCell(squarePos.add(-1, 0, -1), 10);

    GameEntities.createTurfStationaryFlag(ed, EntityId.NULL_ID, phys, time, squarePos);

    GameEntities.createTurfStationaryFlag(ed, EntityId.NULL_ID, phys, time, new Vec3d(-10, 1, -10));

    // Create a square of turf flags 100x100 around x = 20, z = -20
    for (int x = 20; x <= 30; x++) {
      for (int z = -30; z <= -20; z++) {
        GameEntities.createTurfStationaryFlag(ed, EntityId.NULL_ID, phys, time, new Vec3d(x, 1, z));
      }
    }
  }

  @Override
  public void update(final SimTime time) {
    // Nothing to do
  }

  @Override
  protected void terminate() {
    // Nothing to do
  }
}
