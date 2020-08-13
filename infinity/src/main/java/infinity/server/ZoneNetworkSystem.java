/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.es.EntityId;
import com.simsilica.ethereal.zone.ZoneManager;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.ObjectStatusListener;
import com.simsilica.mphys.AbstractShape;
import com.simsilica.mphys.PhysicsListener;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;

/**
 * A game system that registers a listener with the SimplePhysics system and
 * then forwards those events to the SimEtheral zone manager, which in turn will
 * package them up for the clients in an efficient way.
 *
 * @author Paul Speed
 */
public class ZoneNetworkSystem<S extends AbstractShape> extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(ZoneNetworkSystem.class);

    private final ZoneManager zones;
    private final PhysicsObserver physicsObserver = new PhysicsObserver();

    public ZoneNetworkSystem(final ZoneManager zones) {
        this.zones = zones;
    }

    @Override
    protected void initialize() {
        // getSystem(PhysicsSpace.class, true).addPhysicsListener(physicsObserver);
        getSystem(MPhysSystem.class).addPhysicsListener(physicsObserver);
        getSystem(MPhysSystem.class).getBinEntityManager().addObjectStatusListener(physicsObserver);
    }

    @Override
    protected void terminate() {
        // getSystem(PhysicsSpace.class, true).removePhysicsListener(physicsObserver);
        getSystem(MPhysSystem.class).addPhysicsListener(physicsObserver);
        getSystem(MPhysSystem.class).getBinEntityManager().addObjectStatusListener(physicsObserver);
    }

    /**
     * Listens for changes in the physics objects and sends them to the zone
     * manager.
     */
    private class PhysicsObserver implements PhysicsListener<EntityId, S>, ObjectStatusListener<S> {

        // private final Vector3f posf = new Vector3f();
        // private final Quaternion orientf = new Quaternion();

        // private final Vec3d pos = new Vec3d();
        // private final Quatd orient = new Quatd();

        // We probably won't have many zones, if we even have more than one.
        // The physics objects do not provide any sort of accurate bounds so
        // we'll guess at a size that is "big enough" for any particular mobile
        // object. 2x2x2 meters should be good enough... until it isn't.
        // private final AaBBox box = new AaBBox(1);

        public PhysicsObserver() {
            super();
        }

        @Override
        public void startFrame(final long frameTime, final double stepSize) {
            zones.beginUpdate(frameTime);
        }

        @Override
        public void endFrame() {
            zones.endUpdate();
        }

        @Override
        public void update(final RigidBody<EntityId, S> body) {
            if (log.isTraceEnabled()) {
                log.trace("update(" + body.id + ", " + body.isSleepy() + ")");
            }
            final boolean active = !body.isSleepy();
//log.info("update body:" + body.id + "  bounds:" + body.getWorldBounds()
//        + "  cog:" + body.shape.getMass().getCog()
//        + "  shape info:" + body.shape.getCenter() + "  radius:" + body.shape.getRadius()
//        + "  cog bounds:" + body.shape.getCogBounds());
            zones.updateEntity(Long.valueOf(body.id.getId()), active, body.position, body.orientation,
                    body.getWorldBounds());
        }

        @Override
        public void objectLoaded(final EntityId id, final RigidBody<EntityId, S> body) {
            if (log.isTraceEnabled()) {
                log.trace("objectAdded(" + id + ", " + body + ")");
            }
            // Don't really care about this
        }

        @Override
        public void objectUnloaded(final EntityId id, final RigidBody<EntityId, S> body) {
            if (log.isTraceEnabled()) {
                log.trace("objectRemoved(" + id + ", " + body + ")");
            }
            zones.remove(Long.valueOf(id.getId()));
        }
    }
}
