package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.Position;
import example.es.TowerType;
import example.sim.GameEntities;
import example.sim.SimplePhysics;

/**
 * State
 *
 * @author Asser
 */
public class TowerState extends AbstractGameSystem {

    private EntityData ed;
    private SimplePhysics simplePhysics;
    private EntitySet towers;

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        this.towers = ed.getEntities(TowerType.class, Position.class);
    }

    @Override
    protected void terminate() {
        //Release reader object
        towers.release();
        towers = null;
    }

    @Override
    public void update(SimTime tpf) {
        towers.applyChanges();
        for (Entity e : towers.getAddedEntities()) {

        }

        for (Entity e : towers.getChangedEntities()) {

        }

        for (Entity e : towers.getRemovedEntities()) {

        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    public void editTower(double x, double y) {
        //TODO: Perform collission check, to see if placing a tower would overlap with existing towers (or mobs)
        GameEntities.createTower(new Vec3d(x, y, 0), ed);
    }
}
