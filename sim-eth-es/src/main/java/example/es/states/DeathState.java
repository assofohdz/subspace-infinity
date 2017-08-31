/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.Dead;
import example.es.Decay;
import example.sim.SimplePhysics;

/**
 *
 * @author Asser
 */
public class DeathState extends AbstractGameSystem{

    private EntityData ed;
    private SimplePhysics simplePhysics;
    private EntitySet dead;
    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        this.dead = ed.getEntities(Dead.class);
    }

    @Override
    protected void terminate() {
        
    }

    @Override
    public void update(SimTime tpf) {
        if (dead.applyChanges()) {
            for(Entity e : dead){
                Dead d = e.get(Dead.class);
                ed.removeComponent(e.getId(), Dead.class);
                
                ed.setComponent(e.getId(), new Decay(0));
            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
