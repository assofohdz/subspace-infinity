package example.es.states;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.MapTileType;
import example.es.MassProperties;
import example.es.ObjectType;
import example.es.ObjectTypes;
import example.es.Position;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Vector2;

/**
 *
 * @author Asser
 */
public class BlackHoleState extends AbstractGameSystem {

    private SimTime time;

    private java.util.Map<Circle, EntityId> index = new ConcurrentHashMap<>(); //Map black hole entities to dyn4j circle shape that we can use for bounds detection
    private EntityData ed;
    private BlackHoleState.BodyContainer blackHoleContainer;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void start() {
        blackHoleContainer = new BlackHoleState.BodyContainer(ed);
        blackHoleContainer.start();
    }

    @Override
    public void stop() {
        blackHoleContainer.stop();
        blackHoleContainer = null;
    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;
    }

    private class BodyContainer extends EntityContainer<Circle> {

        public BodyContainer(EntityData ed) {
            //Here we filter all entities that has object type black hole, a position and a mass (mass is used to indicate power of said black hole in this case)
            super(ed, FieldFilter.create(ObjectType.class, "type", ed.getStrings().getStringId(ObjectTypes.WORMHOLE, true)), ObjectType.class, Position.class, MassProperties.class);
        }

        @Override
        protected Circle[] getArray() {
            return super.getArray();
        }

        @Override
        protected Circle addObject(Entity e) {
            Position pos = e.get(Position.class);
            MassProperties mass = e.get(MassProperties.class);

            Circle c = new Circle(mass.getMass());
            c.translate(pos.getLocation().x, pos.getLocation().y); //Set this black hole in a fictional physical universe (ie. we do not add it to the physics)

            index.put(c, e.getId());

            return c;
        }

        @Override
        protected void updateObject(Circle object, Entity e) { //We allow black holes to be moved
            Position pos = e.get(Position.class);
            MassProperties mass = e.get(MassProperties.class);

            if (mass.getMass() != object.getRadius()) {
                //Apparently, there is no 'setRadius' method
                object = new Circle(mass.getMass());
            }

            object.translate(pos.getLocation().x, pos.getLocation().y);
        }

        @Override
        protected void removeObject(Circle object, Entity e) {
            removeCircle(object);
        }
    }

    protected boolean removeCircle(Circle c) {
        EntityId result = index.remove(c);
        if (result != null) {
            return true;
        }
        return false;
    }

    public EntityId getEntityId(Circle c) {
        return index.get(c);
    }
}
