package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.Flag;
import example.es.Frequency;
import example.sim.SimplePhysics;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.contact.ContactConstraint;

/**
 *
 * @author Asser
 */
public class FlagStateServer extends AbstractGameSystem implements CollisionListener{

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

    public boolean collide(org.dyn4j.dynamics.Body body1, BodyFixture fixture1, org.dyn4j.dynamics.Body body2, BodyFixture fixture2, Manifold manifold, double tpf) {
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

        if (this.isFlag(one) || this.isFlag(two)) {
            return this.collide(body1, fixture1, body2, fixture2, manifold, tpf.getTpf());
        }
        
        return true;
    }

    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
    }
}
