// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Bumps {@link Rocket} inventory by one toward
 * {@link RocketMax}. No-op when the ship lacks {@link RocketMax} (=
 * disallowed) or is at the cap. {@code RocketThrust} / {@code RocketSpeed}
 * / {@code RocketTime} (Subspace canonical {@code [Rocket]} keys) drive
 * the active-rocket buff at fire-time and live in {@code ConsumableSystem}.
 */
public final class RocketPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RocketPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RocketMax max = ed.getComponent(ship, RocketMax.class);
    if (max == null) {
      return;
    }
    final Rocket curr = ed.getComponent(ship, Rocket.class);
    if (curr == null) {
      log.warn(
          "Ship {} has RocketMax but no Rocket — spawn projection invariant broken; skipping rocket prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up rocket prize and now has {} rockets", ship, next);
      ed.setComponent(ship, new Rocket(next));
    }
  }
}
