/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.server;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.sim.GameEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author AFahrenholz
 */
public class InfinityGameEntities extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(InfinityGameEntities.class);
    private EntityData ed;
    private PhysicsSpace phys;
    private World world;

    @Override
    protected void initialize() {

    }

    @Override
    protected void terminate() {

    }

    @Override
    public void update(SimTime time) {

    }

    public EntityId createShipEntity(EntityId playerEntityId, Vec3d loc, double size, boolean dynamic) {
        EntityId result = ed.createEntity();
        ed.setComponents(result,
                new SpawnPosition(phys.getGrid(), loc),
                ShapeInfo.create("sphere", size, ed),
                new Mass(10),
                Gravity.ZERO);
        
        return result;
    }

}
