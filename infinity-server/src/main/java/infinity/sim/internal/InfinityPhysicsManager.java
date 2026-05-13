// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.internal;

import com.simsilica.mphys.PhysicsSpace;
import infinity.sim.PhysicsManager;

/** {@link PhysicsManager} adapter over a {@link PhysicsSpace}. */
public class InfinityPhysicsManager implements PhysicsManager {

    PhysicsSpace<?, ?> space;

    public InfinityPhysicsManager(final PhysicsSpace<?, ?> space) {
        this.space = space;
    }

    @Override
    public PhysicsSpace<?, ?> getPhysics() {
        return space;
    }
}
