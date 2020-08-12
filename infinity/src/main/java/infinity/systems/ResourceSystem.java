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

import java.util.HashMap;

import com.jme3.network.service.HostedServiceManager;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.Gold;
import infinity.sim.CoreGameConstants;

/**
 * This state is meant to keep track of resources that can be spent.
 *
 * @author ss
 */
public class ResourceSystem extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet ships;
    private double time_since_last_update;
    private final HashMap<EntityId, Integer> goldMap = new HashMap<>();
    private final HostedServiceManager serviceManager;

    public ResourceSystem(final HostedServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);
        ships = ed.getEntities(ShapeInfo.class);
    }

    @Override
    protected void terminate() {
        ships.release();
        ships = null;
    }

    @Override
    public void update(final SimTime tpf) {
        // only update every RESOURCE_UPDATE_INTERVAL
        ships.applyChanges();

        if (time_since_last_update > CoreGameConstants.RESOURCE_UPDATE_INTERVAL) {
            time_since_last_update = 0;

            // TPF is in seconds
            final int gold = (int) (tpf.getTpf() * CoreGameConstants.GOLD_PER_SECOND);

            // Handle old ships
            for (final Entity e : ships) {
                final Gold g = ed.getComponent(e.getId(), Gold.class);
                final int totalGold = g.getGold() + gold;
                ed.setComponent(e.getId(), new Gold(totalGold));

                goldMap.put(e.getId(), Integer.valueOf(totalGold));
            }
        }
        // update time
        time_since_last_update += tpf.getTpf();

    }

    @Override
    public void start() {
        return;
    }

    @Override
    public void stop() {
        return;
    }

    /**
     * Checks if an entity can afford a tower
     *
     * @param owner the entity requesting a tower
     * @return true if the entity has enough gold
     */
    public boolean canAffordTower(final EntityId owner) {
        return goldMap.get(owner).intValue() >= CoreGameConstants.TOWERCOST;
    }

    /**
     * Buys a tower on behalf of the entity and deducts the cost of the tower from
     * the entity
     *
     * @param owner the entity purchasing the tower
     */
    public void buyTower(final EntityId owner) {
        final int currentGold = goldMap.get(owner).intValue();
        final int newGold = currentGold - CoreGameConstants.TOWERCOST;
        ed.setComponent(owner, new Gold(newGold));
        goldMap.put(owner, Integer.valueOf(newGold));
    }
}
