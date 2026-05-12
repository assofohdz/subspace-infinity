// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpActiveChange;
import infinity.es.ship.toggles.AntiwarpStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activates {@link AntiwarpActive} when {@link AntiwarpStats#statusTier()} permits. Subspace canonical
 * tri-state per REFERENCE.md {@code ## Antiwarp}. See ADR 0001 + {@link CloakPrizeApplier}.
 *
 * <p>Arena-global {@code [Toggle] AntiWarpPixels} (range) and {@code [Misc] AntiWarpSettleDelay}
 * are separate concerns, deferred to their own slices.
 */
public final class AntiWarpPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(AntiWarpPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final AntiwarpStats stats = ed.getComponent(ship, AntiwarpStats.class);
    if (stats == null || stats.statusTier() == 0) {
      return;
    }
    final AntiwarpActive current = ed.getComponent(ship, AntiwarpActive.class);
    if (current != null && current.isActive()) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up antiwarp prize (tier={}); emitting AntiwarpActiveChange(true)",
          ship, stats.statusTier());
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new AntiwarpActiveChange(true));
  }
}
