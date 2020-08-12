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
    private HashMap<EntityId, Integer> goldMap = new HashMap<>();
    private final HostedServiceManager serviceManager;

    public ResourceSystem(HostedServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }
    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        this.ships = this.ed.getEntities(ShapeInfo.class);
    }

    @Override
    protected void terminate() {
        this.ships.release();
        this.ships = null;
    }

    @Override
    public void update(SimTime tpf) {
        // only update every RESOURCE_UPDATE_INTERVAL
        this.ships.applyChanges();

        if (this.time_since_last_update > CoreGameConstants.RESOURCE_UPDATE_INTERVAL) {
            this.time_since_last_update = 0;

            //TPF is in seconds
            int gold = (int) (tpf.getTpf() * CoreGameConstants.GOLD_PER_SECOND);

            //Handle old ships
            for (Entity e : this.ships) {
                Gold g = this.ed.getComponent(e.getId(), Gold.class);
                int totalGold = g.getGold() + gold;
                this.ed.setComponent(e.getId(), new Gold(totalGold));
                
                goldMap.put(e.getId(), totalGold);
            }
        }
        // update time
        this.time_since_last_update += tpf.getTpf();

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    /**
     * Checks if an entity can afford a tower
     *
     * @param owner the entity requesting a tower
     * @return true if the entity has enough gold
     */
    public boolean canAffordTower(EntityId owner) {
        return goldMap.get(owner) >= CoreGameConstants.TOWERCOST;
    }

    /**
     * Buys a tower on behalf of the entity and deducts the cost of the tower
     * from the entity
     *
     * @param owner the entity purchasing the tower
     */
    public void buyTower(EntityId owner) {
        int currentGold = goldMap.get(owner);
        int newGold = currentGold - CoreGameConstants.TOWERCOST;
        ed.setComponent(owner, new Gold(newGold));
        goldMap.put(owner, newGold);
    }
}
