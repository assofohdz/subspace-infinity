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
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.Decay;
import infinity.api.es.WeaponType;
import infinity.api.es.WeaponTypes;
import infinity.api.sim.ModuleGameEntities;
import infinity.sim.SimplePhysics;
import org.dyn4j.geometry.Vector2;

/**
 * General app state that watches entities with a Decay component and deletes
 * them when their time is up.
 *
 * @author Asser Fahrenholz
 */
public class DecayState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet entities;
    private SimplePhysics simplePhysics;

    @Override
    public void update(SimTime tpf) {
        entities.applyChanges();
        for (Entity e : entities) {
            Decay d = e.get(Decay.class);
            if (d.getPercent() >= 1.0) {
                WeaponType t = ed.getComponent(e.getId(), WeaponType.class);

                if (t != null && (t.getTypeName(ed).equals(WeaponTypes.BOMB)
                        || t.getTypeName(ed).equals(WeaponTypes.GRAVITYBOMB))) { //TODO: Not sure if we should explode when we do not hit anything before out ttl is up
                    Vector2 bodyLocation = simplePhysics.getBody(e.getId()).getWorldCenter();

                    //Will seem off on client side, because client is showing the bomb a few timesteps behind where it really is
                    EntityId eId = ModuleGameEntities.createExplosion2(new Vec3d(bodyLocation.x, bodyLocation.y, 0), new Quatd().fromAngles(0, 0, Math.random() * 360), ed);
                    ModuleGameEntities.createExplosionSound(eId, new Vec3d(bodyLocation.x, bodyLocation.y, 0), ed);
                }

                ed.removeEntity(e.getId());
            }
        }
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        entities = ed.getEntities(Decay.class);

        simplePhysics = getSystem(SimplePhysics.class);
    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
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
