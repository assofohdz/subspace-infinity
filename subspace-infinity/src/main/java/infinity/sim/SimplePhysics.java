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

import java.util.*;
import java.util.concurrent.*;

import com.jme3.util.SafeArrayList;

import com.simsilica.es.*;
import com.simsilica.es.filter.OrFilter;
import com.simsilica.sim.*;
import infinity.api.es.GravityWell;
import infinity.api.es.PhysicsForce;
import infinity.api.es.PhysicsMassType;
import infinity.api.es.PhysicsMassTypes;
import infinity.api.es.PhysicsShape;
import infinity.api.es.PhysicsVelocity;
import infinity.api.es.Position;

import infinity.es.states.FlagStateServer;
import infinity.es.states.GravityState;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.ContinuousDetectionMode;
import org.dyn4j.dynamics.DetectResult;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.dynamics.World;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Convex;
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

    public World getWorld() {
        return this.world;
    }

    public enum FixtureType {
        Core, Gravity
    }

    static Logger log = LoggerFactory.getLogger(SimplePhysics.class);

    private EntityData ed;
    private BodyContainer bodies;
    private StaticContainer statics;
    private EntitySet forceEntities;
    private EntitySet velocityEntities;
    private EntitySet gravityEntities;

    // Single threaded.... we'll have to take care when adding/removing
    // items.
    //private SafeArrayList<Body> bodies = new SafeArrayList<>(Body.class);
    private Map<EntityId, SimpleBody> index = new ConcurrentHashMap<>();
    private Map<EntityId, SimpleBody> indexStatic = new ConcurrentHashMap<>();
    private Map<EntityId, SimpleBody> mapIndex = new ConcurrentHashMap<>();
    private Map<EntityId, ControlDriver> driverIndex = new ConcurrentHashMap<>();

    // Still need these to manage physics listener notifications in a 
    // thread-consistent way   
    private ConcurrentLinkedQueue<SimpleBody> toAdd = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<SimpleBody> toRemove = new ConcurrentLinkedQueue<>();

    private SafeArrayList<PhysicsListener> listeners = new SafeArrayList<>(PhysicsListener.class);

    private World world;
    private Settings physicsSettings;

    private SimTime time;

    private GravityState gravityState;
    private FlagStateServer flagState;

    public SimplePhysics() {
    }

    /**
     * Adds a listener that will be notified about physics related updates. This
     * is not a thread safe method call so must be called during setup or from
     * the physics/simulation thread.
     *
     * @param l the physics listener to add
     */
    public void addPhysicsListener(PhysicsListener l) {
        listeners.add(l);
    }

    /**
     * Removes a listener
     *
     * @param l the physics listener to remove
     */
    public void removePhysicsListener(PhysicsListener l) {
        listeners.remove(l);
    }

    public SimpleBody getBody(EntityId entityId) {
        if (index.containsKey(entityId)) {
            return index.get(entityId);
        } else {
            return indexStatic.get(entityId);
        }
    }

    public void setControlDriver(EntityId entityId, ControlDriver driver) {
        synchronized (this) {
            driverIndex.put(entityId, driver);
            SimpleBody current = getBody(entityId);
            if (current != null) {
                current.driver = driver;
            }
        }
    }

    public ControlDriver getControlDriver(EntityId entityId) {
        return driverIndex.get(entityId);
    }

    protected SimpleBody createBody(EntityId entityId, String massType, BodyFixture fixture, boolean create) {
        SimpleBody result = index.get(entityId);
        if (result == null && create) {
            synchronized (this) {
                result = index.get(entityId);
                if (result != null) {
                    return result;
                }
                result = new SimpleBody(entityId);

                // Hookup the driver if it has one waiting
                result.driver = driverIndex.get(entityId);

                // Set it up to be managed by Dyn4j
                result.addFixture(fixture);

                switch (massType) {
                    case PhysicsMassTypes.FIXED_ANGULAR_VELOCITY:
                        result.setMass(MassType.FIXED_ANGULAR_VELOCITY);
                        break;
                    case PhysicsMassTypes.FIXED_LINEAR_VELOCITY:
                        result.setMass(MassType.FIXED_LINEAR_VELOCITY);
                        break;
                    case PhysicsMassTypes.INFINITE:
                        result.setMass(MassType.INFINITE);
                        break;
                    case PhysicsMassTypes.NORMAL:
                        result.setMass(MassType.NORMAL);
                        break;
                    case PhysicsMassTypes.NORMAL_BULLET:
                        result.setMass(MassType.NORMAL);
                        result.setBullet(true);
                        break;
                }

                world.addBody(result);

                // Set it up to be managed by physics
                toAdd.add(result);
                index.put(entityId, result);
            }
        }
        log.info("Created dynamic body: " + result.toString());
        log.info("Number of dynamics in play: " + index.size());
        return result;
    }

    protected SimpleBody createStatic(EntityId entityId, String massType, BodyFixture fixture, boolean create) {
        SimpleBody result = indexStatic.get(entityId);
        if (result == null && create) {
            synchronized (this) {
                result = indexStatic.get(entityId);
                if (result != null) {
                    return result;
                }
                result = new SimpleBody(entityId);

                // Set it up to be managed by Dyn4j
                result.addFixture(fixture);

                result.setMass(MassType.INFINITE);

                world.addBody(result);

                // Set it up to be managed by physics
                indexStatic.put(entityId, result);
            }
        }

        log.info("Created static body: " + result.toString());
        log.info("Number of statics in play: " + indexStatic.size());
        return result;
    }

    protected boolean removeBody(EntityId entityId) {
        SimpleBody result = index.remove(entityId);
        if (result != null) {
            log.info("Number of dynamics in play: " + index.size());
            toRemove.add(result);
            return true;
        }
        result = indexStatic.remove(entityId);
        if (result != null) {
            log.info("Number of statics in play: " + indexStatic.size());
            toRemove.add(result);
            return true;
        }
        return false;
    }

    protected boolean removeMapTile(EntityId entityId) {
        SimpleBody result = mapIndex.remove(entityId);
        if (result != null) {
            return true;
        }
        return false;
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException("SimplePhysics system requires an EntityData object.");
        }

        physicsSettings = new Settings();
        physicsSettings.setContinuousDetectionMode(ContinuousDetectionMode.NONE);

        world = new World();
        world.setSettings(physicsSettings);
        world.setGravity(World.ZERO_GRAVITY);

        world.addListener(this);
        world.getSettings().setRestitutionVelocity(0);

        world.getSettings().setContinuousDetectionMode(ContinuousDetectionMode.BULLETS_ONLY);
        //world.getSettings().set

        forceEntities = ed.getEntities(PhysicsForce.class);
        velocityEntities = ed.getEntities(PhysicsVelocity.class);

        gravityEntities = ed.getEntities(GravityWell.class);

        gravityState = this.getSystem(GravityState.class);
        flagState = this.getSystem(FlagStateServer.class);

    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
        forceEntities.release();
        forceEntities = null;

        velocityEntities.release();
        velocityEntities = null;

        gravityEntities.release();
        gravityEntities = null;
    }

    private void fireBodyListListeners() {
        if (!toRemove.isEmpty()) {
            SimpleBody body = null;
            while ((body = toRemove.poll()) != null) {
                //bodies.remove(body);
                for (PhysicsListener l : listeners.getArray()) {
                    l.removeBody(body);
                }
            }
        }
        if (!toAdd.isEmpty()) {
            SimpleBody body = null;
            while ((body = toAdd.poll()) != null) {
                //bodies.add(body);
                for (PhysicsListener l : listeners.getArray()) {
                    l.addBody(body);
                }
            }
        }

    }

    @Override
    public void start() {
        bodies = new BodyContainer(ed);
        bodies.start();

        statics = new StaticContainer(ed);
        statics.start();

    }

    @Override
    public void stop() {
        bodies.stop();
        bodies = null;

        statics.stop();
        statics = null;

    }

    @Override
    public void update(SimTime time) {

        this.time = time;

        for (PhysicsListener l : listeners.getArray()) {
            l.beginFrame(time);
        }

        // Update the entity list       
        bodies.update();
        statics.update();

        if (forceEntities.applyChanges()) {
            applyForces();
        }
        if (velocityEntities.applyChanges()) {
            applyVelocities();
        }

        // Fire off any add/remove events 
        fireBodyListListeners();

        double tpf = time.getTpf();

        // Apply control driver changes (apply forces onto Dyn4j bodies)
        for (SimpleBody b : bodies.getArray()) {
            if (b.driver != null) {
                b.driver.update(tpf, b);
            }
        }

        world.update(tpf);

        // Integrate (get info from Dyn4j bodies)
        for (SimpleBody b : bodies.getArray()) {
            //b.integrate(tpf);
            b.syncronizePhysicsBody();
        }

        //Updating physics listeners
        // Publish the results
        for (PhysicsListener l : listeners.getArray()) {
            for (SimpleBody b : bodies.getArray()) {
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
        //Default, keep processing this event

        return true;
    }

    //Collision detected by narrowphase
    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Penetration penetration) {
        return true;
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
    private class BodyContainer extends EntityContainer<SimpleBody> {

        //Filter for only dynamic objects
        public BodyContainer(EntityData ed) {
            super(ed, OrFilter.create(PhysicsMassType.class,
                    Filters.fieldEquals(PhysicsMassType.class, "type", PhysicsMassTypes.fixedLinearVelocity(ed).getType()),
                    Filters.fieldEquals(PhysicsMassType.class, "type", PhysicsMassTypes.fixedAngularVelocity(ed).getType()),
                    Filters.fieldEquals(PhysicsMassType.class, "type", PhysicsMassTypes.normal(ed).getType()),
                    Filters.fieldEquals(PhysicsMassType.class, "type", PhysicsMassTypes.normal_bullet(ed).getType())),
                    Position.class, PhysicsMassType.class, PhysicsShape.class);
        }

        @Override
        protected SimpleBody[] getArray() {
            return super.getArray();
        }

        @Override
        protected SimpleBody addObject(Entity e) {
            //TODO: Have gravity state and warpstate listen for body creations instead of relying on same info that SimplePhysics relies on in their containers
            PhysicsShape ps = e.get(PhysicsShape.class);
            PhysicsMassType pmt = e.get(PhysicsMassType.class);
            Position pos = e.get(Position.class);

            // Right now only works for CoG-centered shapes                   
            SimpleBody newBody = createBody(e.getId(), pmt.getTypeName(ed), ps.getFixture(), true);

            newBody.setPosition(pos);   //ES position: Not used anymore, since Dyn4j controls movement

            newBody.getTransform().setTranslation(pos.getLocation().x, pos.getLocation().y); //Dyn4j position
            newBody.getTransform().setRotation(pos.getRotation());

            newBody.setUserData(e.getId());

            newBody.setLinearDamping(0.2);

            if (pmt.getTypeName(ed).equals(PhysicsMassTypes.NORMAL_BULLET)) {
                newBody.setBullet(true);
            }

            return newBody;
        }

        @Override
        protected void updateObject(SimpleBody object, Entity e) {
            // We don't support live-updating mass or shape right now
        }

        @Override
        protected void removeObject(SimpleBody object, Entity e) {
            world.removeBody(object);
            log.info("Removed dynamic body: " + object.toString());
            removeBody(e.getId());
        }

    }

    /**
     * Maps the appropriate entities to physics bodies.
     */
    private class StaticContainer extends EntityContainer<SimpleBody> {

        //Filter for only dynamic objects
        public StaticContainer(EntityData ed) {
            super(ed, Filters.fieldEquals(PhysicsMassType.class, "type", PhysicsMassTypes.infinite(ed).getType()),
                    Position.class, PhysicsMassType.class, PhysicsShape.class);
        }

        @Override
        protected SimpleBody[] getArray() {
            return super.getArray();
        }

        @Override
        protected SimpleBody addObject(Entity e) {
            /**
             * TODO: A Body flagged as a Bullet will be checked for tunneling
             * depending on the CCD setting in the world's Settings. Use this if
             * the body is a fast moving body, but be careful as this will incur
             * a performance hit.
             */

            //TODO: Have gravity state and warpstate listen for body creations instead of relying on same info that SimplePhysics relies on in their containers
            PhysicsShape ps = e.get(PhysicsShape.class);
            PhysicsMassType pmt = e.get(PhysicsMassType.class);
            Position pos = e.get(Position.class);

            // Right now only works for CoG-centered shapes                   
            SimpleBody newBody = createStatic(e.getId(), pmt.getTypeName(ed), ps.getFixture(), true);

            newBody.setPosition(pos);   //ES position: Not used anymore, since Dyn4j controls movement

            newBody.getTransform().setTranslation(pos.getLocation().x, pos.getLocation().y); //Dyn4j position
            newBody.getTransform().setRotation(pos.getRotation());

            newBody.setUserData(e.getId());

            newBody.setLinearDamping(0.3);

            return newBody;
        }

        @Override
        protected void updateObject(SimpleBody object, Entity e) {
            // We don't support live-updating mass or shape right now
        }

        @Override
        protected void removeObject(SimpleBody object, Entity e) {
            world.removeBody(object);
            log.info("Removed static body: " + object.toString());
            removeBody(e.getId());
        }

    }

    private void applyForces() {
        for (Entity e : forceEntities.getAddedEntities()) {
            PhysicsForce pf = e.get(PhysicsForce.class);

            EntityId target = pf.getTarget();

            if (bodies.getObject(target) != null) {

                SimpleBody b = getBody(target);

                Force f = pf.getForce();

                Vector2 worldCoords = pf.getForceWorldCoords();
                //These are accumulated until they are processed, so we accept that there can be many forces/torques acting on the same body
                b.applyForce(f.getForce(), worldCoords);

                ed.removeEntity(e.getId());
            }
        }
    }

    private void applyVelocities() {
        for (Entity e : velocityEntities) {
            if (this.getBody(e.getId()) != null) {
                SimpleBody b = getBody(e.getId());

                PhysicsVelocity pv = e.get(PhysicsVelocity.class);

                b.setLinearVelocity(pv.getVelocity());
                //b.setAsleep(false);
                /*
                if (pv.getVelocity().equals(new Vector2(0, 0))) {
                    b.setAsleep(true); //Clear all forces/torques/velocities
                    b.setAsleep(false);
                }
                 */
                ed.removeComponent(e.getId(), PhysicsVelocity.class);
            }
        }
    }

    public void addCollisionListener(CollisionListener cl) {
        world.addListener(cl);
    }

    public void removeCollisionListener(CollisionListener cl) {
        world.removeListener(cl);
    }

    /**
     * Removes and adds the entity. This ensures that listeners know that this
     * body has been reset
     *
     * @param eId the EntityId
     */
    public void resetBody(EntityId eId) {
        SimpleBody body = getBody(eId);
        body.syncronizePhysicsBody();
        toRemove.add(body);
        toAdd.add(body);
        fireBodyListListeners();
    }

    public boolean allowConvex(Convex convex) {
        ArrayList<DetectResult> results = new ArrayList<>();
        world.detect(convex, results);

        return results.isEmpty();
    }
}
