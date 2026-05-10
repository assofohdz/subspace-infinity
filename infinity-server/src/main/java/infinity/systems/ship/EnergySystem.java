// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Buff;
import infinity.es.DamageSource;
import infinity.es.Dead;
import infinity.es.HealthChange;
import infinity.es.ship.Energy;
import infinity.es.ship.weapons.WeaponType;
import infinity.es.ship.Health;
import infinity.es.ship.Player;
import infinity.es.ship.Recharge;
import infinity.systems.PrizeSystem;
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
  /**
   * Resolved lazily inside the death branch so {@link EnergySystem} stays
   * usable in test fixtures that don't register {@link PrizeSystem}.
   * Lazily — system order in {@code GameServer} runs PrizeSystem before
   * EnergySystem, but the lazy lookup is cheap and avoids a hard init
   * dependency.
   */
  private PrizeSystem prizeSystem;

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

    collectBuffChanges(time);
    applyRecharges(time);
    applyAccumulatedChanges(time);

    // Clear our health book-keeping map.
    health.clear();
  }

  /**
   * Drain the {@link #changes} buff queue: for each buff whose start time has
   * arrived, accumulate its {@link HealthChange#getDelta()} into {@link #health}
   * (keyed by target) and delete the buff entity.
   */
  private void collectBuffChanges(final SimTime time) {
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
  }

  /**
   * For every entity with a {@link Recharge}, queue a positive
   * {@link #damage(EntityId, int)} delta proportional to {@code tpf} —
   * skipping entities already at their effective cap.
   */
  private void applyRecharges(final SimTime time) {
    recharges.applyChanges();
    for (final Entity e : recharges) {
      if (capped.containsId(e.getId()) && getHealth(e.getId()) >= getCap(e.getId())) {
        // Already at cap — nothing to recharge.
        continue;
      }
      final double tpf = time.getTpf();
      final Recharge recharge = e.get(Recharge.class);
      final int charge = Math.toIntExact(Math.round(tpf * recharge.getRechargePerSecond()));
      damage(e.getId(), charge);
    }
  }

  /**
   * Apply every accumulated delta in {@link #health} to the matching live
   * entity, clamping at the cap if one exists, and triggering the death
   * branch when the post-delta pool reaches zero.
   */
  private void applyAccumulatedChanges(final SimTime time) {
    for (final Map.Entry<EntityId, Integer> entry : health.entrySet()) {
      final Entity target = living.getEntity(entry.getKey());

      if (target == null) {
        if (log.isWarnEnabled()) {
          log.warn("No target for id: {}", entry.getKey());
        }
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
        handleDeath(target, time);
      }
    }
  }

  /**
   * Mark {@code target} dead (idempotent — no-op if already {@link Dead}) and
   * spawn a death prize at the body's last known location for player ships.
   * Slice 8b: filter for {@link Player} so non-ship dying entities (any future
   * Health-bearing thing) don't trigger a prize. {@code BodyPosition} is the
   * current world coord — captured synchronously while it's still valid (the
   * Decay reaper can sweep the entity later). {@link PrizeSystem} handles the
   * no-op when the arena's {@code PrizeConfig.deathPrizeTimeMs == 0}
   * (death-drops disabled).
   */
  private void handleDeath(final Entity target, final SimTime time) {
    if (log.isInfoEnabled()) {
      log.info("Entity {} died", target.getId());
    }
    // don't set death if it is already dead.
    if (ed.getComponent(target.getId(), Dead.class) != null) {
      return;
    }
    target.set(new Dead(time.getTime()));
    if (ed.getComponent(target.getId(), Player.class) == null) {
      return;
    }
    final BodyPosition bp = ed.getComponent(target.getId(), BodyPosition.class);
    if (bp == null) {
      return;
    }
    if (prizeSystem == null) {
      prizeSystem = getSystem(PrizeSystem.class);
    }
    if (prizeSystem != null) {
      prizeSystem.spawnDeathPrize(target.getId(), bp.getLastLocation(), time.getTime());
    }
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
   * <p>Unattributed: no {@link DamageSource} sibling is stamped on the intent.
   * Reactors that fork on intent type (e.g. hit-feedback) treat absence of
   * {@link DamageSource} as "regen / unsourced." Use
   * {@link #damage(EntityId, int, EntityId, byte)} when the originator
   * (attacker / firing ship / world hazard) is known.
   *
   * @param entityId the entity to create a health change for
   * @param deltaHitPoints the change in hitpoints (can be both positive an negative)
   */
  public void damage(final EntityId entityId, final int deltaHitPoints) {
    final EntityId healthChange = ed.createEntity();
    ed.setComponents(healthChange, new Buff(entityId, 0), new HealthChange(deltaHitPoints));
  }

  /**
   * Attributed overload of {@link #damage(EntityId, int)} — emits the same
   * {@code HealthChange + Buff} intent plus a {@link DamageSource} sibling
   * carrying the originating entity and weapon family.
   *
   * <p>Replacement-as-Mutation slice 1 (.scratch/replacement-as-mutation/PRD.md):
   * the existing intent shape is wire-stable and stays the same; the new
   * {@link DamageSource} component lets reactors fork on intent type without
   * losing the legacy contract. Pass {@link EntityId#NULL_ID} +
   * {@link WeaponType#NONE} for unattributed paths (in which case prefer
   * {@link #damage(EntityId, int)} — same effect, less ceremony).
   *
   * @param entityId the entity whose Health pool should change
   * @param deltaHitPoints the delta (negative = damage, positive = heal)
   * @param source the originating entity (attacker for enemy hits; firing
   *     ship for self-cost-deduction; world / null for environmental)
   * @param weaponFlag one of the {@link WeaponType} byte constants;
   *     {@link WeaponType#NONE} for non-weapon paths
   */
  public void damage(
      final EntityId entityId,
      final int deltaHitPoints,
      final EntityId source,
      final byte weaponFlag) {
    final EntityId healthChange = ed.createEntity();
    ed.setComponents(
        healthChange,
        new Buff(entityId, 0),
        new HealthChange(deltaHitPoints),
        new DamageSource(source, weaponFlag));
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
