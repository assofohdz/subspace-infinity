// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.Dead;

/**
 * A state to keep track of dead entities. Will remove them from the game, but
 * can otherwise be used keep track of deaths with points, buffs etc.
 *
 * Update: Should be upgdated. As it is, it doesn't work with how the
 * DecaySystem works
 *
 * @author Asser
 */
public class DeathSystem extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet dead;

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);

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
                // final Dead d = e.get(Dead.class);
                ed.removeComponent(e.getId(), Dead.class);

                ed.setComponent(e.getId(), new Decay(tpf.getTime(), tpf.getTime()));
            }
        }
    }

    @Override
    public void start() {
        return;
    }

    @Override
    public void stop() {
        return;
    }

}
