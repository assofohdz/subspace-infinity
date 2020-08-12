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

package infinity.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.client.EntityDataClientService;
import com.simsilica.ethereal.EtherealClient;
import com.simsilica.ethereal.SharedObject;
import com.simsilica.ethereal.SharedObjectListener;

import infinity.es.BodyPosition;

/**
 * Updates the entities local position from network state. Requires that the
 * entity have the BodyPosition component to accumulate state history for some
 * backlog. Updates to entities without a BodyPosition will be ignored... the
 * alternative would be to cache a BodyPosition in advance until we finally see
 * the entity. This will be necessary if strict starting visibility is ever a
 * requirement as the message that updates the entity's component may come some
 * time after we've been recieving valid updates. Enough that we'll be missing
 * some history. (For example, a missile might look like it starts a bit down
 * its path.)
 *
 * @author Paul Speed
 */
public class SharedObjectUpdater extends AbstractClientService implements SharedObjectListener {

    static Logger log = LoggerFactory.getLogger(SharedObjectUpdater.class);

    private EntityData ed;
    private EntitySet entities;
    private long frameTime;

    // All of the trackers will be accessed from the SharedObjectListener
    // methods: beginFrame(), objectUpdated(), objectRemoved(), etc. and
    // so should all be from the same thread.
    private final Map<Long, ObjectTracker> trackers = new HashMap<>();

    public SharedObjectUpdater() {
    }

    @Override
    protected void onInitialize(final ClientServiceManager s) {
        log.info("onInitialize()");
        ed = getService(EntityDataClientService.class).getEntityData();
    }

    @Override
    public void start() {
        log.info("start()");
        log.info("start() thread:" + Thread.currentThread());
        entities = ed.getEntities(BodyPosition.class);
        frameTime = -1;
        getService(EtherealClient.class).addObjectListener(this);
    }

    @Override
    public void stop() {
        log.info("stop()");
        getService(EtherealClient.class).removeObjectListener(this);
        entities.release();
    }

    @Override
    public void beginFrame(final long time) {
        if (log.isTraceEnabled()) {
            log.trace("** beginFrame(" + time + ")");
        }
        frameTime = time;
        if (entities.applyChanges()) {
            // Make sure the added/updated entities have been initialized
            initializeBodyPosition(entities.getAddedEntities());
            initializeBodyPosition(entities.getChangedEntities());
            if (log.isTraceEnabled()) {
                for (final Entity e : entities.getRemovedEntities()) {
                    log.trace("entity removed:" + e.getId());
                }
            }

            updateEntityTrackers(entities.getAddedEntities(), 1);
            updateEntityTrackers(entities.getRemovedEntities(), -1);
        }
    }

    protected void initializeBodyPosition(final Set<Entity> set) {
        for (final Entity e : set) {
            final BodyPosition pos = e.get(BodyPosition.class);

            // BodyPosition requires special management to make
            // sure all instances of BodyPosition are sharing the same
            // thread-safe history buffer
            pos.initialize(e.getId(), 12);
            if (log.isTraceEnabled()) {
                log.trace("BodyPos.initialize(" + e.getId() + ")");
            }
        }
    }

    @Override
    public void objectUpdated(final SharedObject obj) {
        if (log.isTraceEnabled()) {
            log.trace("****** Object moved[t=" + frameTime + "]:" + obj.getEntityId() + "  pos:"
                    + obj.getWorldPosition() + "  removed:" + obj.isMarkedRemoved());
        }
        if (obj.getEntityId() == null) {
            log.info("****** Object moved[t=" + frameTime + "]:" + obj.getEntityId() + "  netId:" + obj.getNetworkId()
                    + "  pos:" + obj.getWorldPosition() + "  removed:" + obj.isMarkedRemoved());
        }

        final EntityId id = new EntityId(obj.getEntityId());
        final ObjectTracker tracker = getTracker(id, true);
        tracker.updateCount++;

        final Entity entity = entities.getEntity(id);
        if (entity == null) {
            // This can happen either because we've received an update before we
            // have the entity or because we received the entity removal before we
            // got the shared object update. (The latter is probably more common
            // in my localhost test environment.)
            // Now with the ObjectTrackers we can detect how many times this happens
            // and only log the first few... and then an error at a certain point.
            // We skip the first one because in localhost testing it's really common.
            if (tracker.updateCount > 1 && tracker.updateCount < 5 && log.isDebugEnabled()) {
                log.debug("update: No entity yet for:" + obj.getEntityId());
            } else if (tracker.updateCount == 20) {
                log.error("update: No entity for:" + obj.getEntityId() + "  after 20 updates.");
            }
            return;
        }
        final BodyPosition pos = entity.get(BodyPosition.class);
        if (pos == null) {
            // normal as it may take longer for that update to get here
            if (log.isDebugEnabled()) {
                log.debug("Object doesn't have a BodyPosition yet for:" + obj.getEntityId());
            }
        } else {
            // Temporary watchdog. FIXME: when we are confident that this never
            // happens.
            if (!pos.isInitialized()) {
                log.error("BodyPos not initialized:" + id);
                pos.initialize(id, 12);
            }

            // Update our position buffer
            pos.addFrame(frameTime, obj.getWorldPosition(), obj.getWorldRotation(), true);
        }
    }

