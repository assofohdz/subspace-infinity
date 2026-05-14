// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;

import infinity.es.Dead;

/** Stamps {@link Decay} on {@link Dead}-flagged entities to drive despawn. */
public class DeathSystem extends BaseInfinitySystem {

    private EntityData ed;
    private EntitySet dead;

    @Override
    protected void initialize() {
        ed = requireSystem(EntityData.class);

        dead = ed.getEntities(Dead.class);
    }

    @Override
    protected void terminate() {
        dead.release();
        dead = null;
    }

    @Override
    public void update(final SimTime tpf) {
        if (dead.applyChanges()) {
            for (final Entity e : dead) {
                ed.removeComponent(e.getId(), Dead.class);

                ed.setComponent(e.getId(), new Decay(tpf.getTime(), tpf.getTime()));
            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

}
