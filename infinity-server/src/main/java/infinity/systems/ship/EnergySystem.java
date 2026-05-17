// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.DamageSource;
import infinity.es.Dead;
import infinity.es.PrizeSpawnIntent;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.weapons.WeaponType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.systems.BaseInfinitySystem;
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

  private record TrackedApply(EntityId target, int delta) {}

  public EnergySystem() {
    // no-arg ctor — wiring happens in initialize()
  }

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
    // Last lethal-leaning DamageSource per target — folded change carries the most-recent negative
    // attribution. Reactors that need every-hit attribution must read pre-reap (see DamageSource test).
    final Map<EntityId, DamageSource> lastSourceByTarget = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final int delta = added.get(EnergyChange.class).delta();
      deltaByTarget.merge(ct.target(), delta, Integer::sum);
      final DamageSource src = ed.getComponent(added.getId(), DamageSource.class);
      if (src != null && delta < 0) {
        lastSourceByTarget.put(ct.target(), src);
      }
      final Decay decay = ed.getComponent(added.getId(), Decay.class);
      if (decay == null) {
        oneShotHolders.add(added.getId());
      } else {
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
      }
    }
    for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
      applyDelta(e.getKey(), e.getValue(), lastSourceByTarget.get(e.getKey()));
    }
    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }
    for (final Entity removed : changes.getRemovedEntities()) {
      final TrackedApply applied = trackedApplied.remove(removed.getId());
      if (applied == null) {
        continue;
      }
      applyDelta(applied.target(), -applied.delta(), null);
    }
  }

  private void applyDelta(final EntityId target, final int delta, final DamageSource lethalSrc) {
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
      handleDeath(targetEntity, lethalSrc);
    }
  }

  /** Mark dead (idempotent), emit a Channel A {@link PrizeSpawnIntent} (kill-credit on {@link ChangeTarget#source}), publish {@code playerKilled} for non-ECS consumers. */
  private void handleDeath(final Entity target, final DamageSource lethalSrc) {
    final long now = System.nanoTime();
    final EntityId killer = lethalSrc == null ? null : lethalSrc.getSource();
    final byte weaponFlag = lethalSrc == null ? WeaponType.NONE : lethalSrc.getWeaponFlag();
    if (log.isInfoEnabled()) {
      log.info("Entity {} died (killer={}, weapon={})", target.getId(), killer, weaponFlag);
    }
    if (ed.getComponent(target.getId(), Dead.class) != null) {
      return;
    }
    target.set(new Dead(now));
    // Death events fire for any ship (Player or Mob): bots count as kill targets per the
    // arena-modules smoke pipeline. Subspace canon scopes kill events to player ships
    // only — see legacy-vs-infinity.md (death-event scope).
    // Publish before the BodyPosition check — death is a fact regardless of drop-position availability.
    EventBus.publish(
        PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(target.getId(), killer, weaponFlag));
    final BodyPosition bp = ed.getComponent(target.getId(), BodyPosition.class);
    if (bp == null) {
      return;
    }
    final Vec3d deathPosition = bp.getLastLocation();
    final EntityId holder = ed.createEntity();
    // Kill-credit: ChangeTarget.source carries the killer EntityId (self when unattributed).
    final EntityId source = killer == null ? target.getId() : killer;
    ed.setComponents(
        holder,
        new ChangeTarget(target.getId(), source),
        new PrizeSpawnIntent(deathPosition, now));
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
