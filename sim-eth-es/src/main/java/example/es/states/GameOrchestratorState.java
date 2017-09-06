package example.es.states;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.MobType;
import example.es.SteeringPath;
import example.es.SteeringSeek;
import example.sim.GameEntities;
import example.sim.SimplePhysics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a state that holds the rules of the game. This is the state that will
 * create wave of mobs, tell other states how many healthpoints a mob has, how
 * fast a bullet flies etc.
 *
 * @author Asser
 */
public class GameOrchestratorState extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(GameOrchestratorState.class);

    //As a baseline, spawn 10 mobs per wave
    private final int baseline_mobCountPerWave = 10;
    //As a baseline, add a factor of mobs each wave
    private final int baseline_waveMobFactor = 2;
    //The wait time (in seconds) before the next wave, after the last wave has ended
    private final double baseline_waveWaitTime = 10; //10 seconds
    //As a baseline, end the game after this many waves
    private final int baseline_endWave = 10;

    private long currentWaveEndTime;
    private int currentWave = 0;

    private final int timetoplay = 300; //Game length in seconds

    private SimplePhysics simplePhysics;
    private EntityData ed;
    private EntitySet mobs;
    private EntityId baseId;
    private double timeSinceLastWave;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        //Keep track of how many mobs are in the game currently
        this.mobs = ed.getEntities(MobType.class);
    }

    @Override
    protected void terminate() {
        mobs.release();
        mobs = null;
    }

    @Override
    public void update(SimTime tpf) {

        mobs.applyChanges();

        if (mobs.isEmpty() && currentWave == baseline_endWave) {
            //You won the game
        } else if (mobs.isEmpty() && (timeSinceLastWave > baseline_waveWaitTime)) {
            //Continue with the game
            if (currentWave < baseline_endWave) {
                spawnWave();
                timeSinceLastWave = 0;
            }
        } else if (mobs.isEmpty()) {
            timeSinceLastWave += tpf.getTpf();
        }
    }

    @Override
    public void start() {
        startGame();
    }

    @Override
    public void stop() {
        endGame();
    }
    
    public int getWave(){
        return currentWave;
    }

    //Spawn the required waves for the game
    private void spawnWave() {
        log.debug("Spawning wave");
        //TODO: This could be done on a seperate thread so as not to hold the game thread
        for (int i = 0; i < baseline_mobCountPerWave * currentWave * baseline_waveMobFactor; i++) {
            spawnMob();
        }
        currentWave++;
        
        
        
    }

    private void spawnMob() {
        //TODO: Create mob, set it steering towards the base
        Vec3d randomSpawn = getMobSpawnPoint();
        log.debug("Spawning mob @ " + randomSpawn);
        EntityId mobId = GameEntities.createMob(randomSpawn, ed);

        ed.setComponent(mobId, new SteeringPath());
    }

    //Could probably use some more flexible system to spawn the base
    private void spawnBase() {
        Vec3d basePos = getBaseSpawnPoint();
        log.debug("Spawning base @ " + basePos);
        baseId = GameEntities.createBase(basePos, ed);
    }
    
    private void despawnBase(){
        ed.removeEntity(baseId);
    }

    private void startGame() {
        spawnBase();
    }

    private void endGame() {
        despawnBase();
    }

    private Vec3d getMobSpawnPoint() {
        Vec3d result = new Vec3d(Math.random() * 20 - 10, Math.random() * 20 - 10, 0);

        return result;
    }

    private Vec3d getBaseSpawnPoint() {
        Vec3d result = new Vec3d(30, 30, 0);

        return result;
    }
}
