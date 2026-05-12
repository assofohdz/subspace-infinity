// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalChange;
import infinity.es.ship.actions.PortalStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Emits a one-shot {@link PortalChange}({@code +1})
 * Change holder; {@code PortalSystem} drains and clamps at
 * {@link PortalStats#max}. Subspace canon: per-ship {@code [Ship]
 * InitialPortal} / {@code PortalMax}; REFERENCE.md {@code ## PrizeWeight}
 * ({@code WarpPoint}). {@code [Misc] WarpPointDelay} is the dropped
 * portal's active duration and is consumed by {@code ConsumableSystem}
 * when the portal is dropped, not here.
 *
 * @see infinity.systems.ship.PortalSystem
 */
public final class PortalPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(PortalPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final PortalStats stats = ed.getComponent(ship, PortalStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed portals
    }
    final Portal curr = ed.getComponent(ship, Portal.class);
    if (curr != null && curr.getCount() >= stats.max()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up portal prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new PortalChange(1));
  }
}
