// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstChange;
import infinity.es.ship.actions.BurstStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Emits a one-shot {@link BurstChange}({@code +1})
 * Change holder; {@code BurstSystem} drains and clamps at
 * {@link BurstStats#max}. Subspace canon: per-ship {@code [Ship] InitialBurst} /
 * {@code BurstMax}; REFERENCE.md {@code ## PrizeWeight} ({@code Burst}).
 *
 * @see infinity.systems.ship.BurstSystem
 */
public final class BurstPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(BurstPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BurstStats stats = ed.getComponent(ship, BurstStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed bursts
    }
    final Burst burst = ed.getComponent(ship, Burst.class);
    final int current = burst == null ? 0 : burst.getCount();
    if (current >= stats.max()) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up burst prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new BurstChange(1));
  }
}
