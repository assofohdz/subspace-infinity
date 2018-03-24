package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.BodyPosition;
import example.es.Decay;
import example.es.ViewType;
import example.es.ViewTypes;
import example.es.Position;
import example.es.WeaponType;
import example.es.WeaponTypes;
import example.sim.CoreGameEntities;
import example.sim.SimplePhysics;
import org.dyn4j.geometry.Vector2;

/**
 * General app state that watches entities with a Decay component and deletes
 * them when their time is up.
 *
 * @author Asser Fahrenholz
 */
public class DecayState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet entities;
    private SimplePhysics simplePhysics;

    @Override
    public void update(SimTime tpf) {
        entities.applyChanges();
        for (Entity e : entities) {
            Decay d = e.get(Decay.class);
            if (d.getPercent() >= 1.0) {
                WeaponType t = ed.getComponent(e.getId(), WeaponType.class);

                if (t != null && (t.getTypeName(ed).equals(WeaponTypes.BOMB)
                        || t.getTypeName(ed).equals(WeaponTypes.GRAVITYBOMB))) { //TODO: Not sure if we should explode when we do not hit anything before out ttl is up
                    Vector2 bodyLocation = simplePhysics.getBody(e.getId()).getWorldCenter();
                    CoreGameEntities.createExplosion2(new Vec3d(bodyLocation.x, bodyLocation.y, 0), new Quatd().fromAngles(0, 0, Math.random() * 360), ed);
                }

                ed.removeEntity(e.getId());
            }
        }
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        entities = ed.getEntities(Decay.class);

        simplePhysics = getSystem(SimplePhysics.class);
    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
        entities.release();
        entities = null;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }
}
