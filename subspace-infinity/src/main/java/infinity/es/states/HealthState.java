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
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.Buff;
import infinity.api.es.Dead;
import infinity.api.es.HealthChange;
import infinity.api.es.HitPoints;
import infinity.api.es.MaxHitPoints;
import infinity.api.es.ship.utilities.Recharge;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches entities with hitpoints and entities with health changes and applies
 * them to the hitpoints of an entity, possibly causing death.
 *
 * @author Paul Speed
 */
public class HealthState extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(HealthState.class);

    private EntityData ed;
    private EntitySet living;
    private EntitySet changes;
    private Map<EntityId, Integer> health = new HashMap<>();
    private EntitySet recharges;
    private EntitySet maxLiving;

    public HealthState() {
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);
        living = ed.getEntities(HitPoints.class);
        changes = ed.getEntities(Buff.class, HealthChange.class);

        recharges = ed.getEntities(HitPoints.class, Recharge.class);

        maxLiving = ed.getEntities(HitPoints.class, MaxHitPoints.class);
    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
        living.release();
        living = null;

        changes.release();
        changes = null;

        recharges.release();
        recharges = null;

        maxLiving.release();
        maxLiving = null;
    }

    @Override
    public void update(SimTime time) {

        // We accumulate all health adjustments together that are 
        // in effect at this time... and then apply them all at once.       
        // Make sure our entity views are up-to-date as of
        // now.       
        living.applyChanges();
        maxLiving.applyChanges();
        changes.applyChanges();

        // Collect all of the relevant health updates       
        for (Entity e : changes) {
            Buff b = e.get(Buff.class);

            // Does the buff apply yet
            if (b.getStartTime() > time.getTime()) {
                continue;
            }

            HealthChange change = e.get(HealthChange.class);
            Integer hp = health.get(b.getTarget());
            if (hp == null) {
                hp = change.getDelta();
            } else {
                hp += change.getDelta();
            }
            health.put(b.getTarget(), hp);

            // Delete the buff entity
            ed.removeEntity(e.getId());
        }

        //Perform recharges
        recharges.applyChanges();
        for (Entity e : recharges) {

            if (maxLiving.containsId(e.getId())) {
                if (this.getHealth(e.getId()) < this.getMaxHealth(e.getId())) {
                    double tpf = time.getTpf();

                    Recharge recharge = e.get(Recharge.class);

                    Double charge = tpf * recharge.getRechargePerSecond();

                    this.createHealthChange(e.getId(), charge.intValue());
                }
            } else {
                double tpf = time.getTpf();

                Recharge recharge = e.get(Recharge.class);

                Double charge = tpf * recharge.getRechargePerSecond();

                this.createHealthChange(e.getId(), charge.intValue());
            }
        }

        // Now apply all accumulated adjustments
        for (Map.Entry<EntityId, Integer> entry : health.entrySet()) {
            Entity target = living.getEntity(entry.getKey());
            if (target == null) {
                log.warn("No target for id:" + entry.getKey());
                continue;
            }

            HitPoints hp = target.get(HitPoints.class);
            if (log.isInfoEnabled()) {
                log.info("Applying " + entry.getValue() + " to:" + target + " result:" + hp);
            }

            //If we dont have a max hitpoint, just set new hp
            if (!maxLiving.containsId(target.getId())) {
                hp = hp.newAdjusted(entry.getValue());
            } //If we do have a maximum
            else {
                MaxHitPoints maxHp = maxLiving.getEntity(target.getId()).get(MaxHitPoints.class);
                //Check if we go above max hp
                if (entry.getValue() <= maxHp.getMaxHealth()) {
                    hp = hp.newAdjusted(entry.getValue());
                } //Otherwise, set new hp
                else {
                    hp = hp.newAdjusted(maxHp.getMaxHealth());
                }
            }

            target.set(hp);

            if (hp.getHealth() <= 0) {
                System.out.println(target + " is dead");
                // don't set death if it is already dead.
                if (ed.getComponent(target.getId(), Dead.class) == null) {
                    target.set(new Dead(time.getTime()));
                }
            }
        }

        // Clear our health book-keeping map.
        health.clear();

    }

    /**
     * @param eId the entityid to check
     * @return true if the entity has health, false if not
     */
    public boolean hasHealth(EntityId eId) {
        return living.containsId(eId);
    }

    /**
     * @param eId the entityid to check
     * @return the health of the entity
     */
    public int getHealth(EntityId eId) {
        return living.getEntity(eId).get(HitPoints.class).getHealth();
    }

    /**
     * @param eId the entity to check
     * @return the maximum health of the entity
     */
    public int getMaxHealth(EntityId eId) {
        return maxLiving.getEntity(eId).get(MaxHitPoints.class).getMaxHealth();
    }

    /**
     * @param eId the entity to create a health change for
     * @param deltaHitPoints the change in hitpoints (can be both positive an negative)
     */
    public void createHealthChange(EntityId eId, int deltaHitPoints) {
        EntityId healthChange = ed.createEntity();
        ed.setComponents(healthChange, new Buff(eId, 0), new HealthChange(deltaHitPoints));
    }
}
