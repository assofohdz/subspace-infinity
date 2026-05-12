// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.SimTime;
import infinity.BombLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.MineChange;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for live {@link MineCurrentLevel}; drains {@link MineChange}, clamping at {@link MineStats#max}. See ADR 0001 + {@link ThrustSystem}. */
public class MineSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;

  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, int delta) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(MineChange.class, ChangeTarget.class);
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
      final int delta = added.get(MineChange.class).delta();
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
    final MineCurrentLevel current = ed.getComponent(target, MineCurrentLevel.class);
    if (current == null || current.getLevel() == null) {
      return; // ship not allowed mines
    }
    final MineStats stats = ed.getComponent(target, MineStats.class);
    final BombLevel[] vals = BombLevel.values();
    final int currentOrdinal = current.getLevel().ordinal();
    final int proposed = currentOrdinal + delta;
    final int capOrdinal = stats == null || stats.max() == null ? vals.length - 1 : stats.max().ordinal();
    final int clamped = Math.max(0, Math.min(proposed, capOrdinal));
    if (clamped == currentOrdinal) {
      return;
    }
    ed.setComponent(target, new MineCurrentLevel(vals[clamped]));
  }
}
