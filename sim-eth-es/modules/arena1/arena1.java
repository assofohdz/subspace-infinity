package arena1;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import example.es.MobType;
import example.es.SteeringPath;
import example.sim.BaseGameModule;
import example.sim.CommandListener;
import example.sim.ModuleGameEntities;
import example.sim.events.ShipEvent;
import java.net.URL;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a state that holds the rules of a game. This is the state that will
 * create wave of mobs, tell other states how many healthpoints a mob has, how
 * fast a bullet flies etc.
 *
 * @author Asser
 */
public class arena1 extends BaseGameModule implements CommandListener {

    static Logger log = LoggerFactory.getLogger(arena1.class);

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

    private EntityData ed;
    private EntitySet mobs;
    private EntityId baseId;
    private double timeSinceLastWave;
    private final Pattern arena1Command = Pattern.compile("\\~arena1\\s(\\w+)");

    public arena1(Ini settings) {
        super(settings);
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

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
        timeSinceLastWave += tpf.getTpf();

        mobs.applyChanges();

        if (mobs.isEmpty() && currentWave == baseline_endWave) {
            //You won the game
        } else if (timeSinceLastWave > baseline_waveWaitTime && mobs.isEmpty()) {
            //Continue with the game
            if (currentWave < baseline_endWave) {
                spawnWave();
            }
        }
    }

    @Override
    public void start() {
        EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        startGame();
    }

    @Override
    public void stop() {
        EventBus.removeListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        endGame();
    }

    //Spawn the required waves for the game
    private void spawnWave() {
        log.debug("Spawning wave");
        //TODO: This could be done on a seperate thread so as not to hold the game thread
        for (int i = 0; i < baseline_mobCountPerWave * currentWave * baseline_waveMobFactor; i++) {
            spawnMob();
        }
        currentWave++;
        timeSinceLastWave = 0;
    }

    private void spawnMob() {
        //TODO: Create mob, set it steering towards the base
        Vec3d randomSpawn = getMobSpawnPoint();
        log.debug("Spawning mob @ " + randomSpawn);
        //Create mob ourselves
        EntityId mobId = ModuleGameEntities.createMob(randomSpawn, ed, this.getSettings());

        ed.setComponent(mobId, new SteeringPath());
    }

    //Could probably use some more flexible system to spawn the base
    private void spawnBase() {
        Vec3d basePos = getBaseSpawnPoint();
        log.debug("Spawning base @ " + basePos);
        URL s = ModuleGameEntities.class.getResource("ModuleGameEntities.class");
        baseId = ModuleGameEntities.createBase(basePos, ed, this.getSettings());
    }

    private EntityId getBaseId() {
        return baseId;
    }

    private void startGame() {
        spawnBase();
    }

    private void endGame() {

    }

    private Vec3d getMobSpawnPoint() {
        Vec3d result = new Vec3d(Math.random() * 20 - 10, Math.random() * 20 - 10, 0);

        return result;
    }

    private Vec3d getBaseSpawnPoint() {
        Vec3d result = new Vec3d(30, 30, 0);

        return result;
    }

    //Event handling
    void onShipSpawned(ShipEvent shipEvent) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    //Event handling
    void onShipDestroyed(ShipEvent shipEvent) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Be sure to map all patterns to a method that should be called
     *
     * @return a map of patterns to method
     */
    @Override
    public HashMap<Pattern, Consumer<String>> getPatternConsumers() {
        HashMap<Pattern, Consumer<String>> map = new HashMap<>();
        map.put(arena1Command, (s) -> this.messageHandler(s));
        //Register all the patterns and consuming methods that needs to hook into the chat service
        return map;
    }

    /**
     * Handle the message events
     *
     * @param s
     */
    public void messageHandler(String s) {
    }
}
