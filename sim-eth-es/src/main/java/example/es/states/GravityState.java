package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.PhysicsConstants;
import example.es.GravityWell;
import example.es.MassProperties;
import example.es.PhysicsShape;
import example.es.Position;
import example.es.WarpTouch;
import example.sim.SimpleBody;
import example.sim.EntityCollisionListener;
import example.sim.GameEntities;
import example.sim.SimplePhysics;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.collision.Fixture;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.manifold.ManifoldPoint;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.Torque;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Vector2;

/**
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
    private GravityWells wells;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

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
        simplePhysics.addCollisionListener(this);

        wells = new GravityWells(ed);
        wells.start();
    }

    @Override
    public void stop() {
        simplePhysics.removeCollisionListener(this);

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

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2) {
        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Penetration penetration) {
        return true;
    }

    //Contact manifold created by the manifold solver
    @Override
    public boolean collision(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Manifold manifold) {
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();
        
        if (wells.getObject(one) == fixture1 || wells.getObject(two) == fixture2) {
            if (wells.getObject(one) == fixture1) {
                createWormholeForce(one, two, body1, body2, manifold.getPoints().get(0));
            }

            if (wells.getObject(two) == fixture2) {
                createWormholeForce(two, one, body2, body1, manifold.getPoints().get(0));

            }
            return false;
        }

        return true; //Default, keep processing this event
    }

    private void createWormholeForce(EntityId wormholeEntityId, EntityId bodyEntityId, Body wormholeBody, Body body, ManifoldPoint mp) {
        Vec3d wormholeLocation = new Vec3d(wormholeBody.getTransform().getTranslationX(), wormholeBody.getTransform().getTranslationY(), 0);
        Vec3d bodyLocation = new Vec3d(body.getTransform().getTranslation().x, body.getTransform().getTranslation().y, 0); //TODO: Arena setting?
        
        GravityWell gravityWell = ed.getComponent(wormholeEntityId, GravityWell.class);
        //start applying gravity to other entity
        Force force = getWormholeGravityOnBody(time.getTpf(), gravityWell, wormholeLocation, bodyLocation);
        GameEntities.createForce(bodyEntityId, force, mp.getPoint(), ed);
    }

    //Contact constraint created
    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true; //Default, keep processing this event
        
        
    }

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

    private class GravityWells extends EntityContainer<BodyFixture> {

        public GravityWells(EntityData ed) {
            super(ed, GravityWell.class, Position.class, MassProperties.class, PhysicsShape.class);
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

            b.addFixture(bodyFixture);

            switch (gw.getGravityType()) {
                case GravityWell.PULL:
                    pullingWells.add(e.getId());
                    
                    break;
                case GravityWell.PUSH:
                    pushingWells.add(e.getId());

                    break;
            }

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

            ed.removeComponent(e.getId(), WarpTouch.class);

        }

    }
}
