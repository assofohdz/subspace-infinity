/*
 * $Id$
 *
 * Copyright (c) 2019, Simsilica, LLC
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityChange;
import com.simsilica.es.EntityComponentListener;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.ObservableEntityData;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.InfinityConstants;
import infinity.es.BodyPosition;
import infinity.es.LargeGridCell;
import infinity.es.LargeObject;

/**
 * Watches for changes to large static objects and makes sure that their grid
 * cell components are updated as appropriate.
 *
 * @author Paul Speed
 */
public class LargeGridIndexSystem extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(LargeGridIndexSystem.class);

    private Grid largeGrid = InfinityConstants.LARGE_OBJECT_GRID;

    private EntityData ed;
    private PhysicsSpace phys;
    private EntityChangeObserver entityObserver = new EntityChangeObserver();
    private ConcurrentLinkedQueue<EntityId> changes = new ConcurrentLinkedQueue<>();
    private Set<EntityId> processed = new HashSet<>();

    private LobContainer lobs;

    public LargeGridIndexSystem() {
    }

    @Override
    protected void initialize() {

        ed = getSystem(EntityData.class, true);
        phys = getSystem(PhysicsSpace.class, true);

        // The tricky bit here is that we don't want to
        // watch all static objects all the time like in a big entity
        // set. We just want to make sure that any static object with
        // a LargeObject component updates its LargeGridCell component
        // when it moves.
        // On the one hand, I don't expect a particular world to have
        // lots of large objects... on the other hand, why manage something
        // in a list if you don't have to?
        //
        // So instead, we'll opt for a change listener. For threading
        // issues and because it's actually more convenient, we'll just
        // collect the entity IDs we need to check during event processing
        // and actually look at doing the updates on update().
        ((ObservableEntityData) ed).addEntityComponentListener(entityObserver);

        // Do note that for only a few hundred large objects it might be
        // better to just keep the list rather than constantly looking up
        // their largeness. But also note that in general we expect these
        // objects not to move very much.

        // Keep track of the large mobs to occasionally update their grid
        // information and spawn position.
        lobs = new LobContainer(ed);
    }

    @Override
    public void update(SimTime time) {
        super.update(time);

        updateLobs(time);

        if (changes.isEmpty()) {
            return;
        }

        EntityId id = null;
        while ((id = changes.poll()) != null) {

            if (!processed.add(id)) {
                // Already handled this one
                return;
            }

//log.info("check large object changed:" + id);
            LargeObject lo = ed.getComponent(id, LargeObject.class);
            LargeGridCell cell = ed.getComponent(id, LargeGridCell.class);
            if (lo == null) {
                if (cell != null) {
                    log.info("Removing large grid cell:" + cell + " from:" + id);
                    ed.removeComponent(id, LargeGridCell.class);
                }
                // Doesn't need updating
                continue;
            }

            // Get the latest position
            SpawnPosition pos = ed.getComponent(id, SpawnPosition.class);
            if (pos == null) {
                // Doesn't need updating.
                continue;
            }

            LargeGridCell newCell = LargeGridCell.create(largeGrid, pos.getLocation());

            if (cell == null || newCell.getCellId() != cell.getCellId()) {
                // Update the cell position
                log.info("Updating:" + id + " grid cell:" + newCell);
                ed.setComponent(id, newCell);
            }
        }

        processed.clear();
    }

    private double nextTime = 0;
    private double timeInterval = 0.05f; // 20 times a second

    private void updateLobs(SimTime time) {
        double secs = time.getTimeInSeconds();
        if (secs < nextTime) {
            return;
        }
        nextTime = secs + timeInterval;

        lobs.update();

        for (Lob lob : lobs.getArray()) {
            lob.updateCell();
        }
    }

    @Override
    public void start() {
        super.start();
        lobs.start();
    }

    @Override
    public void stop() {
        lobs.stop();
        super.stop();
    }

    @Override
    protected void terminate() {
        ((ObservableEntityData) ed).removeEntityComponentListener(entityObserver);
    }

    private class Lob {
        private Entity entity;
        private BodyPosition pos;
        private Vec3d lastLoc = new Vec3d();
        private Quatd lastOrient = new Quatd();
        private Long lastCellId = null;

        public Lob(Entity entity) {
            this.entity = entity;
        }

        public void update() {
            pos = entity.get(BodyPosition.class);
        }

        public void updateCell() {
            Vec3d loc = pos.getLastLocation();
            Quatd orient = pos.getLastOrientation();
            if (loc == null || orient == null) {
                return;
            }
            if (loc.isSimilar(lastLoc, 0.01) && orient.isSimilar(lastOrient, 0.01)) {
                return;
            }
            lastLoc.set(loc);
            lastOrient.set(orient);

            SpawnPosition spawnPos = new SpawnPosition(phys.getGrid(), loc, orient);
            entity.set(spawnPos);

            LargeGridCell cell = LargeGridCell.create(largeGrid, spawnPos.getLocation());
            if (lastCellId != null && cell.getCellId() == lastCellId) {
                return;
            }
            lastCellId = cell.getCellId();
            // entity.set(cell);
        }
    }

    /**
     * Keep track of the mobile large objects to keep their spawn positions and
     * LargeGridCells up to date.
     */
    private class LobContainer extends EntityContainer<Lob> {
        public LobContainer(EntityData ed) {
            super(ed, LargeObject.class, BodyPosition.class);
        }

        @Override
        public Lob[] getArray() {
            return super.getArray();
        }

        @Override
        protected Lob addObject(Entity e) {
            log.info("add LOB for:" + e.getId());
            Lob object = new Lob(e);
            updateObject(object, e);
            return object;
        }

        @Override
        protected void updateObject(Lob object, Entity e) {
            object.update();
        }

        @Override
        protected void removeObject(Lob object, Entity e) {
            log.info("remove LOB for:" + e.getId());
        }
    }

    private class EntityChangeObserver implements EntityComponentListener {

        @Override
        public void componentChange(EntityChange change) {
            // We only care about a few components and we should quickly
            // short-circuit otherwise to avoid lag
            Class type = change.getComponentType();
            if (type != SpawnPosition.class && type != LargeObject.class) {
                return;
            }

//log.info("Queueing change:" + change);

            // Queue the change for processing on the game loop thread.
            changes.add(change.getEntityId());
        }
    }
}
