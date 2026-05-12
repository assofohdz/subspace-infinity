// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.BulletLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BulletChange;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletStats;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for live {@link BulletCurrentLevel}; drains {@link BulletChange}, clamping at {@link BulletStats#max}. See ADR 0001 + {@link ThrustSystem}. */
public class BulletSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;

  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, int delta) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(BulletChange.class, ChangeTarget.class);
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
      final int delta = added.get(BulletChange.class).delta();
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
    final BulletCurrentLevel current = ed.getComponent(target, BulletCurrentLevel.class);
    if (current == null || current.getLevel() == null) {
      return; // ship not allowed bullets
    }
    final BulletStats stats = ed.getComponent(target, BulletStats.class);
    final BulletLevel[] vals = BulletLevel.values();
    final int currentOrdinal = current.getLevel().ordinal();
    final int proposed = currentOrdinal + delta;
    final int capOrdinal = stats == null || stats.max() == null ? vals.length - 1 : stats.max().ordinal();
    final int clamped = Math.max(0, Math.min(proposed, capOrdinal));
    if (clamped == currentOrdinal) {
      return;
    }
    ed.setComponent(target, new BulletCurrentLevel(vals[clamped]));
  }
}
