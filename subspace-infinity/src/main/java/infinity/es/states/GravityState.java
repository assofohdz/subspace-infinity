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
package infinity.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.GravityWell;
import infinity.api.es.PhysicsMassType;
import infinity.api.es.PhysicsShape;
import infinity.api.es.Position;
import infinity.api.es.WarpTouch;
import infinity.api.sim.ModuleCollisionFilters;
import infinity.api.sim.ModuleGameEntities;
import infinity.sim.SimplePhysics;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.manifold.ManifoldPoint;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Circle;

/**
 * This state keeps track of all wormholes and applies physical forces to bodies
 * that enter the vicinity of the wormholes
 *
 * @author Asser
 */
public class GravityState extends AbstractGameSystem implements CollisionListener {

    private SimTime time;

    private EntityData ed;
    private EntitySet gravityWells;
    //A set to map from the pulling gravity wells to a pushing gravity well
    private SimplePhysics simplePhysics;
    private HashSet<EntityId> pushingWells, pullingWells;
    private HashSet<BodyFixture> gravityFixtures = new HashSet<>();
    private GravityWells wells;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        this.simplePhysics.addCollisionListener(this);

        gravityWells = ed.getEntities(GravityWell.class, Position.class);

        pushingWells = new HashSet<>();
        pullingWells = new HashSet<>();
    }

    @Override
    protected void terminate() {
        gravityWells.release();
        gravityWells = null;
    }

    @Override
    public void start() {
        wells = new GravityWells(ed);
        wells.start();
    }

    @Override
    public void stop() {

        wells.stop();
        wells = null;
    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;

        wells.update();
    }

    private <T extends Object> T getRandomObject(Collection<T> from) {
        Random rnd = new Random();
        int i = rnd.nextInt(from.size());
        return (T) from.toArray()[i];
    }

    /**
     * @param gravityFixture the fixture to check
     * @return true if the fixture is mapped as a gravity fixture 
     */
    public boolean isWormholeFixture(BodyFixture gravityFixture) {
        return gravityFixtures.contains(gravityFixture);
    }

    /**
     * Creates a force on a given entity
     * @param wormholeEntityId the wormhole entity that exerts a force
     * @param bodyEntityId the body entity impacted by the force
     * @param wormholeBody the wormhole body
     * @param body the impacted body
     * @param mp the manifold
     * @param tpf the time per frame
     */
    private void createWormholeForce(EntityId wormholeEntityId, EntityId bodyEntityId, Body wormholeBody, Body body, ManifoldPoint mp, double tpf) {
        Vec3d wormholeLocation = new Vec3d(wormholeBody.getTransform().getTranslationX(), wormholeBody.getTransform().getTranslationY(), 0);
        Vec3d bodyLocation = new Vec3d(body.getTransform().getTranslation().x, body.getTransform().getTranslation().y, 0);

        GravityWell gravityWell = ed.getComponent(wormholeEntityId, GravityWell.class);
        //start applying gravity to other entity
        Force force = getWormholeGravityOnBody(tpf, gravityWell, wormholeLocation, bodyLocation);
        ModuleGameEntities.createForce(bodyEntityId, force, mp.getPoint(), ed, time.getTime());
    }

    /**
     * Calculates the force that a wormhole exerts on a body
     * @param tpf the time per frame
     * @param wormhole the wormhole
     * @param wormholeLocation the location of the wormhole
     * @param bodyLocation the location of the body
     * @return the calculate force
     */
    private Force getWormholeGravityOnBody(double tpf, GravityWell wormhole, Vec3d wormholeLocation, Vec3d bodyLocation) {
        Vec3d difference = wormholeLocation.subtract(bodyLocation);
        Vec3d gravity = difference.normalize().multLocal(tpf);
        double distance = difference.length();

        double wormholeGravity = wormhole.getForce();
        double gravityDistance = wormhole.getDistance();

        switch (wormhole.getGravityType()) {
            case GravityWell.PULL:
                gravity.multLocal(Math.abs(wormholeGravity));
                break;
            case GravityWell.PUSH:
                gravity.multLocal(-1 * Math.abs(wormholeGravity));
                break;
        }

        gravity.multLocal(gravityDistance / distance);

        Force force = new Force(gravity.x, gravity.y);

        return force;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2) {
        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Penetration penetration) {
        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Manifold manifold) {
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        if (this.isWormholeFixture(fixture1) || this.isWormholeFixture(fixture2)) {
            return this.collide(body1, fixture1, body2, fixture2, manifold, time.getTpf());
        }

        return true;
    }

    public boolean collide(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Manifold manifold, double tpf) {
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        if (wells.getObject(one) == fixture1) {
            createWormholeForce(one, two, body1, body2, manifold.getPoints().get(0), tpf);
        } else if (wells.getObject(two) == fixture2) {
            createWormholeForce(two, one, body2, body1, manifold.getPoints().get(0), tpf);
        } else {
            return true;
        }

        return false;
    }

    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
    }

    /**
     * Maps entities to Physical Fixtures (for use in collision detection)
     */
    private class GravityWells extends EntityContainer<BodyFixture> {

        public GravityWells(EntityData ed) {
            super(ed, GravityWell.class, Position.class, PhysicsMassType.class, PhysicsShape.class);
        }

        @Override
        protected BodyFixture[] getArray() {
            return super.getArray();
        }

        @Override
        protected BodyFixture addObject(Entity e) {
            Body b = simplePhysics.getBody(e.getId());
            //Needs to be a physical body for us to create the fixture
            //Create gravity well;
            GravityWell gw = e.get(GravityWell.class);
            BodyFixture bodyFixture = new BodyFixture(new Circle(gw.getDistance()));

            bodyFixture.setUserData(e.getId());
            bodyFixture.setSensor(true);
            bodyFixture.setFilter(ModuleCollisionFilters.FILTER_CATEGORY_STATIC_GRAVITY);

            b.addFixture(bodyFixture);

            switch (gw.getGravityType()) {
                case GravityWell.PULL:
                    pullingWells.add(e.getId());

                    break;
                case GravityWell.PUSH:
                    pushingWells.add(e.getId());
                    break;
            }
            gravityFixtures.add(bodyFixture);

            return bodyFixture;

        }

        @Override
        protected void updateObject(BodyFixture object, Entity e) {
            //We dont support live-updating the gravity wells
        }

        @Override
        protected void removeObject(BodyFixture object, Entity e) {

            pushingWells.remove(e.getId());
            pullingWells.remove(e.getId());

            gravityFixtures.remove(object);

            ed.removeComponent(e.getId(), WarpTouch.class);

        }

    }
}
