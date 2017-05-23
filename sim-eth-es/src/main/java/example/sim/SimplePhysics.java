/*
 * $Id$
 * 
 * Copyright (c) 2016, Simsilica, LLC
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
package example.sim;

import com.jme3.math.Vector3f;
import java.util.*;
import java.util.concurrent.*;

import com.jme3.util.SafeArrayList;

import com.simsilica.es.*;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.*;
import example.PhysicsConstants;

import example.es.*;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.Torque;
import org.dyn4j.dynamics.World;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Just a basic physics simulation that integrates acceleration, velocity, and
 * position on "point masses".
 *
 * @author Paul Speed
 */
public class SimplePhysics extends AbstractGameSystem implements CollisionListener {

    static Logger log = LoggerFactory.getLogger(SimplePhysics.class);

    private EntityData ed;
    private BodyContainer bodies;
    private EntitySet forceEntities;
    private EntitySet velocityEntities;

    // Single threaded.... we'll have to take care when adding/removing
    // items.
    //private SafeArrayList<Body> bodies = new SafeArrayList<>(Body.class);
    private Map<EntityId, Body> index = new ConcurrentHashMap<>();
    private Map<EntityId, ControlDriver> driverIndex = new ConcurrentHashMap<>();

    // Still need these to manage physics listener notifications in a 
    // thread-consistent way   
    private ConcurrentLinkedQueue<Body> toAdd = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Body> toRemove = new ConcurrentLinkedQueue<>();

    private SafeArrayList<PhysicsListener> listeners = new SafeArrayList<>(PhysicsListener.class);

    private World world;

    private SimTime time;

    public SimplePhysics() {
    }

    /**
     * Adds a listener that will be notified about physics related updates. This
     * is not a thread safe method call so must be called during setup or from
     * the physics/simulation thread.
     */
    public void addPhysicsListener(PhysicsListener l) {
        listeners.add(l);
    }

    public void removePhysicsListener(PhysicsListener l) {
        listeners.remove(l);
    }

    public Body getBody(EntityId entityId) {
        return index.get(entityId);
    }

    public void setControlDriver(EntityId entityId, ControlDriver driver) {
        synchronized (this) {
            driverIndex.put(entityId, driver);
            Body current = getBody(entityId);
            if (current != null) {
                current.driver = driver;
            }
        }
    }

    public ControlDriver getControlDriver(EntityId entityId) {
        return driverIndex.get(entityId);
    }

    protected Body createBody(EntityId entityId, double invMass, BodyFixture fixture, boolean create) {
        Body result = index.get(entityId);
        if (result == null && create) {
            synchronized (this) {
                result = index.get(entityId);
                if (result != null) {
                    return result;
                }
                result = new Body(entityId);

                // Hookup the driver if it has one waiting
                result.driver = driverIndex.get(entityId);

                // Set it up to be managed by Dyn4j
                result.addFixture(fixture.getShape());

                if (invMass == 0) {
                    result.setMass(MassType.INFINITE);

                } else {
                    result.setMass(MassType.NORMAL);
                }

                world.addBody(result);

                // Set it up to be managed by physics
                toAdd.add(result);
                index.put(entityId, result);
            }
        }
        return result;
    }

