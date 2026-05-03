// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.toggles.Cloak;
import infinity.es.ship.toggles.CloakStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>STATUS family.</b> Activates the {@link Cloak} toggle on the ship
 * when the per-ship {@link CloakStatus} permits it.
 *
 * <p>Subspace canonical encoding (REFERENCE.md "Ship abilities"):
 * {@code CloakStatus} is tri-state — {@code 0} = forbidden (this prize
 * is a no-op), {@code 1} = acquirable via prize, {@code 2} = acquirable
 * + starts active at spawn. Apply respects that signal: if
 * {@code CloakStatus} is missing or {@code 0}, no-op (matches Subspace
 * "ship can't cloak"). Otherwise stamp {@code Cloak(true)} so the
 * {@code StatusDrainSystem} starts draining energy at the per-ship
 * rate from {@code CloakEnergy}.
 */
public final class CloakPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(CloakPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final CloakStatus status = ed.getComponent(ship, CloakStatus.class);
    if (status == null || status.getStatus() == 0) {
      // Capability forbidden for this ship type — Subspace canonical no-op.
      return;
    }
    log.info("Ship {} picked up cloak prize (status={}); enabling Cloak toggle",
        ship, status.getStatus());
    ed.setComponent(ship, new Cloak(true));
  }
}
