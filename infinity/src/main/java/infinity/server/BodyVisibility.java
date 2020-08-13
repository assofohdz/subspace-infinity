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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.EntityChange;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.server.ComponentVisibility;
import com.simsilica.ethereal.NetworkStateListener;

import infinity.es.BodyPosition;

/**
 * Limits the client's visibility of any entity containing a BodyPosition to
 * just what the SimEthereal visibility says they can see.
 *
 * @author Paul Speed
 */
public class BodyVisibility implements ComponentVisibility {

    static Logger log = LoggerFactory.getLogger(BodyVisibility.class);

    private final NetworkStateListener netState;
    private EntityData ed;

    // private final Set<Long> lastActiveIds;

    private final Map<EntityId, BodyPosition> lastValues = new HashMap<>();

    protected BodyVisibility(final NetworkStateListener netState,
            @SuppressWarnings("unused") final Set<Long> lastActiveIds) {
        this.netState = netState;
        // this.lastActiveIds = lastActiveIds;
    }

    public BodyVisibility(final NetworkStateListener netState) {
        this(netState, null);
    }

    @Override
    public Class<? extends EntityComponent> getComponentType() {
        return BodyPosition.class;
    }

    @Override
    public void initialize(final EntityData entityData) {
        ed = entityData;
    }

    @Override
    public <T extends EntityComponent> T getComponent(final EntityId entityId, final Class<T> type) {
        log.info("getComponent(" + entityId + ", " + type + ")");
        // if( !netState.getActiveIds().contains(entityId) ) {
        // return null;
        // }
        if (!lastValues.containsKey(entityId)) {
            return null;
        }
        return ed.getComponent(entityId, type);
    }

    @Override
    public Set<EntityId> getEntityIds(@SuppressWarnings("rawtypes") final ComponentFilter filter) {
        if (log.isTraceEnabled()) {
            log.trace("getEntityIds(" + filter + ")");
        }
        if (filter != null) {
            throw new UnsupportedOperationException("Filtering + body visibility not yet supported");
        }

        /*
         * Set<Long> active = netState.getActiveIds(); log.info("active:" + active);
         *
         * Set<EntityId> results = new HashSet<>(); for( Long l : active ) {
         * results.add(new EntityId(l)); }
         *
         * return results;
         */
        return lastValues.keySet();
    }

    @Override
    public boolean collectChanges(final Queue<EntityChange> updates) {
        final Set<Long> active = netState.getActiveIds();
        boolean changed = false;
        if (log.isTraceEnabled()) {
            log.trace("active:" + active);
            log.info("updates before:" + updates);
        }

        // Remove any BodyPosition updates that don't belong to the active
        // set
        for (final Iterator<EntityChange> it = updates.iterator(); it.hasNext();) {
            final EntityChange change = it.next();
            if (change.getComponentType() == BodyPosition.class
                    && !active.contains(Long.valueOf(change.getEntityId().getId()))) {
                if (log.isTraceEnabled()) {
                    log.trace("removing irrelevant change:" + change);
                }
                it.remove();
            }
        }

        // First process the removals
        for (final Iterator<EntityId> it = lastValues.keySet().iterator(); it.hasNext();) {
            final EntityId id = it.next();
            if (active.contains(Long.valueOf(id.getId()))) {
                continue;
            }
            if (log.isTraceEnabled()) {
                log.trace("removing:" + id);
            }
            updates.add(new EntityChange(id, BodyPosition.class));
            it.remove();
            changed = true;
        }

        // Now the adds
        for (final Long l : active) {
            final EntityId id = new EntityId(l.longValue());
            if (lastValues.containsKey(id)) {
                continue;
            }
            if (log.isTraceEnabled()) {
                log.trace("adding:" + id);
            }
            final BodyPosition pos = ed.getComponent(id, BodyPosition.class);
            lastValues.put(id, pos);
            updates.add(new EntityChange(id, pos));
            changed = true;
        }

        if (changed) {
            log.info("done collectChanges() " + active);
        }

        return changed;
    }

}
