package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.GravityWell;
import example.es.MassProperties;
import example.es.PhysicsShape;
import example.es.Position;
import example.es.WarpTo;
import example.es.WarpTouch;
import example.sim.SimplePhysics;
import java.util.HashSet;
import org.dyn4j.collision.manifold.Manifold;
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
public class WarpState extends AbstractGameSystem implements CollisionListener {

    private SimTime time;

    private EntityData ed;
    private EntitySet warpTouchEntities, warpToEntities;
    private SimplePhysics simplePhysics;
    private HashSet<BodyFixture> warpTouchFixtures;
    private Warpers warpers;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        warpTouchEntities = ed.getEntities(WarpTouch.class);
        warpToEntities = ed.getEntities(WarpTo.class);

    }

    @Override
    protected void terminate() {
        warpTouchEntities.release();
        warpTouchEntities = null;
    }

    @Override
    public void start() {
        simplePhysics.addCollisionListener(this);

        warpers = new Warpers(ed);
        warpers.start();
    }

    @Override
    public void stop() {
        simplePhysics.removeCollisionListener(this);

        warpers.stop();
        warpers = null;
    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;

        //warpTouchEntities.applyChanges();
        warpers.update();

        if (warpToEntities.applyChanges()) {
            for (Entity e : warpToEntities) {
                Body b = simplePhysics.getBody(e.getId());
                if (b != null) {
                    Vec3d targetLocation = ed.getComponent(e.getId(), WarpTo.class).getTargetLocation();

                    simplePhysics.getBody(e.getId()).getTransform().setTranslation(targetLocation.x, targetLocation.y);
                }
            }
        }
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

        if ((warpers.getObject(one) != null && warpers.getObject(one).getWarpFixture() == fixture1) || (warpers.getObject(two) != null && warpers.getObject(two).getWarpFixture() == fixture2)) {
            if (warpers.getObject(one) != null && warpers.getObject(one).getWarpFixture() == fixture1) {
                Vector2 targetLocation = warpers.getObject(one).getTargetLocation();
                body2.getTransform().setTranslation(targetLocation);
            }

            if (warpers.getObject(two) != null && warpers.getObject(two).getWarpFixture() == fixture2) {
                Vector2 targetLocation = warpers.getObject(two).getTargetLocation();
                body1.getTransform().setTranslation(targetLocation);
            }
            return false;
        }

        return true;
    }

    //Contact constraint created
    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
    }

    private class WarpFixture {

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

    }

    private class Warpers extends EntityContainer<WarpFixture> {

        public Warpers(EntityData ed) {
            super(ed, WarpTouch.class, Position.class, MassProperties.class, PhysicsShape.class);
        }

        @Override
        protected WarpFixture[] getArray() {
            return super.getArray();
        }

        @Override
        protected WarpFixture addObject(Entity e) {
            WarpTouch wt = e.get(WarpTouch.class);

            Body b = simplePhysics.getBody(e.getId());
            BodyFixture bf = b.getFixture(0);

            //return new Vector2(wt.getTargetLocation().x, wt.getTargetLocation().y);
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

    }
}
