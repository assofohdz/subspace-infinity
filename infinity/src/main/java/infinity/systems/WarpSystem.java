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
package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.WarpTouch;
import infinity.es.ship.Energy;
import infinity.es.ship.actions.WarpTo;
import infinity.server.chat.ChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CommandBiConsumer;
import infinity.sim.CommandMonoConsumer;
import infinity.sim.GameEntities;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
//FIXME: Implement collisionlistener interface and listen for collisions between warptouch entities and other warpable entities
public class WarpSystem extends AbstractGameSystem{

    private SimTime time;

    private EntityData ed;
    private EntitySet warpTouchEntities, warpToEntities;
    //private Warpers warpers;
    private EntitySet canWarp;
    static Logger log = LoggerFactory.getLogger(WarpSystem.class);
    private PhysicsSpace physicsSpace;
    private ChatHostedService chat;
    private final Pattern requestWarpToCenter = Pattern.compile("\\~warpCenter");

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        this.chat = getSystem(ChatHostedService.class);

        if (getSystem(MPhysSystem.class).equals(null)){
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }
        physicsSpace = getSystem(MPhysSystem.class).getPhysicsSpace();

        warpTouchEntities = ed.getEntities(WarpTouch.class);
        warpToEntities = ed.getEntities(BodyPosition.class, WarpTo.class);

        canWarp = ed.getEntities(BodyPosition.class, Energy.class);

        // Register consuming methods for patterns
        chat.registerPatternMonoConsumer(
            requestWarpToCenter,
            "The command to warp to the center of the arena is ~warpCenter",
            new CommandMonoConsumer(AccessLevel.PLAYER_LEVEL, (id) -> requestWarpToCenter(id)));
    }

    @Override
    protected void terminate() {
        warpTouchEntities.release();
        warpTouchEntities = null;
    }

    @Override
    public void start() {

        //simplePhysics.addCollisionListener(this);

        //warpers = new Warpers(ed);
        //warpers.start();
    }

    @Override
    public void stop() {
        //simplePhysics.removeCollisionListener(this);

        //warpers.stop();
        //warpers = null;
    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;

        //warpTouchEntities.applyChanges();
        //warpers.update()

        canWarp.applyChanges();

        if (warpToEntities.applyChanges()) {
            for (Entity e : warpToEntities) {
                BodyPosition bodyPos = e.get(BodyPosition.class);
                Vec3d targetLocation = e.get(WarpTo.class).getTargetLocation();
                Vec3d originalLocation = bodyPos.getLastLocation();

                //This is the new method to teleport units
                physicsSpace.teleport(e.getId(),targetLocation,bodyPos.getLastOrientation());

                GameEntities.createWarpEffect(ed, e.getId(), physicsSpace, tpf.getTime(),originalLocation,2000);
                GameEntities.createWarpEffect(ed, e.getId(), physicsSpace, tpf.getTime(),targetLocation,2000);

                ed.removeComponent(e.getId(), WarpTo.class);
            }
        }
    }

/*    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2) {
        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Penetration penetration) {
        return true;
    }*/

    //Contact manifold created by the manifold solver
/*    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Manifold manifold) {
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        if ((warpers.getObject(one) != null && warpers.getObject(one).getWarpFixture() == fixture1) || (warpers.getObject(two) != null && warpers.getObject(two).getWarpFixture() == fixture2)) {
            if (warpers.getObject(one) != null && warpers.getObject(one).getWarpFixture() == fixture1) {
                Vector2 targetLocation = warpers.getObject(one).getTargetLocation();
                WarpTo warpTo = new WarpTo(new Vec3d(targetLocation.x, targetLocation.y, 1));
                ed.setComponent(two, warpTo);
            }

            if (warpers.getObject(two) != null && warpers.getObject(two).getWarpFixture() == fixture2) {
                Vector2 targetLocation = warpers.getObject(two).getTargetLocation();
                WarpTo warpTo = new WarpTo(new Vec3d(targetLocation.x, targetLocation.y, 1));
                ed.setComponent(one, warpTo);
            }
            return false;
        }

        return true;
    }*/

    //Contact constraint created
/*    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
    }*/

    /**
     * A WarpFixture is a physical body that has a target location to warp
     * entities to
     */
    /*private class WarpFixture {

        private Vector2 targetLocation;
        private BodyFixture warpFixture;

        public WarpFixture(Vector2 targetLocation, BodyFixture warpFixture) {
            this.targetLocation = targetLocation;
            this.warpFixture = warpFixture;
        }

        public Vector2 getTargetLocation() {
            return targetLocation;
        }

        public void setTargetLocation(Vector2 targetLocation) {
            this.targetLocation = targetLocation;
        }

        public BodyFixture getWarpFixture() {
            return warpFixture;
        }

        public void setWarpFixture(BodyFixture warpFixture) {
            this.warpFixture = warpFixture;
        }
    }*/

    /**
     * Lets entities request a warp to the center of the arena
     * @param eID requesting entity
     */
    public void requestWarpToCenter(EntityId eID) {
        //FIXME: Check for full health

        Entity e = ed.getEntity(eID, BodyPosition.class);
        Vec3d lastLoc = e.get(BodyPosition.class).getLastLocation();

        Vec3d centerOfArena = getSystem(MapSystem.class).getCenterOfArena(lastLoc.x, lastLoc.z);

        Vec3d res = new Vec3d(centerOfArena.x, 1, centerOfArena.z);

        ed.setComponent(eID, new WarpTo(res));
    }

    //Entities that upon touched will warp the other body away
/*    private class Warpers extends EntityContainer<WarpFixture> {

        public Warpers(EntityData ed) {
            super(ed, WarpTouch.class, Position.class, PhysicsMassType.class, PhysicsShape.class);
        }

        @Override
        protected WarpFixture[] getArray() {
            return super.getArray();
        }

        @Override
        protected WarpFixture addObject(Entity e) {
            WarpTouch wt = e.get(WarpTouch.class);

            Body b = physicsSpace.getBody(e.getId());
            BodyFixture bf = b.getFixture(0);

            Vector2 targetLocation = new Vector2(wt.getTargetLocation().x, wt.getTargetLocation().y);

            return new WarpFixture(targetLocation, bf);
        }

        @Override
        protected void updateObject(WarpFixture object, Entity e) {
            //Do not support live-updating warpers
        }

        @Override
        protected void removeObject(WarpFixture object, Entity e) {

        }
    }*/
}
