// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakActiveChange;
import infinity.es.ship.toggles.CloakStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activates {@link CloakActive} when {@link CloakStats#statusTier()} permits. Subspace canonical tri-state
 * per REFERENCE.md {@code ## Cloak} — 0=forbidden (no-op), 1=acquirable, 2=starts-active. See ADR 0001.
 */
public final class CloakPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(CloakPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final CloakStats stats = ed.getComponent(ship, CloakStats.class);
    if (stats == null || stats.statusTier() == 0) {
      // Capability forbidden for this ship type — Subspace canonical no-op.
      return;
    }
    final CloakActive current = ed.getComponent(ship, CloakActive.class);
    if (current != null && current.isActive()) {
      // Already on — emitting the Change would be a no-op the writer skips. Short-circuit here.
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up cloak prize (tier={}); emitting CloakActiveChange(true)",
          ship, stats.statusTier());
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new CloakActiveChange(true));
  }
}
