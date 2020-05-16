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
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.Flag;
import infinity.api.es.Frequency;
import infinity.sim.SimplePhysics;
import org.dyn4j.collision.CollisionBody;
import org.dyn4j.collision.Fixture;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;

/**
 *
 * @author Asser
 */
public class FlagStateServer extends AbstractGameSystem implements CollisionListener {

    private EntityData ed;
    private EntitySet teamFlags;
    private ShipFrequencyStateServer shipState;

    private SimTime tpf;
    private SimplePhysics simplePhysics;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        teamFlags = ed.getEntities(Flag.class, Frequency.class);

        shipState = getSystem(ShipFrequencyStateServer.class);
        this.simplePhysics = getSystem(SimplePhysics.class);
        this.simplePhysics.addCollisionListener(this);
    }

    @Override
    protected void terminate() {
        teamFlags.release();
        teamFlags = null;

    }

    public boolean isFlag(EntityId flagEntityId) {
        return teamFlags.getEntityIds().contains(flagEntityId);
    }

    @Override
    public void update(SimTime tpf) {
        this.tpf = tpf;
        teamFlags.applyChanges();
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    /**
     * Collides a player with a flag and captures the flag if necessary
     *
     * @param body1 the first physics body
     * @param fixture1 the first fixture
     * @param body2 the second physics body
     * @param fixture2 the second fixture
     * @param manifold the manifold
     * @param tpf the time per frame
     * @return true if the event should be processed further, false if collision
     * event is consumed by this state
     */
    public boolean collide(CollisionBody body1, Fixture fixture1, CollisionBody body2, Fixture fixture2, Manifold manifold, double tpf) {
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        if (isFlag(one)) {
            int flagFreq = ed.getComponent(one, Frequency.class).getFreq();
            int shipFreq = shipState.getFrequency(two);

            if (shipFreq != flagFreq) {
                ed.setComponent(one, new Frequency(shipFreq));
            }

        } else if (isFlag(two)) {
            int flagFreq = ed.getComponent(two, Frequency.class).getFreq();
            int shipFreq = shipState.getFrequency(one);

            if (shipFreq != flagFreq) {
                ed.setComponent(two, new Frequency(shipFreq));
            }
        } else {
            return true;
        }
        return false;
    }

    @Override
    public boolean collision(BroadphaseCollisionData collision) {
        return true;
    }

    @Override
    public boolean collision(NarrowphaseCollisionData collision) {
        return true;
    }

    @Override
    public boolean collision(ManifoldCollisionData collision) {
        
        CollisionBody body1 = collision.getBody1();
        CollisionBody body2 = collision.getBody1();
        
        Fixture fixture1 = collision.getFixture1();
        Fixture fixture2 = collision.getFixture2();
        
        Manifold manifold = collision.getManifold();
        
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        if (this.isFlag(one) || this.isFlag(two)) {
            return this.collide(body1, fixture1, body2, fixture2, manifold, tpf.getTpf());
        }

        return true;

    }
}
