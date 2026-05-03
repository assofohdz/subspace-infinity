// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

/**
 * State to queue the creation of entities. This is a state that will pace the
 * generation of entities (in case we want to create thousands in an instant)
 *
 * @author Asser
 */
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
