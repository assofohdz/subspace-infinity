// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.sim.TimeManager;

/**
 *
 * @author AFahrenholz
 */
public class InfinityTimeSystem extends AbstractGameSystem implements TimeManager {

    long time;

    @Override
    public void stop() {
        super.stop(); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(final SimTime simTime) {
        super.update(simTime); // To change body of generated methods, choose Tools | Templates.
        time = simTime.getTime();
    }

    @Override
    public void start() {
        super.start(); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    protected void initialize() {
        return;
    }

    @Override
    protected void terminate() {
        return;
    }
}
