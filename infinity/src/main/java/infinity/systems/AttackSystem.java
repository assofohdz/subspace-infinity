/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.input.AttackInput;
import infinity.sim.PlayerDriver;

/**
 *
 * @author AFahrenholz
 */
public class AttackSystem extends AbstractGameSystem {
    
    private EntityData ed;
    private MPhysSystem<MBlockShape> physics;
    private PhysicsSpace<EntityId, MBlockShape> space;
    
    private EntitySet attackInput;

    @Override
    public void stop() {
        super.stop(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(SimTime time) {
        super.update(time); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void start() {
        super.start(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
        }
        this.physics = (MPhysSystem<MBlockShape>) getSystem(MPhysSystem.class);
        if (physics == null) {
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }

        this.space = physics.getPhysicsSpace();
        this.attackInput = ed.getEntities(AttackInput.class);
    }

    @Override
    protected void terminate() {
        this.attackInput.release();
        this.attackInput = null;
    }
    
    /**
     * All moving ships will be mapped to a driver. We use this to lookup the
     * drivers when we need to fire a weapon on that ship
     */
    private class AttackContainer extends EntityContainer<RigidBody> {

        public AttackContainer(EntityData ed) {
            super(ed, AttackInput.class);
        }
        
        @Override
        protected RigidBody addObject(Entity e) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void updateObject(RigidBody object, Entity e) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected void removeObject(RigidBody object, Entity e) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
