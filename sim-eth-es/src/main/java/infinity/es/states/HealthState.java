package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.Buff;
import example.es.Dead;
import example.es.HealthChange;
import example.es.HitPoints;
import example.es.MaxHitPoints;
import example.es.ship.utilities.Recharge;
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

                    int chargeIntValue = charge.intValue();

                    this.createHealthChange(e.getId(), charge.intValue());
                }
            } else {
                double tpf = time.getTpf();

                Recharge recharge = e.get(Recharge.class);

                Double charge = tpf * recharge.getRechargePerSecond();

                int chargeIntValue = charge.intValue();

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
            } 
            //If we do have a maximum
            else {
                MaxHitPoints maxHp = maxLiving.getEntity(target.getId()).get(MaxHitPoints.class);
                //Check if we go above max hp
                if (entry.getValue() <= maxHp.getMaxHealth()) {
                    hp = hp.newAdjusted(entry.getValue());
                } 
                //Otherwise, set new hp
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

    public boolean hasHealth(EntityId eId) {
        return living.containsId(eId);
    }

    public int getHealth(EntityId eId) {
        return living.getEntity(eId).get(HitPoints.class).getHealth();
    }

    public int getMaxHealth(EntityId eId) {
        return maxLiving.getEntity(eId).get(MaxHitPoints.class).getMaxHealth();
    }

    public void createHealthChange(EntityId eId, int deltaHitPoints) {
        EntityId healthChange = ed.createEntity();
        ed.setComponents(healthChange, new Buff(eId, 0), new HealthChange(deltaHitPoints));
    }
}
