// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.SpeedChange;
import infinity.es.ship.SpeedStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TopSpeed prize — emits a {@link SpeedChange} delta (live cap bump). Drained by {@code SpeedSystem}; clamped at {@link SpeedStats#max()}. */
public final class TopSpeedPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(TopSpeedPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final SpeedStats stats = ed.getComponent(ship, SpeedStats.class);
    if (stats == null) {
      return;
    }
    final int delta = stats.upgrade();
    if (delta == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} topspeed upgrade: emitting SpeedChange delta={}", ship, delta);
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(changeId, ChangeTarget.self(ship), new SpeedChange(delta));
  }
}
