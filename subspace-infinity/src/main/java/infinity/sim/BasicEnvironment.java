/* 
 * Copyright (c) 2018, Asser Fahrenholz
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

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.GameSystem;
import com.simsilica.sim.SimTime;
import infinity.TimeState;
import infinity.api.es.SteeringPath;
import infinity.api.es.SteeringSeek;
import infinity.api.sim.ModuleGameEntities;

/**
 * Creates a bunch of base entities in the environment.
 *
 * @author Paul Speed
 */
public class BasicEnvironment extends AbstractGameSystem {

    private EntityData ed;
    private SimTime time;
    private boolean envCreated = false;

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException("SimplePhysics system requires an EntityData object.");
        }
    }

    @Override
    protected void terminate() {
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        // For now at least, we won't be reciprocal, ie: we won't remove
        // all of the stuff we added.
    }

    @Override
    public void update(SimTime tpf) {
        this.time = tpf;
        
        if (!envCreated) {
            this.createEnv();
        }

    }

    private void createEnv() {
        envCreated = true;

        // Create some built in objects
        double spacing = 256;
        double offset = -2 * spacing + spacing * 0.5;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    Vec3d pos = new Vec3d(offset + x * spacing, offset + y * spacing, offset + z * spacing);
                    ModuleGameEntities.createGravSphere(pos, 10, ed, time.getTime());
                }
            }
        }

        ModuleGameEntities.createArena(ed, "arena0", new Vec3d(), time.getTime());

        //GameEntities.createBountySpawner(new Vec3d(0, 0, 0), 10, ed);0
        //GameEntities.createExplosion2(new Vec3d(5,5,0), new Quatd().fromAngles(0, 0, Math.random()*360), ed);
        //GameEntities.createWormhole(new Vec3d(-10,-10,0), 5, 5, 5000, GravityWell.PULL, new Vec3d(10,-10,0), ed);
        //GameEntities.createOver5(new Vec3d(10,-10,0), 5, 5000, GravityWell.PUSH, ed);
        ModuleGameEntities.createTower(new Vec3d(5, 5, 0), ed, time.getTime());
        ModuleGameEntities.createTower(new Vec3d(5, 7, 0), ed, time.getTime());
        ModuleGameEntities.createTower(new Vec3d(5, 3.5, 0), ed, time.getTime());
        ModuleGameEntities.createTower(new Vec3d(6, 9, 0), ed, time.getTime());
        ModuleGameEntities.createTower(new Vec3d(4, 2, 0), ed, time.getTime());

        EntityId baseId = ModuleGameEntities.createBase(new Vec3d(30, 10, 0), ed, time.getTime());

        EntityId mobId = ModuleGameEntities.createMob(new Vec3d(-5, 5, 0), ed, time.getTime());
        ed.setComponent(mobId, new SteeringSeek(baseId));
        EntityId mobId2 = ModuleGameEntities.createMob(new Vec3d(-10, 5, 0), ed, time.getTime());
        ed.setComponent(mobId2, new SteeringSeek(baseId));
        EntityId mobId3 = ModuleGameEntities.createMob(new Vec3d(10, -5, 0), ed, time.getTime());
        ed.setComponent(mobId3, new SteeringSeek(baseId));
        EntityId mobId4 = ModuleGameEntities.createMob(new Vec3d(-5, -10, 0), ed, time.getTime());
        ed.setComponent(mobId4, new SteeringSeek(baseId));
        EntityId mobId5 = ModuleGameEntities.createMob(new Vec3d(-5, -15, 0), ed, time.getTime());
        ed.setComponent(mobId5, new SteeringSeek(baseId));

        EntityId mobId6 = ModuleGameEntities.createMob(new Vec3d(-5, 5, 0), ed, time.getTime());
        ed.setComponent(mobId6, new SteeringPath());
        EntityId mobId7 = ModuleGameEntities.createMob(new Vec3d(-10, 5, 0), ed, time.getTime());
        ed.setComponent(mobId7, new SteeringPath());
        EntityId mobId8 = ModuleGameEntities.createMob(new Vec3d(10, -5, 0), ed, time.getTime());
        ed.setComponent(mobId8, new SteeringPath());
        EntityId mobId9 = ModuleGameEntities.createMob(new Vec3d(-5, -10, 0), ed, time.getTime());
        ed.setComponent(mobId9, new SteeringPath());
        EntityId mobId10 = ModuleGameEntities.createMob(new Vec3d(-5, -15, 0), ed, time.getTime());
        ed.setComponent(mobId10, new SteeringPath());

        /*
        GameEntities.createOver1(new Vec3d(10,10,0), ed);
        GameEntities.createOver1(new Vec3d(11,10,0), ed);
        GameEntities.createOver1(new Vec3d(12,10,0), ed);
        GameEntities.createOver1(new Vec3d(13,10,0), ed);
        GameEntities.createOver1(new Vec3d(10,9,0), ed);
        GameEntities.createOver1(new Vec3d(10,8,0), ed);
        GameEntities.createOver1(new Vec3d(10,7,0), ed);
         */
 /*
        for (int x = -4; x < 4; x++) {
            for (int y = -4; y < 4; y++) {
                Vec3d pos = new Vec3d(x, y, 0); 
                GameEntities.createBounty(pos, ed);

            }
        }
         */
        //GameEntities.createWormhole(new Vec3d(-10,10,0), 5, 5, 5000, GravityWell.PULL, new Vec3d(-512,700,0), ed);
    }
}
