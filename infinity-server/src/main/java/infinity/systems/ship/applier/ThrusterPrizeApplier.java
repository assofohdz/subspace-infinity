// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.ThrustChange;
import infinity.es.ship.ThrustStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thruster prize — emits a {@link ThrustChange} delta (live cap bump). Drained by {@code ThrustSystem}; clamped at {@link ThrustStats#max()}. */
public final class ThrusterPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(ThrusterPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final ThrustStats stats = ed.getComponent(ship, ThrustStats.class);
    if (stats == null) {
      return;
    }
    final int delta = stats.upgrade();
    if (delta == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} thruster upgrade: emitting ThrustChange delta={}", ship, delta);
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(changeId, ChangeTarget.self(ship), new ThrustChange(delta));
  }
}
