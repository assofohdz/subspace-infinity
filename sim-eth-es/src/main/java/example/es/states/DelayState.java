/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.ArenaId;
import example.es.Delay;
import example.sim.GameEntities;

/**
 *
 * @author Asser
 */
public class DelayState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet delayEntities;

    @Override
    public void update(SimTime tpf) {
        if(delayEntities.applyChanges())
        {
            for(Entity e : delayEntities.getAddedEntities()){
                Delay d = e.get(Delay.class);
                
                if (tpf.getTime() > d.getScheduledTime()) {
                    ed.setComponents(e.getId(), (EntityComponent[]) d.getComponentSet().toArray());
                    
                    ed.removeComponent(e.getId(), Delay.class);
                }
            }
        }
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        delayEntities = ed.getEntities(Delay.class); //This filters all entities that have delayed components
    }

    @Override
    protected void terminate() {
        delayEntities.release();;
        delayEntities = null;
    }
    
    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }
}
