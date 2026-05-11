// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.Parent;
import infinity.es.ship.actions.RocketActive;
import infinity.es.ship.actions.RocketBuff;
import infinity.es.ship.actions.RocketSnapshot;
import infinity.systems.BaseInfinitySystem;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages {@link RocketActive} marker on the parent ship across buff-entity add/remove. Thrust/Speed revert is handled by {@code ThrustSystem}/{@code SpeedSystem} via Decay-bound deltas. */
public final class RocketBuffSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(RocketBuffSystem.class);

  private EntityData ed;
  private EntitySet buffs;

  // Cache shipId at add-time — Zay-ES may null Parent on the removed buff entity.
  private final Map<EntityId, EntityId> shipByBuff = new HashMap<>();

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    buffs = ed.getEntities(RocketBuff.class, Parent.class, RocketSnapshot.class);
  }

  @Override
  protected void terminate() {
    if (buffs != null) {
      buffs.release();
      buffs = null;
    }
    shipByBuff.clear();
  }

  @Override
  public void update(final SimTime time) {
    buffs.applyChanges();
    for (final Entity added : buffs.getAddedEntities()) {
      onBuffAdded(added);
    }
    for (final Entity removed : buffs.getRemovedEntities()) {
      onBuffRemoved(removed.getId());
    }
  }

  private void onBuffAdded(final Entity buff) {
    final Parent parent = buff.get(Parent.class);
    if (parent == null) {
      return;
    }
    final EntityId shipId = parent.getParentEntityId();
    shipByBuff.put(buff.getId(), shipId);
    ed.setComponent(shipId, new RocketActive());
    if (log.isInfoEnabled()) {
      log.info("Rocket buff active on ship {} — RocketActive marker installed", shipId);
    }
  }

  private void onBuffRemoved(final EntityId buffId) {
    final EntityId shipId = shipByBuff.remove(buffId);
    if (shipId == null) {
      return;
    }
    ed.removeComponent(shipId, RocketActive.class);
    if (log.isInfoEnabled()) {
      log.info("Rocket buff expired on ship {} — RocketActive marker stripped", shipId);
    }
  }
}
