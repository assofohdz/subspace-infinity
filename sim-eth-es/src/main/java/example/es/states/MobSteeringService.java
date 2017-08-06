package example.es.states;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.MobType;

/**
 *
 * @author Asser
 */
public class MobSteeringService extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet mobs;

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        mobs = ed.getEntities(MobType.class); //Find all mobs
    }

    @Override
    protected void terminate() {
        //Release reader object
        // Release the entity set we grabbed previously
        mobs.release();
        mobs = null;
    }

    @Override
    public void update(SimTime tpf) {
        if (mobs.applyChanges()) {

        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
