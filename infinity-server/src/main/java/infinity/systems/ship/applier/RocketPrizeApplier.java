// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketChange;
import infinity.es.ship.actions.RocketStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Emits a one-shot {@link RocketChange}({@code +1})
 * Change holder; {@code RocketSystem} drains and clamps at
 * {@link RocketStats#max}. Subspace canon: per-ship {@code [Ship]
 * InitialRocket} / {@code RocketMax}; REFERENCE.md {@code ## PrizeWeight}
 * ({@code Rocket}). The {@code [Rocket]} tunables drive the active-rocket
 * buff at fire-time in {@code ConsumableSystem}, not here.
 *
 * @see infinity.systems.ship.RocketSystem
 */
public final class RocketPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RocketPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RocketStats stats = ed.getComponent(ship, RocketStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed rockets
    }
    final Rocket curr = ed.getComponent(ship, Rocket.class);
    if (curr != null && curr.getCount() >= stats.max()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up rocket prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new RocketChange(1));
  }
}
