// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Bumps {@link Brick} inventory by one toward
 * {@link BrickMax}. No-op when the ship lacks {@link BrickMax} (= disallowed)
 * or is at the cap. {@code BrickTime} / {@code BrickSpan} (Subspace
 * canonical {@code [Brick]} keys) are consumed at fire-time by
 * {@code ConsumableSystem}, not here.
 */
public final class BrickPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(BrickPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BrickMax max = ed.getComponent(ship, BrickMax.class);
    if (max == null) {
      return;
    }
    final Brick curr = ed.getComponent(ship, Brick.class);
    if (curr == null) {
      log.warn(
          "Ship {} has BrickMax but no Brick — spawn projection invariant broken; skipping brick prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up brick prize and now has {} bricks", ship, next);
      ed.setComponent(ship, new Brick(next));
    }
  }
}
