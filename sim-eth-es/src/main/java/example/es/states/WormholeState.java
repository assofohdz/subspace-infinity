package example.es.states;

import com.jme3.math.Vector3f;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.es.filter.OrFilter;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.BodyPosition;
import example.es.MassProperties;
import example.es.ObjectType;
import example.es.ObjectTypes;
import example.es.PhysicsForce;
import example.es.Position;
import example.es.GravityWell;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.Torque;

/**
 *
 * @author Asser
 */
public class WormholeState extends AbstractGameSystem {

    private SimTime time;

    private EntityData ed;
    private EntitySet wormholes;
    private EntitySet bodies;
    private java.util.Map<EntityId, HashSet<EntityId>> wormholeMap;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        ComponentFilter wormholeFilter = FieldFilter.create(ObjectType.class, "type", ObjectTypes.wormhole(ed).getType());
        ComponentFilter shipsFilter = FieldFilter.create(ObjectType.class, "type", ObjectTypes.ship(ed).getType());
        ComponentFilter bombsFilter = FieldFilter.create(ObjectType.class, "type", ObjectTypes.bomb(ed).getType());
        ComponentFilter bulletsFilter = FieldFilter.create(ObjectType.class, "type", ObjectTypes.bullet(ed).getType());
        
        ComponentFilter filter = OrFilter.create(
                ObjectType.class, 
                shipsFilter, 
                bombsFilter, 
                bulletsFilter,
                wormholeFilter);
        
        bodies = ed.getEntities(filter, ObjectType.class); //Any object that has a body position can be moved in space

        wormholeMap = new ConcurrentHashMap<>();
    }

    @Override
    protected void terminate() {
        bodies.release();
        wormholes.release();

        bodies = null;
        wormholes = null;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }

    @Override
    public void update(SimTime tpf) {
        wormholes.applyChanges();
        bodies.applyChanges();
        
        time = tpf;

        for (Entity bodyEntity : bodies.getChangedEntities()) {
            Vec3d bodyEntityLocation = new Vec3d(bodyEntity.get(BodyPosition.class).getFrame(time.getFrame()).getPosition(time.getTime()));

            for (Entity wormholeEntity : wormholes) {
                GravityWell wormhole = wormholeEntity.get(GravityWell.class);
                Vec3d wormholeLocation = new Vec3d(wormholeEntity.get(BodyPosition.class).getFrame(time.getFrame()).getPosition(time.getTime()));
                
                double gravityDistance = wormhole.getDistance();

                if (isNearby(wormholeLocation, bodyEntityLocation, gravityDistance)) {
                    Vec3d difference = wormholeLocation.subtract(bodyEntityLocation);

                    Vec3d gravity = difference.normalize().multLocal(time.getTpf());

                    double distance = difference.length();
                    double wormholeGravity = wormhole.getForce();

                    gravity.multLocal(wormholeGravity);
                    gravity.multLocal(gravityDistance / distance);

                    Force force = new Force(gravity.x, gravity.y);

                    PhysicsForce pf = new PhysicsForce(bodyEntity.getId(), force, new Torque());

                    bodyEntity.set(pf);
                }
            }
        }
    }

    private boolean isNearby(Vec3d a, Vec3d b, double distance) {
        return a.distanceSq(b) <= distance * distance;
    }
}
