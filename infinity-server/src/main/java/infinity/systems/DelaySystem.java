// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import java.util.Iterator;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.Delay;

/**
 *
 * @author Asser
 */
public class DelaySystem extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet entities;

    @Override
    public void update(final SimTime tpf) {
        entities.applyChanges();
        for (final Entity e : entities) {
            final Delay d = e.get(Delay.class);
            if (d.getPercent() >= 1.0) {
                final Iterator<EntityComponent> componentIterator = d.getDelayedComponents().iterator();
                switch (d.getType()) {
                case Delay.REMOVE:
                    while (componentIterator.hasNext()) {
                        ed.removeComponent(e.getId(), componentIterator.next().getClass());
                    }
                    break;
                case Delay.SET:

                    while (componentIterator.hasNext()) {
                        ed.setComponent(e.getId(), componentIterator.next());
                    }
                    break;
                default:
                    break;
                }

                ed.removeComponent(e.getId(), Delay.class);
            }
        }
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);

        entities = ed.getEntities(Delay.class); // This filters all entities that have delayed components
    }

    @Override
    protected void terminate() {
        entities.release();
        entities = null;
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
