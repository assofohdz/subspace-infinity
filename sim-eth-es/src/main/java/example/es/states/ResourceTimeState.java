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
import example.GameConstants;
import example.es.Resource;
import example.es.ShipType;


/**
 *
 * @author ss
 */
public class ResourceTimeState extends AbstractGameSystem{

    private EntityData ed;
    private EntitySet eset;
    private double time_since_last_update;
    
    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        this.eset =  this.ed.getEntities(ShipType.class); 
    }

    @Override
    protected void terminate() {
        this.eset.release();
        this.eset = null;
    }
    
    @Override
    public void update(SimTime tpf) {
        // only update every RESOURCE_UPDATE_INTERVAL
        System.out.print("update");
        
        if (this.time_since_last_update > GameConstants.RESOURCE_UPDATE_INTERVAL){
            this.time_since_last_update = 0;
            
        
            eset.applyChanges();

            for (Entity e : this.eset) {
                Resource g = this.ed.getComponent(e.getId(), Resource.class);
                if (g == null){
                    this.ed.setComponent(e.getId(), new Resource( new int[]{(int) ( tpf.getTpf() * GameConstants.GOLD_PER_TIME)}));
                } else {
                    this.ed.setComponent(e.getId(), new Resource( new int[]{(int) ( tpf.getTpf() * GameConstants.GOLD_PER_TIME + g.getResources()[0])}));
                }
            }
        }
        // update time
        this.time_since_last_update +=  tpf.getTpf();
        System.out.print(this.time_since_last_update);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
    
}
