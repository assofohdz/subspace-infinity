// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import java.util.HashMap;
import java.util.Map;

import com.jme3.network.service.HostedServiceManager;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.Gold;

/** Tracks spendable resources (Gold, etc.). Tower/gold-economy is shelved; values are co-located until reactivated. */
public class ResourceSystem extends AbstractGameSystem {

    private static final double RESOURCE_UPDATE_INTERVAL = 1;
    private static final double GOLD_PER_SECOND = 10000;
    private static final int TOWER_COST = 1000;

    private EntityData ed;
    private EntitySet ships;
    private double time_since_last_update;
    private final Map<EntityId, Integer> goldMap = new HashMap<>();
    // private final HostedServiceManager serviceManager;

    public ResourceSystem(@SuppressWarnings("unused") final HostedServiceManager serviceManager) {
        // this.serviceManager = serviceManager;
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

        if (time_since_last_update > RESOURCE_UPDATE_INTERVAL) {
            time_since_last_update = 0;

            // TPF is in seconds
            final int gold = (int) (tpf.getTpf() * GOLD_PER_SECOND);

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
        // no-op: lifecycle hook unused; EntitySet wiring happens in initialize()
    }

    @Override
    public void stop() {
        // no-op: lifecycle hook unused; cleanup happens in terminate()
    }

    /**
     * Checks if an entity can afford a tower
     *
     * @param owner the entity requesting a tower
     * @return true if the entity has enough gold
     */
    public boolean canAffordTower(final EntityId owner) {
        return goldMap.get(owner).intValue() >= TOWER_COST;
    }

    /**
     * Buys a tower on behalf of the entity and deducts the cost of the tower from
     * the entity
     *
     * @param owner the entity purchasing the tower
     */
    public void buyTower(final EntityId owner) {
        final int currentGold = goldMap.get(owner).intValue();
        final int newGold = currentGold - TOWER_COST;
        ed.setComponent(owner, new Gold(newGold));
        goldMap.put(owner, Integer.valueOf(newGold));
    }
}
