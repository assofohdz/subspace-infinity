/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package basicTester;

import infinity.sim.AccountManager;
import infinity.sim.AdaptiveLoader;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;

/**
 *
 * @author AFahrenholz
 */
public class BasicTester extends BaseGameModule {

    public BasicTester(ChatHostedPoster chp, AccountManager am, AdaptiveLoader loader, ArenaManager arenas, TimeManager time, PhysicsManager physics) {
        super(chp, am, loader, arenas, time, physics);
    }

    @Override
    protected void initialize() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
