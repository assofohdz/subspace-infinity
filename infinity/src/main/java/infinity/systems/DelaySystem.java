/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
    public void update(SimTime tpf) {
        entities.applyChanges();
        for (Entity e : entities) {
            Delay d = e.get(Delay.class);
            if (d.getPercent() >= 1.0) {
                Iterator<EntityComponent> componentIterator = d.getDelayedComponents().iterator();
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
                }

                ed.removeComponent(e.getId(), Delay.class);
            }
        }
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        entities = ed.getEntities(Delay.class); // This filters all entities that have delayed components
    }

    @Override
    protected void terminate() {
        entities.release();
        entities = null;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }
}
