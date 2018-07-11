/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.states;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.net.server.GameServer;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author asser
 */
public class ServerStatsState extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(ServerStatsState.class);
    /**
     * the interval between logging stats to console
     */
    public static final int LOGINTERVALMS = 10000;
    private final GameServer server;
    private Timer timer;
    SimTime localTime;

    public ServerStatsState(GameServer server) {
        this.server = server;
    }

    @Override
    protected void initialize() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new Task(server), LOGINTERVALMS, LOGINTERVALMS);  
    }

    @Override
    protected void terminate() {

    }

    @Override
    public void update(SimTime tpf) {
        localTime = tpf;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    class Task extends TimerTask {

        private final GameServer localServer;

        public Task(GameServer server){
            this.localServer = server;
        }
        
        int count = 1;

        // run is a abstract method that defines task performed at scheduled time.
        @Override
        public void run() {
            log.info("-------------- Logging stats --------------");
            localServer.logStats();
        }
    }

}
