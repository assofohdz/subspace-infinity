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

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/**
 * Provides some standard entity factories as well as setting up an initial test
 * environment.
 *
 * @author Paul Speed
 */
public class BasicEnvironment extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(BasicEnvironment.class);

    private EntityData ed;
    private PhysicsSpace phys;
    private World world;

    // Some constantly emitted test objects... need to track the time
    private double nextTime = 5;
    private Random rand = new Random(0);

    private EntityId toggle1;
    private EntityId toggle2;
    private double toggleTime = 5;
    private boolean moved = false;

    private Vec3d putLoc = new Vec3d(0, 66, -10);
    private double putTime = 5;

    private Vec3d putLoc2 = new Vec3d(4, 64, -4);

    private EntityId spawner;
    private Vec3d spawnerOffset = new Vec3d(0, 10, 0);

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class, true);
        this.phys = getSystem(PhysicsSpace.class, true);
        this.world = getSystem(World.class, true);
        /*
         * // Setup some test entities EntityId test; test = ed.createEntity();
         * ed.setComponents(test, new SpawnPosition(phys.getGrid(), 5, 65, -5), // For
         * static objects, we set both position and spawn position. // The physics
         * engine needs spawn position for static objects. // We need position for
         * displayed objects. //new Position(5, 1, -5), ShapeInfo.create("sphere", 1,
         * ed), new Mass(0) );
         */
        this.createTestSphere(new Vec3d(0, 0, 0), 1d, false);

    }

    @Override
    public void update(SimTime time) {

        double secs = time.getTimeInSeconds();

    }

    public EntityId createTestSphere(Vec3d loc, double size, boolean dynamic) {
        EntityId result = ed.createEntity();

        if (dynamic) {
            ed.setComponents(result, new SpawnPosition(phys.getGrid(), loc), ShapeInfo.create("sphere", size, ed),
                    new Mass(10), Gravity.ZERO);
        } else {
            ed.setComponents(result, new SpawnPosition(phys.getGrid(), loc), ShapeInfo.create("sphere", size, ed),
                    new Mass(0));
        }

        return result;
    }

    @Override
    protected void terminate() {
    }
}
