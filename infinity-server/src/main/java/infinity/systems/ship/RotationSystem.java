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
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationChange;
import infinity.es.ship.RotationStats;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for live {@link Rotation}; drains {@link RotationChange}. See ADR 0001 + {@link EnergySystem}. */
public class RotationSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet stats;
  private EntitySet changes;

  // Cache (target, delta) at apply-time — Zay-ES may null components on the
  // removed Change holder; see EnergySystem for the full rationale.
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, double delta) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    stats = ed.getEntities(Rotation.class, RotationStats.class);
    changes = ed.getEntities(RotationChange.class, ChangeTarget.class);
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
    drainRotationChanges();
  }

  private void drainRotationChanges() {
    final Map<EntityId, Double> deltaByTarget = new HashMap<>();
    final Map<EntityId, Boolean> targetIsTemporary = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final double delta = added.get(RotationChange.class).delta();
      deltaByTarget.merge(ct.target(), delta, Double::sum);
      final Decay decay = ed.getComponent(added.getId(), Decay.class);
      if (decay == null) {
        oneShotHolders.add(added.getId());
      } else {
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
        targetIsTemporary.put(ct.target(), Boolean.TRUE);
      }
    }

    for (final Map.Entry<EntityId, Double> e : deltaByTarget.entrySet()) {
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

  private void applyDelta(final EntityId target, final double delta, final boolean temporary) {
    final Rotation current = ed.getComponent(target, Rotation.class);
    if (current == null) {
      return;
    }
    final RotationStats targetStats = ed.getComponent(target, RotationStats.class);
    // Bypass clamp for temporary deltas: rocket-buff-style overrides may exceed RotationStats.max.
    final double clamped =
        (temporary || targetStats == null)
            ? proposed(current, delta)
            : Math.min(proposed(current, delta), targetStats.max());
    if (Double.compare(clamped, current.getRadSec()) == 0) {
      return;
    }
    ed.setComponent(target, new Rotation(clamped));
  }

  private static double proposed(final Rotation current, final double delta) {
    return current.getRadSec() + delta;
  }
}
