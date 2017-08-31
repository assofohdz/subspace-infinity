/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.BodyPosition;
import example.es.Buff;
import example.es.Damage;
import example.es.Decay;
import example.es.HealthChange;
import example.es.MobType;
import example.es.Position;
import example.es.ProjectileType;
import example.es.ViewType;
import example.es.ViewTypes;
import example.sim.GameEntities;
import example.sim.PhysicsListener;
import example.sim.SimpleBody;
import example.sim.SimplePhysics;
import java.util.List;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.manifold.ManifoldPoint;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Vector2;

/**
 *
 * @author Asser
 */
public class ProjectileCollisionState extends AbstractGameSystem implements CollisionListener, PhysicsListener {

    private SimplePhysics simplePhysics;
    private EntityData ed;
    private EntitySet projectiles;
    private EntitySet mobs;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        this.projectiles = ed.getEntities(ProjectileType.class, Damage.class);

        this.mobs = ed.getEntities(MobType.class);
    }

    @Override
    protected void terminate() {
        
    }

    @Override
    public void update(SimTime tpf) {
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

        //Handle the contact between mob and projectile
        if (mobs.getEntityIds().contains(two) && projectiles.getEntityIds().contains(one)) {
            List<ManifoldPoint> points = manifold.getPoints();
            ManifoldPoint point = points.get(0);

            Damage d = projectiles.getEntity(one).get(Damage.class);
            ed.setComponent(one, new Decay(0)); //Set it to be removed
            
            GameEntities.createHealthBuff(d.getDamage(), two, ed);
            
            return false;

        } else if (mobs.getEntityIds().contains(one) && projectiles.getEntityIds().contains(two)) {
            List<ManifoldPoint> points = manifold.getPoints();
            ManifoldPoint point = points.get(0);

            Damage d = projectiles.getEntity(two).get(Damage.class);
            ed.setComponent(two, new Decay(0)); //Set it to be removed

            GameEntities.createHealthBuff(d.getDamage(), one, ed);
            
            return false;
        }

        return true;
    }

    @Override
    public boolean collision(ContactConstraint contactConstraint) {
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
}
