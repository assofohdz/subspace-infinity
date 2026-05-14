// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.sim.TimeManager;

/** Sim-tick time accessor via {@link TimeManager}. */
public class InfinityTimeSystem extends AbstractGameSystem implements TimeManager {

    long time;

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void update(final SimTime simTime) {
        super.update(simTime);
        time = simTime.getTime();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    protected void initialize() {
        // no-op: no EntitySets or services to wire; time is read from SimTime in update()
    }

    @Override
    protected void terminate() {
        // no-op: no resources held
    }
}
