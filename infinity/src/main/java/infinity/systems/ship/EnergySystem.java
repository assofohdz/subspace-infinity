/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Buff;
import infinity.es.Dead;
import infinity.es.HealthChange;
import infinity.es.ship.Energy;
import infinity.es.ship.Health;
import infinity.es.ship.Recharge;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches entities with a live {@link Health} pool and applies pending health
 * changes (damage, ability cost, recharge) each tick. The pool tops out at the
 * ship's current effective energy cap {@link Energy} and reaching zero triggers
 * a {@link Dead} component.
 *
 * <p>This system never reads {@code EnergyMax} — that hard-cap component is
 * consumed only by {@code PrizeSystem} when an ENERGY prize bumps {@link Energy}.
 *
 * @author Paul Speed
 */
public class EnergySystem extends AbstractGameSystem {

  static Logger log = LoggerFactory.getLogger(EnergySystem.class);
  private final Map<EntityId, Integer> health = new HashMap<>();
  private EntityData ed;
  private EntitySet living;
  private EntitySet changes;
  private EntitySet recharges;
  private EntitySet capped;

  public EnergySystem() {
    // Nothing to do
  }

  @Override
  protected void initialize() {

    ed = getSystem(EntityData.class);
    living = ed.getEntities(Health.class);
    changes = ed.getEntities(Buff.class, HealthChange.class);

    recharges = ed.getEntities(Health.class, Recharge.class);

    capped = ed.getEntities(Health.class, Energy.class);
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

    capped.release();
    capped = null;
  }

  @Override
  public void update(final SimTime time) {

    // We accumulate all health adjustments together that are
    // in effect at this time... and then apply them all at once.
    // Make sure our entity views are up-to-date as of
    // now.
    living.applyChanges();
    capped.applyChanges();
    changes.applyChanges();

    // Collect all of the relevant health updates
    for (final Entity e : changes) {
      final Buff b = e.get(Buff.class);

      // Does the buff apply yet
      if (b.getStartTime() > time.getTime()) {
        continue;
      }

      final HealthChange change = e.get(HealthChange.class);
      Integer hp = health.get(b.getTarget());
      if (hp == null) {
        hp = Integer.valueOf(change.getDelta());
      } else {
        hp = Integer.valueOf(hp.intValue() + change.getDelta());
      }
      health.put(b.getTarget(), hp);

      // Delete the buff entity
      ed.removeEntity(e.getId());
    }

    // Perform recharges
    recharges.applyChanges();
    for (final Entity e : recharges) {

      if (capped.containsId(e.getId())) {
        if (getHealth(e.getId()) < getCap(e.getId())) {
          final double tpf = time.getTpf();
          final Recharge recharge = e.get(Recharge.class);
          final int charge = Math.toIntExact(Math.round(tpf * recharge.getRechargePerSecond()));
          damage(e.getId(), charge);
        }
      } else {
        final double tpf = time.getTpf();
        final Recharge recharge = e.get(Recharge.class);
        final int charge = Math.toIntExact(Math.round(tpf * recharge.getRechargePerSecond()));
        damage(e.getId(), charge);
      }
    }

    // Now apply all accumulated adjustments
    for (final Map.Entry<EntityId, Integer> entry : health.entrySet()) {
      final Entity target = living.getEntity(entry.getKey());

      if (target == null) {
        log.warn("No target for id: {}", entry.getKey());
        continue;
      }

      Health hp = target.get(Health.class);

      // If we don't have a cap, just apply the delta as-is.
      if (!capped.containsId(target.getId())) {
        hp = hp.newAdjusted(entry.getValue().intValue());
      } else {
        // Cap exists — clamp the post-delta pool at the current cap.
        final Energy cap = capped.getEntity(target.getId()).get(Energy.class);
        final int next = hp.getHealth() + entry.getValue().intValue();
        hp = new Health(Math.min(next, cap.getEnergy()));
      }

      target.set(hp);

      if (hp.getHealth() <= 0) {
        log.info("Entity " + target.getId() + " died");
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
   * Returns true if the entity has a live {@link Health} pool.
   *
   * @param entityId the entityid to check
   * @return true if the entity has health, false if not
   */
  public boolean hasEnergy(final EntityId entityId) {
    return living.containsId(entityId);
  }

  /**
   * Returns the entity's current live {@link Health} value (the depleting
   * pool).
   *
   * @param entityId the entityid to check
   * @return the live health of the entity
   */
  public int getHealth(final EntityId entityId) {
    return living.getEntity(entityId).get(Health.class).getHealth();
  }

  /**
   * Returns the entity's current effective energy cap (the upgradeable
   * {@link Energy} the live pool tops out at; <i>not</i> the absolute
   * hard cap {@code EnergyMax}).
   *
   * @param entityId the entity to check
   * @return the current effective cap
   */
  public int getCap(final EntityId entityId) {
    return capped.getEntity(entityId).get(Energy.class).getEnergy();
  }

  /**
   * Creates a health change for the specified entity. The health change will be applied at the next
   * update.
   *
   * @param entityId the entity to create a health change for
   * @param deltaHitPoints the change in hitpoints (can be both positive an negative)
   */
  public void damage(final EntityId entityId, final int deltaHitPoints) {
    final EntityId healthChange = ed.createEntity();
    ed.setComponents(healthChange, new Buff(entityId, 0), new HealthChange(deltaHitPoints));
  }

  /**
   * Refills the entity's live {@link Health} pool to its current effective cap
   * {@link Energy}. Used by the QUICKCHARGE prize.
   *
   * @param entityId the entity to refill (must have both Health and Energy)
   * @return the new live health value
   */
  public int refillHealth(final EntityId entityId) {
    final Entity e = ed.getEntity(entityId, Health.class, Energy.class);
    final Energy cap = e.get(Energy.class);
    final Health refilled = new Health(cap.getEnergy());
    e.set(refilled);
    return refilled.getHealth();
  }
}
