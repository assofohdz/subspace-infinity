/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.sim;

import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.EntityBodyFactory;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Grid;
import com.simsilica.mphys.AbstractShape;
import java.util.HashMap;

/**
 *
 * @author AFahrenholz
 */
public class InfinityMPhysSystem<S extends AbstractShape> extends MPhysSystem{
    
    private HashMap<EntityId,ShipDriver> driverIndex;
    
    public InfinityMPhysSystem(Grid zoneGrid, EntityBodyFactory bodyFactory) {
        super(zoneGrid, bodyFactory);
    }
    
    public void setDriverIndex(HashMap<EntityId,ShipDriver> index){
        this.driverIndex = index;
    }
    
    public ShipDriver getDriver(EntityId id){
        return driverIndex.get(id);
    }
    
}
