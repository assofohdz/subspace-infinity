// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.DamageSource;
import infinity.es.Dead;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.Player;
import infinity.es.ship.weapons.WeaponType;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.PrizeSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical writer for the {@link Energy} component (the live energy
 * pool). Drains {@link EnergyChange} + {@link ChangeTarget} holder
 * entities each tick, folds same-tick deltas additively per target,
 * applies per-tick recharge driven by {@link EnergyStats#rechargePerSecond()},
 * clamps at {@link EnergyStats#max()}, triggers {@link Dead} on the
 * zero-energy edge, and spawns the death prize for {@link Player}
 * ships via {@link PrizeSystem}.
 *
 * <p><b>ADR 0001 canonical-writer recipe</b>
 * (see {@code .claude/rules/replacement-as-mutation.md}):
 *
 * <ul>
 *   <li>One {@link EntitySet} keyed on
 *       {@code (EnergyChange.class, ChangeTarget.class)} — Zay-ES
 *       component-type narrowing is the dispatch, no enum / registration
 *       table.
 *   <li>Per-tick fold by target — multi-source summing.
 *   <li>{@link Decay}-presence check at apply time distinguishes
 *       one-shot (destroy immediately) from temporary (track for
 *       reversal on the central decay reaper's removal signal).
 *   <li>Skip-no-op writes — if post-fold equals current, no
 *       {@code setComponent} call fires (RaM rule #6).
 *   <li>Recharge tick is encoded as positive per-tick
 *       {@code EnergyChange} entities emitted by this writer itself —
 *       so recharge participates in the same fold + clamp + skip-no-op
 *       pipeline as damage / cost-deductions.
 * </ul>
 *
 * <p><b>Critical: cache (target, delta) at apply-time for Decay-bound
 * Changes</b> (Phase 0 Task #3 finding). On Decay-driven removal, this
 * writer must NOT read {@link ChangeTarget} or {@link EnergyChange} off
 * the removed-entity snapshot. {@code DefaultEntityData.removeEntity}
 * walks {@code handlers.keySet()} (a {@link java.util.HashMap}, non-
 * deterministic order); by the time the writer's EntitySet sees the
 * {@code removedEntities} signal, either component may already be
 * null. We cache the {@code (target, delta)} tuple at apply-time
 * keyed by the Change holder's {@link EntityId} and reverse from the
 * cache on remove. Pattern lifted verbatim from
 * {@code CanonicalWriterDrainTest.TestStatSystem}.
 *
 * <p><b>System registration order:</b> this writer is registered
 * <em>before</em> the central {@code DecaySystem} in {@code GameServer}
 * so that on the tick a Decay-bound holder is reaped, the writer
 * drains the add (applies + caches) first, the reaper destroys the
 * entity second, and the writer reverses the delta on the next tick's
 * remove signal.
 *
 * <p><b>Supersedes</b> the pre-ADR {@code Buff(target, startTime) +
 * HealthChange(delta)} pair. Deferred-buff scheduling (the
 * {@code startTime} field) was audit-confirmed dead (Task #4) and
 * dropped without successor.
 *
 * @author Paul Speed (original), Asser Fahrenholz (ADR 0001 migration)
 */
public class EnergySystem extends BaseInfinitySystem {

  static Logger log = LoggerFactory.getLogger(EnergySystem.class);

  private EntityData ed;
  private EntitySet living;
  private EntitySet changes;
  private EntitySet rechargers;

  /**
   * Per-Change-entity record of "what we applied where" — captured at
   * apply-time so we can reverse on Decay-reaper removal without
   * depending on the removed-entity component snapshot (see class
   * Javadoc). Keyed by Change holder {@link EntityId}.
   */
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  /**
   * Resolved lazily inside the death branch so {@link EnergySystem} stays
   * usable in test fixtures that don't register {@link PrizeSystem}.
   */
  private PrizeSystem prizeSystem;

  /**
   * Pair captured at apply-time for a temporary Change entity: the
   * {@link ChangeTarget#target()} the writer mutated and the delta it
   * added (which must be subtracted on Decay-reaper removal).
   */
  private record TrackedApply(EntityId target, int delta) {}

  public EnergySystem() {
    // Nothing to do
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    // Watch every entity with a live pool — used for death detection
    // and as the cap-clamp set (the same entity also carries the
    // EnergyStats record carrying the cap).
    living = ed.getEntities(Energy.class, EnergyStats.class);
    // Canonical drain — Change holder entities.
    changes = ed.getEntities(EnergyChange.class, ChangeTarget.class);
    // Recharge pulse source — every ship with an Energy pool + stats
    // gets a per-tick positive EnergyChange emitted by THIS system so
    // recharge folds through the same canonical drain as damage.
    rechargers = ed.getEntities(Energy.class, EnergyStats.class);
  }

  @Override
  protected void terminate() {
    living.release();
    living = null;
    changes.release();
    changes = null;
    rechargers.release();
    rechargers = null;
  }

  @Override
  public void update(final SimTime time) {
    living.applyChanges();
    rechargers.applyChanges();
    changes.applyChanges();

    // Phase 1 — emit per-ship recharge Change holders. They will be
    // picked up by the same drain on this same tick because
    // applyChanges() is called again below for changes (no, actually,
    // we don't call again — added entities are not surfaced until the
    // next applyChanges()). Recharge fold therefore lands one tick
    // delayed, identical to damage. The old EnergySystem fold also
    // ran recharge through the same tick-deferred path
    // (Recharge emitted via damage() → Buff/HealthChange seen next
    // tick). Preserving that timing.
    emitRechargeChanges(time);

    // Phase 2 — drain the canonical EnergyChange + ChangeTarget set.
    drainEnergyChanges();
  }

  /**
   * Emit one positive {@link EnergyChange} per ship whose pool is
   * below cap. Each ship's recharge rate (energy/sec) is multiplied by
   * tick {@code tpf} and rounded to int — same arithmetic the legacy
   * applyRecharges used.
   */
  private void emitRechargeChanges(final SimTime time) {
    final double tpf = time.getTpf();
    if (tpf <= 0.0) {
      return;
    }
    for (final Entity e : rechargers) {
      final Energy pool = e.get(Energy.class);
      final EnergyStats stats = e.get(EnergyStats.class);
      if (pool.getEnergy() >= stats.max()) {
        // Already at cap — no recharge needed.
        continue;
      }
      final int charge = Math.toIntExact(Math.round(tpf * stats.rechargePerSecond()));
      if (charge <= 0) {
        continue;
      }
      final EntityId changeId = ed.createEntity();
      ed.setComponents(
          changeId, ChangeTarget.self(e.getId()), new EnergyChange(charge));
    }
  }

  /**
   * Canonical drain — apply on add (fold + clamp + skip-no-op), track
   * temporaries, reverse on Decay-reaper removal.
   */
  private void drainEnergyChanges() {
    // Phase 1 — fold added deltas by target; classify holders as
    // one-shot vs temporary based on Decay-presence.
    final Map<EntityId, Integer> deltaByTarget = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final int delta = added.get(EnergyChange.class).delta();
      deltaByTarget.merge(ct.target(), delta, Integer::sum);
      final Decay decay = ed.getComponent(added.getId(), Decay.class);
      if (decay == null) {
        oneShotHolders.add(added.getId());
      } else {
        // Cache (target, delta) under the holder's id so we can
        // reverse on Decay-reaper removal without depending on the
        // removed-entity component snapshot — see TrackedApply Javadoc.
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
      }
    }

    // Phase 2 — apply fold-sum per target, skip-no-op writes, death edge.
    for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
      applyDelta(e.getKey(), e.getValue());
    }

    // Phase 3 — destroy one-shot holders.
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }

    // Phase 4 — reverse delta on Decay-reaper removals. We do NOT read
    // the ChangeTarget off the removed Entity — see TrackedApply.
    for (final Entity removed : changes.getRemovedEntities()) {
      final TrackedApply applied = trackedApplied.remove(removed.getId());
      if (applied == null) {
        // Writer destroyed it itself (one-shot); the remove signal is
        // the trailing echo. Nothing to do.
        continue;
      }
      applyDelta(applied.target(), -applied.delta());
    }
  }

  /**
   * Apply a folded delta to the target's Energy pool: read current,
   * clamp at the cap (when an {@link EnergyStats} exists on the target),
   * skip no-op writes, trigger death on the zero edge.
   */
  private void applyDelta(final EntityId target, final int delta) {
    final Entity targetEntity = living.getEntity(target);
    if (targetEntity == null) {
      // Target has no Energy/EnergyStats pair — no pool to apply against
      // (also covers the no-cap path; today every ship has both).
      return;
    }
    final Energy current = targetEntity.get(Energy.class);
    final EnergyStats stats = targetEntity.get(EnergyStats.class);
    final int proposed = current.getEnergy() + delta;
    final int clamped = Math.min(proposed, stats.max());
    if (clamped == current.getEnergy()) {
      // RaM rule #6 — skip no-op writes.
      return;
    }
    ed.setComponent(target, new Energy(clamped));
    if (clamped <= 0) {
      handleDeath(targetEntity);
    }
  }

  /**
   * Mark {@code target} dead (idempotent — no-op if already {@link Dead})
   * and spawn a death prize at the body's last known location for
   * player ships. Filters for {@link Player} so non-ship dying entities
   * (any future Energy-bearing thing) don't trigger a prize.
   */
  private void handleDeath(final Entity target) {
    final long now = System.nanoTime();
    if (log.isInfoEnabled()) {
      log.info("Entity {} died", target.getId());
    }
    if (ed.getComponent(target.getId(), Dead.class) != null) {
      return;
    }
    target.set(new Dead(now));
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
      prizeSystem.spawnDeathPrize(target.getId(), bp.getLastLocation(), now);
    }
  }

  /**
   * Returns true if the entity has a live {@link Energy} pool.
   *
   * @param entityId the entityid to check
   * @return true if the entity has Energy, false if not
   */
  public boolean hasEnergy(final EntityId entityId) {
    return living.containsId(entityId);
  }

  /**
   * Returns the entity's current live {@link Energy} value (the
   * depleting pool).
   *
   * @param entityId the entityid to check
   * @return the live Energy of the entity
   */
  public int getHealth(final EntityId entityId) {
    return living.getEntity(entityId).get(Energy.class).getEnergy();
  }

  /**
   * Returns the entity's current effective energy cap
   * ({@link EnergyStats#max()}).
   *
   * @param entityId the entity to check
   * @return the current effective cap
   */
  public int getCap(final EntityId entityId) {
    return living.getEntity(entityId).get(EnergyStats.class).max();
  }

  /**
   * Unattributed emit — create a Change holder carrying
   * {@link ChangeTarget#self(EntityId)} + {@link EnergyChange}. No
   * {@link DamageSource} sibling; reactors that fork on intent type
   * (e.g. hit-feedback) treat absence of {@link DamageSource} as
   * "regen / unsourced." Use
   * {@link #damage(EntityId, int, EntityId, byte)} when the originator
   * is known.
   *
   * @param entityId the entity to apply a delta to
   * @param deltaHitPoints the change in pool value (positive = heal,
   *     negative = damage)
   */
  public void damage(final EntityId entityId, final int deltaHitPoints) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(entityId), new EnergyChange(deltaHitPoints));
  }

  /**
   * Attributed overload — emits a Change holder with a
   * {@link DamageSource} sibling carrying the originating entity and
   * weapon family. Used by {@code WeaponsDamageLogic} (direct hits,
   * splash) and {@code WeaponsEligibility.deductCostOfAttack} (self-
   * cost shape). The {@code source} field of the
   * {@link ChangeTarget} also records the attribution; the
   * {@link DamageSource} sibling additionally carries the
   * weapon-family discriminator.
   *
   * @param entityId the entity whose pool should change
   * @param deltaHitPoints the delta (negative = damage, positive = heal)
   * @param source the originating entity (attacker for enemy hits;
   *     firing ship for self-cost-deduction; {@link EntityId#NULL_ID}
   *     for world / unattributed)
   * @param weaponFlag one of the {@link WeaponType} byte constants;
   *     {@link WeaponType#NONE} for non-weapon paths
   */
  public void damage(
      final EntityId entityId,
      final int deltaHitPoints,
      final EntityId source,
      final byte weaponFlag) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(
        holder,
        new ChangeTarget(entityId, source),
        new EnergyChange(deltaHitPoints),
        new DamageSource(source, weaponFlag));
  }
}
