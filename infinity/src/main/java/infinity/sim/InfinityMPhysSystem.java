/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.sim;

import com.google.common.base.Function;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.EntityBodyFactory;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Grid;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractShape;
import com.simsilica.mphys.RigidBody;
import java.util.HashMap;

/**
 *
 * @author AFahrenholz
 */
public class InfinityMPhysSystem<S extends AbstractShape> extends MPhysSystem{
    
    private HashMap<EntityId,RigidBody> bodyIndex;
    
    public InfinityMPhysSystem(Grid zoneGrid, EntityBodyFactory bodyFactory) {
        super(zoneGrid, bodyFactory);
    }
}
