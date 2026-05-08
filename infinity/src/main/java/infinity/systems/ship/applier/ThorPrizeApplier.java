// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorMaxCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Increments {@link ThorCurrentCount} by 1 if under
 * {@link ThorMaxCount}. Allowed iff {@code ThorMaxCount > 0 && current < max}.
 * First-time acquisition seeds count=1 and a fallback {@code ThorFireDelay(1000)}
 * — note the hardcoded delay is a pre-existing carryover from
 * {@code handleAcquireThor}; properly it should come from {@code ShipConfig.thors.fireDelayCs}
 * (which {@code ShipSpawnSystem} already projects), but the original code
 * overwrites it on first acquisition. Preserved bit-for-bit.
 */
public final class ThorPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(ThorPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final ThorCurrentCount thorCurrentCount = ed.getComponent(ship, ThorCurrentCount.class);
    final ThorMaxCount thorMaxCount = ed.getComponent(ship, ThorMaxCount.class);
    if (thorMaxCount == null || thorMaxCount.getCount() <= 0) {
      return; // ship not allowed thors
    }
    if (thorCurrentCount != null && thorCurrentCount.getCount() < thorMaxCount.getCount()) {
      final ThorCurrentCount thorNextCount = thorCurrentCount.add(1);
      if (log.isInfoEnabled()) {
        log.info(
            "Ship {} picked up thor prize and now has {} thor", ship, thorNextCount.getCount());
      }
      ed.setComponent(ship, thorNextCount);
    } else if (thorCurrentCount == null) {
      log.info("Ship {} picked up thor prize", ship);
      ed.setComponent(ship, new ThorCurrentCount(1));
      ed.setComponent(ship, new ThorFireDelay(1000));
    }
  }
}
