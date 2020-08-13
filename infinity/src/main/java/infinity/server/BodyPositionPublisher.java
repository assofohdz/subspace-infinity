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

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.ObjectStatusListener;
import com.simsilica.mphys.AbstractShape;
import com.simsilica.mphys.PhysicsListener;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;

import infinity.es.BodyPosition;

/**
 * Publishes to a BodyPosition component so that server-side systems have easy
 * access to the mobile entity positions. Since we wrote our own simple physics
 * engine for this example, we could have just added BodyPosition as a field to
 * the Body class but I wanted to show how one might integrate this component
 * using a physics system that wouldn't let you do that.
 *
 * Note: also adding the BodyPosition to the entity on the server is what makes
 * it available on the client so that it can have a place to add its object
 * update events from the network. The BodyPosition component itself is actually
 * transferred empty.
 *
 * @author Paul Speed
 */
public class BodyPositionPublisher<S extends AbstractShape> extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(BodyPositionPublisher.class);

    private EntityData ed;
    private final PhysicsObserver observer = new PhysicsObserver();

    public BodyPositionPublisher() {
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        getSystem(MPhysSystem.class).addPhysicsListener(observer);
        getSystem(MPhysSystem.class).getBinEntityManager().addObjectStatusListener(observer);
    }

    @Override
    protected void terminate() {
        getSystem(MPhysSystem.class).getBinEntityManager().removeObjectStatusListener(observer);
        getSystem(MPhysSystem.class).removePhysicsListener(observer);
    }

    private class PhysicsObserver implements PhysicsListener<EntityId, S>, ObjectStatusListener<S> {

        private long frameTime;

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
        }

        @Override
        public void startFrame(final long time, final double stepSize) {
            frameTime = time;
        }

        @Override
        public void endFrame() {
            return;
        }

        @Override
        public void update(final RigidBody<EntityId, S> body) {
            if (log.isTraceEnabled()) {
                log.trace("update(" + body + ")");
            }
            final BodyPosition p = ed.getComponent(body.id, BodyPosition.class);
            if (p == null) {
                // Until we have remove notifications
                log.error("No body position for:" + body.id);
                return;
            }
            p.addFrame(frameTime, body.position, body.orientation, true);
        }

        @Override
        public void objectLoaded(final EntityId id, final RigidBody<EntityId, S> body) {
            if (log.isTraceEnabled()) {
                log.trace("objectLoaded(" + id + ", " + body + ")");
            }
            // The server side needs hardly any backlog. We'll use 3 just in case
            // but 2 (even possibly 1) should be fine. If we ever need to rewind
            // for shot resolution then we can increase the backlog as necessary
            final BodyPosition bPos = new BodyPosition(3);

            // We have the body and the position, might as well just set it to
            // its initial value.
            bPos.addFrame(frameTime, body.position, body.orientation, true);

//log.info("set body position on:" + id);
            ed.setComponent(body.id, bPos);
        }

        @Override
        public void objectUnloaded(final EntityId id, final RigidBody<EntityId, S> body) {
            if (log.isTraceEnabled()) {
                log.trace("objectUnloaded(" + id + ", " + body + ")");
            }

            // Note: objectRemoved() is called when the RigidBody has been removed
            // from the physics space by the bin entity manager. This generally only
            // happens when the entity itself has been 'removed' and therefore it is
            // unlikely to have a BodyPosition anymore. Also, this BodyPosition updating
            // is only used on the server and so is unlikely to care about historical
            // visibility, etc..
            final BodyPosition p = ed.getComponent(id, BodyPosition.class);
            if (p == null) {
                return; // just in case
            }

            // Add the final frame with the invisible flag
            p.addFrame(frameTime, body.position, body.orientation, false);
        }
    }
}
