/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.sim;

import com.simsilica.mphys.PhysicsSpace;

/**
 *
 * @author AFahrenholz
 */
public class InfinityPhysicsManager implements PhysicsManager{
    
    PhysicsSpace space;

    public InfinityPhysicsManager(PhysicsSpace space) {
        this.space = space;
    }
    
    @Override
    public PhysicsSpace getPhysics() {
        return space;
    }
}
