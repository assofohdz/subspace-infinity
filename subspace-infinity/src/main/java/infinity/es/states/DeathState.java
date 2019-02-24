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
package infinity.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.Dead;
import com.simsilica.es.common.Decay;
import infinity.sim.SimplePhysics;

/**
 * A state to keep track of dead entities. Will remove them from the game, but
 * can otherwise be used keep track of deaths with points, buffs etc.
 * 
 * Update: Should be upgdated. As it is, it doesn't work with how the DecaySystem works
 *
 * @author Asser
 */
public class DeathState extends AbstractGameSystem {

    private EntityData ed;
    private SimplePhysics simplePhysics;
    private EntitySet dead;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.simplePhysics = getSystem(SimplePhysics.class);

        this.dead = ed.getEntities(Dead.class);
    }

    @Override
    protected void terminate() {

    }

    @Override
    public void update(SimTime tpf) {
        if (dead.applyChanges()) {
            for (Entity e : dead) {
                Dead d = e.get(Dead.class);
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
