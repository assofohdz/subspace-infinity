// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarActiveChange;
import infinity.es.ship.toggles.XRadarStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activates {@link XRadarActive} when {@link XRadarStats#statusTier()} permits. Subspace canonical
 * tri-state per REFERENCE.md {@code ## XRadar}. See ADR 0001 + {@link CloakPrizeApplier}.
 */
public final class XRadarPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(XRadarPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final XRadarStats stats = ed.getComponent(ship, XRadarStats.class);
    if (stats == null || stats.statusTier() == 0) {
      return;
    }
    final XRadarActive current = ed.getComponent(ship, XRadarActive.class);
    if (current != null && current.isActive()) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up xradar prize (tier={}); emitting XRadarActiveChange(true)",
          ship, stats.statusTier());
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new XRadarActiveChange(true));
  }
}
