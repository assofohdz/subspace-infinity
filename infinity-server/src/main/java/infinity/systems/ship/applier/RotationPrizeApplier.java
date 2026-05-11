// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.RotationChange;
import infinity.es.ship.RotationStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rotation prize — emits a {@link RotationChange} delta (live cap bump). Drained by {@code RotationSystem}; clamped at {@link RotationStats#max()}. */
public final class RotationPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RotationPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RotationStats stats = ed.getComponent(ship, RotationStats.class);
    if (stats == null) {
      return;
    }
    final double delta = stats.upgrade();
    if (Double.compare(delta, 0.0) == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} rotation upgrade: emitting RotationChange delta={}", ship, delta);
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(changeId, ChangeTarget.self(ship), new RotationChange(delta));
  }
}
