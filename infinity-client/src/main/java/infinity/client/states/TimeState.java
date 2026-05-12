// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;

import com.simsilica.ethereal.TimeSource;

import infinity.sim.TimeManager;

/** Locks {@code getTime()} to a snapshot taken at {@link #update} so interpolated visuals don't drift mid-frame. */
public class TimeState extends BaseAppState implements TimeManager {

    static Logger log = LoggerFactory.getLogger(TimeState.class);

    private TimeSource timeSource;
    private long frameTime;
    private long realTime;

    public TimeState() {
        log.info("Constructed TimeState");
    }

    public TimeState(final TimeSource timeSource) {
        this.timeSource = timeSource;
    }

    public void setTimeSource(final TimeSource timeSource) {
        this.timeSource = timeSource;
    }

    public TimeSource getTimeSource() {
        return timeSource;
    }

    @Override
    public long getTime() {
        return frameTime;
    }

    public long getRealTime() {
        return realTime;
    }

    @Override
    public void update(final float tpf) {
        if (timeSource != null) {
            frameTime = timeSource.getTime();
            realTime = System.nanoTime();
        }
    }

    @Override
    protected void initialize(final Application app) {
        return;
    }

    @Override
    protected void cleanup(final Application app) {
        return;
    }

    @Override
    protected void onEnable() {
        return;
    }

    @Override
    protected void onDisable() {
        return;
    }
}