    protected boolean removeBody(EntityId entityId) {
        Body result = index.remove(entityId);
        if (result != null) {
            toRemove.add(result);
            return true;
        }
        return false;
    }

    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException("SimplePhysics system requires an EntityData object.");
        }

        world = new World();
        world.setGravity(World.ZERO_GRAVITY);
        world.addListener(this);

        forceEntities = ed.getEntities(PhysicsForce.class);
        velocityEntities = ed.getEntities(PhysicsVelocity.class);
    }

    protected void terminate() {
        // Release the entity set we grabbed previously
        forceEntities.release();
        forceEntities = null;

        velocityEntities.release();
        velocityEntities = null;
    }

    private void fireBodyListListeners() {
        if (!toAdd.isEmpty()) {
            Body body = null;
            while ((body = toAdd.poll()) != null) {
                //bodies.add(body);
                for (PhysicsListener l : listeners.getArray()) {
                    l.addBody(body);
                }
            }
        }
        if (!toRemove.isEmpty()) {
            Body body = null;
            while ((body = toRemove.poll()) != null) {
                //bodies.remove(body);
                for (PhysicsListener l : listeners.getArray()) {
                    l.removeBody(body);
                }
            }
        }
    }

    @Override
    public void start() {
        bodies = new BodyContainer(ed);
        bodies.start();
    }

    @Override
    public void stop() {
        bodies.stop();
        bodies = null;
    }

    @Override
    public void update(SimTime time) {

        this.time = time;

        for (PhysicsListener l : listeners.getArray()) {
            l.beginFrame(time);
        }

        // Update the entity list       
        bodies.update();
        applyForces();
        applyVelocities();

        // Fire off any add/remove events 
        fireBodyListListeners();

        double tpf = time.getTpf();

        // Apply control driver changes (apply forces onto Dyn4j bodies)
        for (Body b : bodies.getArray()) {
            if (b.driver != null) {
                b.driver.update(tpf, b);
            }
        }

        world.update(tpf);

        // Integrate (get info from Dyn4j bodies)
        for (Body b : bodies.getArray()) {
            //b.integrate(tpf);
            b.syncronizePhysicsBody();
        }

        //Updating physics listeners
        // Publish the results
        for (PhysicsListener l : listeners.getArray()) {
            for (Body b : bodies.getArray()) {
                l.updateBody(b);
            }
        }

        for (PhysicsListener l : listeners.getArray()) {
            l.endFrame(time);
        }
    }

    //Collision detected by the broadphase
    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2) {

        Entity one = (Entity) body1.getUserData();
        Entity two = (Entity) body2.getUserData();

        ObjectType ot1 = one.get(ObjectType.class);
        ObjectType ot2 = two.get(ObjectType.class);
        
        if (ot1.getTypeName(ed).equals(ObjectTypes.WORMHOLE) || ot1.getTypeName(ed).equals(ObjectTypes.OVER5)) {
            EntityId wormholeId = one.getId();
            EntityId eId2 = two.getId();

            GravityWell warp = ed.getComponent(wormholeId, GravityWell.class);
            Position wormholePos = ed.getComponent(wormholeId, Position.class);

            Vector2 bodyTranslation = body2.getTransform().getTranslation();
            Vec3d bodyEntityLocation = new Vec3d(bodyTranslation.x, bodyTranslation.y, 0); //TODO: Arena setting?
            if (fixture1.getShape().getRadius() != PhysicsConstants.WORMHOLESIZERADIUS) {
                //start applying gravity to other entity

                Force force = getWormholeGravityOnBody(time.getTpf(), warp, wormholePos.getLocation(), bodyEntityLocation);

                GameEntities.createForce(eId2, force, new Torque(), ed);
            } else {
                //Beam me up Scotty!
                //TODO: Place blink animation at old and new location
                body2.getTransform().setTranslation(warp.getTargetLocation().x, warp.getTargetLocation().y);
            }
            return false;
        }

        if (ot2.getTypeName(ed).equals(ObjectTypes.WORMHOLE) || ot2.getTypeName(ed).equals(ObjectTypes.OVER5)) {
            EntityId wormholeId = two.getId();
            EntityId eId2 = one.getId();

            GravityWell warp = ed.getComponent(wormholeId, GravityWell.class);
            Position wormholePos = ed.getComponent(wormholeId, Position.class);

            Vector2 bodyTranslation = body1.getTransform().getTranslation();
            Vec3d bodyEntityLocation = new Vec3d(bodyTranslation.x, bodyTranslation.y, 0); //TODO: Arena setting?
            if (fixture2.getShape().getRadius() != PhysicsConstants.WORMHOLESIZERADIUS) {

                Force force = getWormholeGravityOnBody(time.getTpf(), warp, wormholePos.getLocation(), bodyEntityLocation);

                GameEntities.createForce(eId2, force, new Torque(), ed);

            } else {
                //Beam me out of here
                body1.getTransform().setTranslation(warp.getTargetLocation().x, warp.getTargetLocation().y);
            }
            return false;
        }

        return true; //Default, keep processing this event
    }

    //Collision detected by narrowphase
    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Penetration penetration) {
        return true; //Default, keep processing this event
    }

    //Contact manifold created by the manifold solver
    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Manifold manifold) {
        return true; //Default, keep processing this event
    }

    //Contact constraint created
    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true; //Default, keep processing this event
    }

    /**
     * Maps the appropriate entities to physics bodies.
     */
    private class BodyContainer extends EntityContainer<Body> {

        public BodyContainer(EntityData ed) {
            super(ed, ObjectType.class, Position.class, MassProperties.class, PhysicsShape.class);
        }

        @Override
        protected Body[] getArray() {
            return super.getArray();
        }

        @Override
        protected Body addObject(Entity e) {
            MassProperties mass = e.get(MassProperties.class);
            PhysicsShape ps = e.get(PhysicsShape.class);
            ObjectType ot = e.get(ObjectType.class);

            // Right now only works for CoG-centered shapes                   
            Body newBody = createBody(e.getId(), mass.getInverseMass(), ps.getFixture(), true);

            Position pos = e.get(Position.class);
            newBody.setPosition(pos);   //ES position: Not used anymore, since Dyn4j controls movement

            newBody.getTransform().setTranslation(pos.getLocation().x, pos.getLocation().y); //Dyn4j position
            newBody.getTransform().setRotation(pos.getRotation());

            switch (ot.getTypeName(ed)) {
                case ObjectTypes.WORMHOLE:
                    //Add gravity circle here
                    GravityWell wormhole = ed.getComponent(e.getId(), GravityWell.class);

                    newBody.addFixture(new Circle(wormhole.getDistance()));
                    break;
                case ObjectTypes.OVER5:
                    //Add gravity circle here
                    GravityWell over5 = ed.getComponent(e.getId(), GravityWell.class);

                    newBody.addFixture(new Circle(over5.getDistance()));
                    break;
                default:
                    break;
            }

            newBody.setUserData(e);

            return newBody;
        }

        @Override
        protected void updateObject(Body object, Entity e) {
            // We don't support live-updating mass or shape right now
        }

        @Override
        protected void removeObject(Body object, Entity e) {
            removeBody(e.getId());
            world.removeBody(object);
        }

    }

    private void applyForces() {
        if (forceEntities.applyChanges()) {
            for (Entity e : forceEntities) {
                PhysicsForce pf = e.get(PhysicsForce.class);

                EntityId target = pf.getTarget();

                if (bodies.getObject(target) != null) {

                    Body b = getBody(target);

                    Force f = pf.getForce();
                    Torque t = pf.getTorque();
                    b.applyForce(f);
                    b.applyTorque(t);

                    ed.removeEntity(e.getId());
                }
            }
        }
    }

    private void applyVelocities() {
        if (velocityEntities.applyChanges()) {
            for (Entity e : velocityEntities) {
                if (bodies.getObject(e.getId()) != null) {
                    Body b = getBody(e.getId());

                    PhysicsVelocity pv = e.get(PhysicsVelocity.class);

                    b.setLinearVelocity(pv.getVelocity());

                    ed.removeComponent(e.getId(), PhysicsVelocity.class);
                }
            }
        }
    }

    private Force getWormholeGravityOnBody(double tpf, GravityWell wormhole, Vec3d wormholeLocation, Vec3d bodyLocation) {
        Vec3d difference = wormholeLocation.subtract(bodyLocation);
        Vec3d gravity = difference.normalize().multLocal(tpf);
        double distance = difference.length();

        double wormholeGravity = wormhole.getForce();
        double gravityDistance = wormhole.getDistance();

        gravity.multLocal(wormholeGravity);
        gravity.multLocal(gravityDistance / distance);

        Force force = new Force(gravity.x, gravity.y);

        return force;
    }
}
