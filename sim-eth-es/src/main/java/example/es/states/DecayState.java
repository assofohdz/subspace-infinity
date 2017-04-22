package example.es.states;

import com.jme3.app.Application;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.lemur.event.BaseAppState;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.ConnectionState;
import example.es.Decay;


/**
 *  General app state that watches entities with a Decay component
 *  and deletes them when their time is up.
 *
 *  @author    Asser Fahrenholz
 */
public class DecayState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet entities;

    @Override
    public void update(SimTime tpf) {
        entities.applyChanges();
        for(Entity e : entities)
        {
            Decay d = e.get(Decay.class);
            if (d.getPercent() >= 1.0) {
                //Entity decayingEntity = ed.getEntity(e.getId(), Position.class, ObjectType.class);

//                if (null != decayingEntity && decayingEntity.get(ObjectType.class).getTypeName(ed).equals(ObjectTypes.BULLET)) {
//                    //EXPLODEDEDE!!!
//                    EntityId explosion = ed.createEntity();
//                    ed.setComponents(explosion,
//                            new Position(decayingEntity.get(Position.class).getLocation(), new Quatd()),
//                            ObjectTypes.explosion(ed),
//                            new Decay(500));
//                }
                
                ed.removeEntity(e.getId());
            }
        }
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        entities = ed.getEntities(Decay.class);
    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
        entities.release();
        entities = null;
    }
}




