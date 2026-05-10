// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;

import com.simsilica.ethereal.TimeSource;

import infinity.sim.TimeManager;

/**
 * Provides a consistent frame time to the classes that want it. Time inevitably
 * marches forward except in the case of this state. When update() is called,
 * the frame time is locked such that getTime() will return the same value until
 * after the next update(). This makes sure that small inter-frame time
 * differences don't creep into interpolated visuals.
 *
 * For a simple game example like this, it isn't really necessary but it's a
 * good pattern to follow.
 *
 * @author Paul Speed
 */
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
