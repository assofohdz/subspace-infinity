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
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakActiveChange;
import infinity.systems.BaseInfinitySystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical writer for {@link CloakActive}; drains {@link CloakActiveChange}. See ADR 0001 + {@link EnergySystem}. */
public class CloakSystem extends BaseInfinitySystem {

  private EntityData ed;
  private EntitySet changes;

  // Cache (target, previousValue) at apply-time — see EnergySystem class Javadoc for rationale.
  private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

  private record TrackedApply(EntityId target, boolean previousValue) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    changes = ed.getEntities(CloakActiveChange.class, ChangeTarget.class);
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
    final List<EntityId> oneShotHolders = new ArrayList<>();
    for (final Entity added : changes.getAddedEntities()) {
      final ChangeTarget ct = added.get(ChangeTarget.class);
      final boolean newValue = added.get(CloakActiveChange.class).newValue();
      final Decay decay = ed.getComponent(added.getId(), Decay.class);
      final boolean previous = applyValue(ct.target(), newValue);
      if (decay == null) {
        oneShotHolders.add(added.getId());
      } else {
        trackedApplied.put(added.getId(), new TrackedApply(ct.target(), previous));
      }
    }

    for (final EntityId id : oneShotHolders) {
      ed.removeEntity(id);
    }

    for (final Entity removed : changes.getRemovedEntities()) {
      final TrackedApply applied = trackedApplied.remove(removed.getId());
      if (applied == null) {
        continue;
      }
      applyValue(applied.target(), applied.previousValue());
    }
  }

  /** Apply newValue; return previous value (for reverse-on-remove cache). */
  private boolean applyValue(final EntityId target, final boolean newValue) {
    final CloakActive current = ed.getComponent(target, CloakActive.class);
    final boolean previous = current != null && current.isActive();
    if (previous == newValue) {
      return previous;
    }
    ed.setComponent(target, new CloakActive(newValue));
    return previous;
  }
}
