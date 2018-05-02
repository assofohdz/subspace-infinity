/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/**
 * State to queue the creation of entities. This is a state that will pace the
 * generation of entities (in case we want to create thousands in an instant)
 *
 * @author Asser
 */
public class EntityQueueState extends AbstractGameSystem {

    @Override
    protected void initialize() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }

    @Override
    public void update(SimTime tpf) {

    }

}
