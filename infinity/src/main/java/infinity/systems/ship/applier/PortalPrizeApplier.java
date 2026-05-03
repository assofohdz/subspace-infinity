// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Bumps {@link Portal} inventory by one toward
 * {@link PortalMax}. No-op when the ship lacks {@link PortalMax} (=
 * disallowed) or is at the cap. {@code WarpPointDelay} (Subspace
 * canonical {@code [Misc]} key for portal active duration) is consumed
 * by {@code ConsumableSystem} when the portal is dropped, not here.
 */
public final class PortalPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(PortalPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final PortalMax max = ed.getComponent(ship, PortalMax.class);
    if (max == null) {
      return;
    }
    final Portal curr = ed.getComponent(ship, Portal.class);
    if (curr == null) {
      log.warn(
          "Ship {} has PortalMax but no Portal — spawn projection invariant broken; skipping portal prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up portal prize and now has {} portals", ship, next);
      ed.setComponent(ship, new Portal(next));
    }
  }
}
