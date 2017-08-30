package example.es.states;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.MobType;
import example.sim.SimplePhysics;

/**
 * This is a state that holds the rules of the game. This is the state that will
 * create wave of mobs, tell other states how many healthpoints a mob has, how
 * fast a bullet flies etc.
 *
 * @author Asser
 */
public class GameOrchestratorState extends AbstractGameSystem {

    //As a baseline, spawn 10 mobs per wave
    private final int baseline_mobCountPerWave = 10;
    //As a baseline, add a factor of mobs each wave
    private final int baseline_waveMobFactor = 2;

    private final int baseline_endWave = 10; //Game length in waves
    private int currentWave = 0;

    private final int timetoplay = 300; //Game length in seconds

    private SimplePhysics simplePhysics;
    private EntityData ed;
    private EntitySet mobs;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        //Keep track of how many mobs are in the game currently
        this.mobs = ed.getEntities(MobType.class);
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(SimTime tpf) {
        //Here we should account for the game type

        if (mobs.applyChanges()) {
            //If there are no mobs, start spawning next wave
            if (mobs.size() == 0) {

                currentWave++;
                if (currentWave < baseline_endWave) {
                    spawnWave(currentWave);
                }
            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    //Spawn the required waves for the game
    private void spawnWave(int wave) {
        //TODO: This could be done on a seperate thread so as not to hold the game thread
        for (int i = 0; i < baseline_mobCountPerWave * currentWave * baseline_waveMobFactor; i++) {
            //TODO: Create mob, set it steering towards the base
        }
    }
}
