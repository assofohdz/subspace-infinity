// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/** Queues entity creation so bulk spawns can be paced over multiple ticks. */
public class EntityQueueSystem extends AbstractGameSystem {

    private static final String NOT_SUPPORTED = "Not supported yet.";

    @Override
    protected void initialize() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public void update(final SimTime tpf) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }
}