    @Override
    public void objectRemoved(final SharedObject obj) {
        if (log.isDebugEnabled()) {
            log.debug("****** Object removed[t=" + frameTime + "]:" + obj.getEntityId() + "  netId:"
                    + obj.getNetworkId());
        }
        final EntityId id = new EntityId(obj.getEntityId());
        final Entity entity = entities.getEntity(id);

        final ObjectTracker tracker = getTracker(id, false);
        if (tracker == null) {
            log.warn("Received object removal for entity/object with no tracker:" + id);
        } else {
            tracker.objectRemoved++;
            tracker.checkRelease();
        }

        if (entity == null) {
            // Sometimes we recieve the entity removal before we receive the sim-ethereal
            // removal... and so we will fail to find an entity. If we really cared about
            // proper warnings, we could probably keep track of this state. Might be worth
            // it in this test/demo app... but probably not in a real game.
            // We do now... so I'm commenting out this log. A missing tracker will trigger
            // a real warning.
            // if( log.isDebugEnabled() ) {
            // log.debug("No entity for removed object for:" + obj.getEntityId());
            // }
            return;
        }
        final BodyPosition pos = entity.get(BodyPosition.class);
        if (pos == null) {
            // normal as it may take longer for that update to get here
            if (log.isDebugEnabled()) {
                log.debug("Removed object doesn't have a BodyPosition yet for:" + obj.getEntityId());
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Setting entity to invisible for:" + obj.getEntityId());
            }
            pos.addFrame(frameTime, obj.getWorldPosition(), obj.getWorldRotation(), false);
        }
    }

    @Override
    public void endFrame() {
        log.trace("** endFrame()");
        frameTime = -1;

        if (log.isTraceEnabled()) {
            if (trackers.size() != entities.size()) {
                log.info("Tracker count:" + trackers.size() + "  entity count:" + entities.size());
            }
        }
    }

    protected ObjectTracker getTracker(final EntityId id, final boolean create) {
        return getTracker(id.getId(), create);
    }

    protected ObjectTracker getTracker(final Long id, final boolean create) {
        ObjectTracker result = trackers.get(id);
        if (result == null && create) {
            result = new ObjectTracker(id);
            trackers.put(id, result);
        }
        return result;
    }

    protected void updateEntityTrackers(final Set<Entity> set, final int delta) {
        for (final Entity e : set) {
            if (delta > 0) {
                getTracker(e.getId(), true).entityAdded += delta;
            } else {
                final ObjectTracker tracker = getTracker(e.getId(), false);
                if (tracker == null) {
                    log.warn("Receiving entity removal for entity we've never seen:" + e.getId());
                } else {
                    tracker.entityAdded += delta;
                    tracker.checkRelease();
                }
            }
        }
    }

    /**
     * Keeps track of which objects we've seen events for. A real application
     * wouldn't need this but because this demo acts as a test app, it's nice if it
     * can warn us _accurately_ when we are seeing messages that we shouldn't be.
     */
    private class ObjectTracker {
        Long id;
        int entityAdded;
        int objectRemoved;
        long updateCount;

        public ObjectTracker(final Long id) {
            this.id = id;
        }

        public void checkRelease() {
            if (entityAdded == 0 && objectRemoved > 0) {
                trackers.remove(id);
            } else if (entityAdded < 0) {
                log.warn("ObjectTracker.entityAdded somehow went negative for:" + id);
            }
        }
    }
}
