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
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedChange;
import infinity.es.ship.SpeedStats;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for live {@link Speed}; drains {@link SpeedChange}. See ADR 0001 + {@link EnergySystem}. */
public class SpeedSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet stats;
  private EntitySet changes;

  // Cache (target, delta) at apply-time — Zay-ES may null components on the
  // removed Change holder; see EnergySystem for the full rationale.
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, int delta) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    stats = ed.getEntities(Speed.class, SpeedStats.class);
    changes = ed.getEntities(SpeedChange.class, ChangeTarget.class);
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
    drainSpeedChanges();
  }

  private void drainSpeedChanges() {
    final Map<EntityId, Integer> deltaByTarget = new HashMap<>();
    final Map<EntityId, Boolean> targetIsTemporary = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final int delta = added.get(SpeedChange.class).delta();
      deltaByTarget.merge(ct.target(), delta, Integer::sum);
      final Decay decay = ed.getComponent(added.getId(), Decay.class);
      if (decay == null) {
        oneShotHolders.add(added.getId());
      } else {
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
        targetIsTemporary.put(ct.target(), Boolean.TRUE);
      }
    }

    for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
      final boolean temporary = Boolean.TRUE.equals(targetIsTemporary.get(e.getKey()));
      applyDelta(e.getKey(), e.getValue(), temporary);
    }

    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }

    for (final Entity removed : changes.getRemovedEntities()) {
      final TrackedApply applied = trackedApplied.remove(removed.getId());
      if (applied == null) {
        continue;
      }
      applyDelta(applied.target(), -applied.delta(), true);
    }
  }

  private void applyDelta(final EntityId target, final int delta, final boolean temporary) {
    final Speed current = ed.getComponent(target, Speed.class);
    if (current == null) {
      return;
    }
    final SpeedStats stats = ed.getComponent(target, SpeedStats.class);
    final int proposed = current.getSpeed() + delta;
    // Bypass clamp for temporary deltas: rocket-buff RocketSpeed (e.g. 4000) exceeds SpeedStats.max (e.g. 3250).
    final int clamped = (temporary || stats == null) ? proposed : Math.min(proposed, stats.max());
    if (clamped == current.getSpeed()) {
      return;
    }
    ed.setComponent(target, new Speed(clamped));
  }
}
