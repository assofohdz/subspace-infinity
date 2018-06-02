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
package infinity.es.states.towerdefense;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.PhysicsShapes;
import infinity.api.es.Position;
import infinity.api.es.TowerType;
import infinity.api.sim.ModuleGameEntities;
import infinity.sim.SimplePhysics;
import org.dyn4j.geometry.Convex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State to keep track of towers and letting the players edit the towers
 *
 * @author Asser
 */
public class TowerState extends AbstractGameSystem {

    private EntityData ed;
    private SimplePhysics simplePhysics;
    private EntitySet towers;
    private ResourceState resourceState;
    static Logger log = LoggerFactory.getLogger(TowerState.class);

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);
        this.simplePhysics = getSystem(SimplePhysics.class);
        
        this.resourceState = getSystem(ResourceState.class);
        
        this.towers = ed.getEntities(TowerType.class, Position.class);
    }

    @Override
    protected void terminate() {
        //Release reader object
        towers.release();
        towers = null;
    }

    @Override
    public void update(SimTime tpf) {

        if (towers.applyChanges()) {
            for (Entity e : towers.getAddedEntities()) {

            }

            for (Entity e : towers.getChangedEntities()) {

            }

            for (Entity e : towers.getRemovedEntities()) {

            }
        }
        
        
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    /**
     * Lets an entity request a tower placement
     * @param x the x-coordinate of the tower
     * @param y the y-coordinate of the tower
     * @param owner the entity that wants to place a tower
     */
    public void requestPlaceTower(double x, double y, EntityId owner) {
        Convex c = PhysicsShapes.tower().getFixture().getShape();
        c.translate(x, y);
        //Can we build there and do we have the money?
        if (simplePhysics.allowConvex(c) && resourceState.canAffordTower(owner)) {
            //Create tower
            ModuleGameEntities.createTower(new Vec3d(x, y, 0), ed);
            //Deduct cost
            resourceState.buyTower(owner);
        }
    }
}
