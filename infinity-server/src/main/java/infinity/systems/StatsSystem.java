// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.server.GameServer;

/** Periodically logs {@link GameServer#logStats} via a fixed-rate timer. */
public class StatsSystem extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(StatsSystem.class);
    public static final int LOGINTERVALMS = 10000;
    private final GameServer server;
    private Timer timer;
    SimTime localTime;

    public StatsSystem(final GameServer server) {
        this.server = server;
    }

    @Override
    protected void initialize() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new Task(server), LOGINTERVALMS, LOGINTERVALMS);
    }

    @Override
    protected void terminate() {
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public void update(final SimTime tpf) {
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

        public Task(final GameServer server) {
            localServer = server;
        }

        int count = 1;

        @Override
        public void run() {
            localServer.logStats();
        }
    }

}
