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

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.Damage;
import com.simsilica.es.common.Decay;
import infinity.api.es.MobType;
import infinity.api.es.Parent;
import infinity.api.es.WeaponType;
import infinity.api.sim.ModuleGameEntities;
import infinity.sim.PhysicsListener;
import infinity.sim.SimpleBody;
import infinity.sim.SimplePhysics;
import java.util.List;
import org.dyn4j.collision.CollisionBody;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.manifold.ManifoldPoint;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.world.BroadphaseCollisionData;
import org.dyn4j.world.ManifoldCollisionData;
import org.dyn4j.world.NarrowphaseCollisionData;
import org.dyn4j.world.listener.CollisionListener;

/**
 * This state handles projectiles and their collisions. Makes sure that damage
 * is properly transfered from projectile to target
 *
 * @author Asser
 */
public class ProjectileCollisionState extends AbstractGameSystem implements CollisionListener, PhysicsListener {

    private SimplePhysics simplePhysics;
    private EntityData ed;
    private EntitySet projectiles;
    private EntitySet mobs;
    private SimTime time;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        this.projectiles = ed.getEntities(WeaponType.class, Damage.class, Parent.class);

        this.mobs = ed.getEntities(MobType.class);

    }

    @Override
    protected void terminate() {

    }

    @Override
    public void update(SimTime tpf) {
        this.time = tpf;

        mobs.applyChanges();
        projectiles.applyChanges();

    }

    @Override
    public void start() {
        simplePhysics.addCollisionListener(this);
        simplePhysics.addPhysicsListener(this);
    }

    @Override
    public void stop() {
        simplePhysics.removeCollisionListener(this);
        simplePhysics.removePhysicsListener(this);
    }

    /**
     * Here we filter out projectile collisions for projectile hitting their
     * owners TODO: Filter out for team members as well
     */
    @Override
    public boolean collision(BroadphaseCollisionData collision) {
        CollisionBody body1 = collision.getBody1();
        CollisionBody body2 = collision.getBody2();

        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        if (projectiles.getEntityIds().contains(one) && projectiles.getEntity(one).get(Parent.class).getParentEntity() == two) {
            return false;
        } else if (projectiles.getEntityIds().contains(two) && projectiles.getEntity(two).get(Parent.class).getParentEntity() == one) {
            return false;
        }

        return true;
    }

    @Override
    public boolean collision(ManifoldCollisionData collision) {
        CollisionBody body1 = collision.getBody1();
        CollisionBody body2 = collision.getBody2();
        
        Manifold manifold = collision.getManifold();
        
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        //Handle the contact between mob and projectile
        if (mobs.getEntityIds().contains(two) && projectiles.getEntityIds().contains(one)) {
            List<ManifoldPoint> points = manifold.getPoints();
            ManifoldPoint point = points.get(0);

            Damage d = projectiles.getEntity(one).get(Damage.class);
            ed.setComponent(one, new Decay(time.getTime(), time.getTime())); //Set it to be removed

            ModuleGameEntities.createHealthBuff(d.getDamage(), two, ed, time.getTime());

            return false;

        } else if (mobs.getEntityIds().contains(one) && projectiles.getEntityIds().contains(two)) {
            List<ManifoldPoint> points = manifold.getPoints();
            ManifoldPoint point = points.get(0);

            Damage d = projectiles.getEntity(two).get(Damage.class);
            ed.setComponent(two, new Decay(time.getTime(), time.getTime())); //Set it to be removed

            ModuleGameEntities.createHealthBuff(d.getDamage(), one, ed, time.getTime());

            return false;
        }

        return true;
    }

    @Override
    public void beginFrame(SimTime time) {

    }

    @Override
    public void addBody(SimpleBody body) {
    }

    @Override
    public void updateBody(SimpleBody body) {
    }

    @Override
    public void removeBody(SimpleBody body) {
    }

    @Override
    public void endFrame(SimTime time) {
    }

    @Override
    public boolean collision(NarrowphaseCollisionData collision) {
        return true;
    }
}
