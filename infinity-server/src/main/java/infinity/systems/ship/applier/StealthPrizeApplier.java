// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.toggles.StealthActiveChange;
import infinity.es.ship.toggles.StealthStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activates {@link StealthActive} when {@link StealthStats#statusTier()} permits. Subspace canonical
 * tri-state per REFERENCE.md {@code ## Stealth}. See ADR 0001 + {@link CloakPrizeApplier}.
 */
public final class StealthPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(StealthPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final StealthStats stats = ed.getComponent(ship, StealthStats.class);
    if (stats == null || stats.statusTier() == 0) {
      return;
    }
    final StealthActive current = ed.getComponent(ship, StealthActive.class);
    if (current != null && current.isActive()) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up stealth prize (tier={}); emitting StealthActiveChange(true)",
          ship, stats.statusTier());
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new StealthActiveChange(true));
  }
}
