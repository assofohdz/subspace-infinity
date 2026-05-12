// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickChange;
import infinity.es.ship.actions.BrickStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Emits a one-shot {@link BrickChange}({@code +1})
 * Change holder; {@code BrickSystem} drains and clamps at
 * {@link BrickStats#max}. No-op when ship not equipped for bricks.
 * Subspace canon: per-ship {@code [Ship] InitialBrick} / {@code BrickMax};
 * REFERENCE.md {@code ## PrizeWeight} ({@code Brick}). The {@code [Brick]}
 * tunables ({@code BrickTime}, {@code BrickSpan}) are consumed at
 * fire-time by {@code ConsumableSystem}, not here.
 *
 * @see infinity.systems.ship.BrickSystem
 */
public final class BrickPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(BrickPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BrickStats stats = ed.getComponent(ship, BrickStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed bricks
    }
    final Brick curr = ed.getComponent(ship, Brick.class);
    if (curr != null && curr.getCount() >= stats.max()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up brick prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new BrickChange(1));
  }
}
