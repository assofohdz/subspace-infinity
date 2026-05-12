// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.EnergyStatsChange;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for {@link EnergyStats}; drains {@link EnergyStatsChange} with per-field fold + clamp; companion to {@link EnergySystem} (live pool). See ADR 0001. */
public class EnergyStatsSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet stats;
  private EntitySet changes;

  // Cache payload-as-applied so Decay-reaper reversal can negate each present field.
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, EnergyStatsChange payload) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    stats = ed.getEntities(EnergyStats.class);
    changes = ed.getEntities(EnergyStatsChange.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    stats.release();
    stats = null;
    changes.release();
    changes = null;
  }

  @Override
  public void update(final SimTime time) {
    stats.applyChanges();
    changes.applyChanges();

    final Map<EntityId, EnergyStatsChange> folded = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final EnergyStatsChange payload = added.get(EnergyStatsChange.class);
      folded.merge(ct.target(), payload, EnergyStatsSystem::mergeChanges);
      if (ed.getComponent(added.getId(), Decay.class) == null) {
        oneShotHolders.add(added.getId());
      } else {
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), payload));
      }
    }
    for (final Map.Entry<EntityId, EnergyStatsChange> e : folded.entrySet()) {
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
      applyDelta(applied.target(), negate(applied.payload()));
    }
  }

  private void applyDelta(final EntityId target, final EnergyStatsChange payload) {
    final EnergyStats current = ed.getComponent(target, EnergyStats.class);
    if (current == null) {
      return;
    }
    final int nextHardMax = applyInt(current.hardMax(), payload.deltaHardMax());
    final int nextUpgrade = applyInt(current.upgrade(), payload.deltaUpgrade());
    int nextMax = applyInt(current.max(), payload.deltaMax());
    if (nextMax > nextHardMax) {
      nextMax = nextHardMax;
    }
    final double nextRechargeMax =
        applyDouble(current.rechargeMax(), payload.deltaRechargeMax());
    final double nextRechargeUpgrade =
        applyDouble(current.rechargeUpgrade(), payload.deltaRechargeUpgrade());
    double nextRechargePerSecond =
        applyDouble(current.rechargePerSecond(), payload.deltaRechargePerSecond());
    if (nextRechargePerSecond > nextRechargeMax) {
      nextRechargePerSecond = nextRechargeMax;
    }
    final EnergyStats next =
        new EnergyStats(
            nextMax,
            nextHardMax,
            nextUpgrade,
            nextRechargePerSecond,
            nextRechargeMax,
            nextRechargeUpgrade);
    if (next.equals(current)) {
      return;
    }
    ed.setComponent(target, next);
  }

  private static EnergyStatsChange mergeChanges(
      final EnergyStatsChange a, final EnergyStatsChange b) {
    return new EnergyStatsChange(
        sumInt(a.deltaMax(), b.deltaMax()),
        sumInt(a.deltaHardMax(), b.deltaHardMax()),
        sumInt(a.deltaUpgrade(), b.deltaUpgrade()),
        sumDouble(a.deltaRechargePerSecond(), b.deltaRechargePerSecond()),
        sumDouble(a.deltaRechargeMax(), b.deltaRechargeMax()),
        sumDouble(a.deltaRechargeUpgrade(), b.deltaRechargeUpgrade()));
  }

  private static EnergyStatsChange negate(final EnergyStatsChange p) {
    return new EnergyStatsChange(
        p.deltaMax() == null ? null : -p.deltaMax(),
        p.deltaHardMax() == null ? null : -p.deltaHardMax(),
        p.deltaUpgrade() == null ? null : -p.deltaUpgrade(),
        p.deltaRechargePerSecond() == null ? null : -p.deltaRechargePerSecond(),
        p.deltaRechargeMax() == null ? null : -p.deltaRechargeMax(),
        p.deltaRechargeUpgrade() == null ? null : -p.deltaRechargeUpgrade());
  }

  private static Integer sumInt(final Integer a, final Integer b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  private static Double sumDouble(final Double a, final Double b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  private static int applyInt(final int current, final Integer delta) {
    return delta == null ? current : current + delta;
  }

  private static double applyDouble(final double current, final Double delta) {
    return delta == null ? current : current + delta;
  }
}
