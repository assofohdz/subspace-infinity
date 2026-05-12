// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelChange;
import infinity.es.ship.actions.RepelStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Emits a one-shot {@link RepelChange}({@code +1})
 * Change holder; {@code RepelCountSystem} drains and clamps at
 * {@link RepelStats#max}. Subspace canon: per-ship {@code [Ship]
 * InitialRepel} / {@code RepelMax}; REFERENCE.md {@code ## PrizeWeight}
 * ({@code Repel}). The {@code [Repel]} tunables ({@code RepelSpeed},
 * {@code RepelTime}, {@code RepelDistance}) drive the spawned effect
 * entity at fire-time in {@code ConsumableSystem}, not here.
 *
 * @see infinity.systems.ship.RepelCountSystem
 */
public final class RepelPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RepelPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RepelStats stats = ed.getComponent(ship, RepelStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed repels
    }
    final Repel curr = ed.getComponent(ship, Repel.class);
    if (curr != null && curr.getCount() >= stats.max()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up repel prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new RepelChange(1));
  }
}
