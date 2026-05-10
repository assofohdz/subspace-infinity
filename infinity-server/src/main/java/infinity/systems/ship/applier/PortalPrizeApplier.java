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
 * <b>INVENTORY family.</b> Bumps {@link Portal} inventory by one toward
 * {@link PortalMax}.
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialPortal} /
 * {@code PortalMax} inventory caps (REFERENCE.md "Inventory caps and
 * starts" line 372/374). {@code [Misc] WarpPointDelay} is the dropped
 * portal's active duration (REFERENCE.md {@code ## Misc} line 163 — the
 * "WarpPoint" name is the legacy alias for portal; see
 * {@code .claude/rules/settings-pipeline.md}). Consumed by
 * {@code ConsumableSystem} when the portal is dropped, not here.
 * See {@code ## PrizeWeight} line 242.
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
