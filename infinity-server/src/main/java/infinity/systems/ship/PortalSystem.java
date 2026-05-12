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
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalChange;
import infinity.es.ship.actions.PortalStats;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for live {@link Portal} (count); drains {@link PortalChange}, clamping at {@link PortalStats#max} above and {@code 0} below. See ADR 0001 + {@link BurstSystem}. */
public class PortalSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;

  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, int delta) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(PortalChange.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    changes.release();
    changes = null;
  }

  @Override
  public void update(final SimTime time) {
    changes.applyChanges();
    drainChanges();
  }

  private void drainChanges() {
    final Map<EntityId, Integer> deltaByTarget = new HashMap<>();
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final int delta = added.get(PortalChange.class).delta();
      deltaByTarget.merge(ct.target(), delta, Integer::sum);
      if (ed.getComponent(added.getId(), Decay.class) == null) {
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
    final PortalStats stats = ed.getComponent(target, PortalStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed portals
    }
    final Portal current = ed.getComponent(target, Portal.class);
    final int currentCount = current == null ? 0 : current.getCount();
    final int proposed = currentCount + delta;
    final int clamped = Math.max(0, Math.min(proposed, stats.max()));
    if (clamped == currentCount) {
      return;
    }
    ed.setComponent(target, new Portal(clamped));
  }
}
