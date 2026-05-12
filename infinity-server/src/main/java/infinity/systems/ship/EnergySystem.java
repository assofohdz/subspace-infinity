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
import infinity.systems.BaseInfinitySystem;
import infinity.systems.PrizeSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Canonical writer for live {@link Energy}; drains {@link EnergyChange}, emits per-tick recharge, triggers {@link Dead} on zero edge. See ADR 0001. */
public class EnergySystem extends BaseInfinitySystem {

  static Logger log = LoggerFactory.getLogger(EnergySystem.class);

  private EntityData ed;
  private EntitySet living;
  private EntitySet changes;
  private EntitySet rechargers;

  // Cache (target, delta) at apply-time — Zay-ES may null components on the
  // removed Change holder; reverse from cache on Decay-reaper removal.
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  // Lazy-resolved so EnergySystem stays usable in test fixtures without PrizeSystem.
  private PrizeSystem prizeSystem;

  private record TrackedApply(EntityId target, int delta) {}

  public EnergySystem() {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    living = ed.getEntities(Energy.class, EnergyStats.class);
    changes = ed.getEntities(EnergyChange.class, ChangeTarget.class);
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

    // Recharge folds one tick delayed (added entities surface on next applyChanges) — matches damage timing.
    emitRechargeChanges(time);
    drainEnergyChanges();
  }

  /** Emit one positive {@link EnergyChange} per ship below cap; rate × tpf rounded to int. */
  private void emitRechargeChanges(final SimTime time) {
    final double tpf = time.getTpf();
    if (tpf <= 0.0) {
      return;
    }
    for (final Entity e : rechargers) {
      final Energy pool = e.get(Energy.class);
      final EnergyStats stats = e.get(EnergyStats.class);
      if (pool.getEnergy() >= stats.max()) {
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

  private void drainEnergyChanges() {
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
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
      }
    }
    for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
      applyDelta(e.getKey(), e.getValue());
    }
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }
    for (final Entity removed : changes.getRemovedEntities()) {
      final TrackedApply applied = trackedApplied.remove(removed.getId());
      if (applied == null) {
        continue;
      }
      applyDelta(applied.target(), -applied.delta());
    }
  }

  private void applyDelta(final EntityId target, final int delta) {
    final Entity targetEntity = living.getEntity(target);
    if (targetEntity == null) {
      return;
    }
    final Energy current = targetEntity.get(Energy.class);
    final EnergyStats stats = targetEntity.get(EnergyStats.class);
    final int clamped = Math.min(current.getEnergy() + delta, stats.max());
    if (clamped == current.getEnergy()) {
      return;
    }
    ed.setComponent(target, new Energy(clamped));
    if (clamped <= 0) {
      handleDeath(targetEntity);
    }
  }

  /** Mark dead (idempotent) + spawn death prize for {@link Player} ships. */
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

  public boolean hasEnergy(final EntityId entityId) {
    return living.containsId(entityId);
  }

  public int getHealth(final EntityId entityId) {
    return living.getEntity(entityId).get(Energy.class).getEnergy();
  }

  public int getCap(final EntityId entityId) {
    return living.getEntity(entityId).get(EnergyStats.class).max();
  }

  /** Unattributed emit — no {@link DamageSource} sibling. */
  public void damage(final EntityId entityId, final int deltaHitPoints) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(entityId), new EnergyChange(deltaHitPoints));
  }

  /** Attributed emit — adds {@link DamageSource} sibling with weapon family. */
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
