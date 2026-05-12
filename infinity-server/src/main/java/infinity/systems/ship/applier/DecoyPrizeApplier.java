// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyChange;
import infinity.es.ship.actions.DecoyStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Emits a one-shot {@link DecoyChange}({@code +1})
 * Change holder; {@code DecoySystem} drains and clamps at
 * {@link DecoyStats#max}. Subspace canon: per-ship {@code [Ship]
 * InitialDecoy} / {@code DecoyMax}; REFERENCE.md {@code ## PrizeWeight}
 * ({@code Decoy}). {@code [Misc] DecoyAliveTime} drives the dropped
 * decoy's {@code Decay} lifetime and is consumed at fire-time by
 * {@code ConsumableSystem}, not here.
 *
 * @see infinity.systems.ship.DecoySystem
 */
public final class DecoyPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(DecoyPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final DecoyStats stats = ed.getComponent(ship, DecoyStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed decoys
    }
    final Decoy curr = ed.getComponent(ship, Decoy.class);
    if (curr != null && curr.getCount() >= stats.max()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up decoy prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new DecoyChange(1));
  }
}
