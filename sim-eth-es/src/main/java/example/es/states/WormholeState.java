package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.GravityWell;
import example.sim.SimpleBody;
import example.sim.EntityCollisionListener;
import example.sim.SimplePhysics;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Vector2;

/**
 *
 * @author Asser
 */
public class WormholeState extends AbstractGameSystem implements EntityCollisionListener{

    private SimTime time;

    private EntityData ed;
    private EntitySet gravityWells;
    //A set to map from the pulling gravity wells to a pushing gravity well
    private java.util.Map<EntityId, Vector2> wormholeLinks;
    private SimplePhysics simplePhysics;
    private HashSet<EntityId> pushingWells, pullingWells;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        gravityWells = ed.getEntities(GravityWell.class); //Any object that has a body position can be moved in space
        
        wormholeLinks = new ConcurrentHashMap<>();
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

    }

    @Override
    public void stop() {
    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;

        if (gravityWells.applyChanges()) {
            applyGravities();
        }
    }

    private void applyGravities() {
        //Handle entities that has gravity added
        for (Entity e : gravityWells) {
            SimpleBody b = simplePhysics.getBody(e.getId());
            if (b != null && !(pushingWells.contains(e.getId()) || pullingWells.contains(e.getId()))) {
                GravityWell gravity = ed.getComponent(e.getId(), GravityWell.class);

                b.addFixture(new Circle(gravity.getDistance()));
                
                if (gravity.getForce() < 0) {
                    pushingWells.add(e.getId());

                    if (pullingWells.size() > 0) {
                        //Create link from random pulling well to this well
                        wormholeLinks.put(getRandomObject(pullingWells), b.getTransform().getTranslation());
                    }

                } else {
                    pullingWells.add(e.getId());

                    //Check if we can find a wormhole to fit on the other end
                    if (pushingWells.size() > 0) {
                        //Create link from this well to a random pushing well
                        wormholeLinks.put(e.getId(), simplePhysics.getBody(getRandomObject(pushingWells)).getTransform().getTranslation());
                    }
                }
            }
        }
        //TODO: Handle changing and removing gravities on entities

        for (Entity e : gravityWells.getRemovedEntities()) {
            if (pullingWells.contains(e.getId()) && wormholeLinks.containsKey(e.getId())) {
                wormholeLinks.remove(e.getId());
                pullingWells.remove(e.getId());
            }
        }
    }

    private <T extends Object> T getRandomObject(Collection<T> from) {
        Random rnd = new Random();
        int i = rnd.nextInt(from.size());
        return (T) from.toArray()[i];
    }

    public Vector2 getWarpTargetLocation(EntityId sourceWormholeEntityId) {
        if (wormholeLinks.containsKey(sourceWormholeEntityId)) {
            return wormholeLinks.get(sourceWormholeEntityId);
        }
        return null;
    }
    
    public boolean isPushingWell(EntityId eId){
        return pushingWells.contains(eId);
    }
    
    public boolean isPullingWell(EntityId eId){
        return pullingWells.contains(eId);
    }

    @Override
    public void collision(EntityId eId1, EntityId eId2) {
        if (isPushingWell(eId1)) {
            
        }
    }
    
    public boolean hasGravity(EntityId eId){
        return isPullingWell(eId) || isPullingWell(eId);
    }
}
