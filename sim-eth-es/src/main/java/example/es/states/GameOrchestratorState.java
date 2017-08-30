package example.es.states;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/**
 * This is a state that holds the rules of the game. This is the state that will
 * create wave of mobs, tell other states how many healthpoints a mob has, how
 * fast a bullet flies etc.
 *
 * @author Asser
 */
public class GameOrchestratorState extends AbstractGameSystem {

    @Override
    protected void initialize() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(SimTime tpf) {

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
