// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Bumps the ship's {@link Repel} count by one toward
 * {@link RepelMax}. No-op when the ship has no {@link RepelMax} component
 * (= ship not allowed repels) or is already at the cap.
 *
 * <p>Pure component read/write — no {@link infinity.config.ShipConfig}
 * access on the hot path. Applier only adjusts inventory; {@code RepelTime},
 * {@code RepelDistance}, {@code RepelSpeed} (Subspace canonical
 * {@code [Repel]} keys) describe what happens when the repel is fired and
 * belong to {@code ConsumableSystem}, not here.
 */
public final class RepelPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RepelPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RepelMax max = ed.getComponent(ship, RepelMax.class);
    if (max == null) {
      return; // ship not allowed repels
    }
    final Repel curr = ed.getComponent(ship, Repel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has RepelMax but no Repel — spawn projection invariant broken; skipping repel prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up repel prize and now has {} repels", ship, next);
      ed.setComponent(ship, new Repel(next));
    }
  }
}
