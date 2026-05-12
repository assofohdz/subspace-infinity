// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/** Queues entity creation so bulk spawns can be paced over multiple ticks. */
public class EntityQueueSystem extends AbstractGameSystem {

    @Override
    protected void initialize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void start() {
        return;
    }

    @Override
    public void stop() {
        return;
    }

    @Override
    public void update(final SimTime tpf) {
        return;
    }
}
