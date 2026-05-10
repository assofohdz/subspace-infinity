// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.toggles.Stealth;
import infinity.es.ship.toggles.StealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>STATUS family.</b> Activates the {@link Stealth} toggle on the ship
 * when the per-ship {@link StealthStatus} permits it.
 *
 * <p>Subspace canonical encoding (REFERENCE.md "Ship abilities"):
 * {@code StealthStatus} is tri-state — {@code 0} = forbidden (this prize
 * is a no-op), {@code 1} = acquirable via prize, {@code 2} = acquirable
 * + starts active at spawn. Apply respects that signal: if
 * {@code StealthStatus} is missing or {@code 0}, no-op. Otherwise
 * stamp {@code Stealth(true)} so the {@code StatusDrainSystem} starts
 * draining energy at the per-ship rate from {@code StealthEnergy}.
 */
public final class StealthPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(StealthPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final StealthStatus status = ed.getComponent(ship, StealthStatus.class);
    if (status == null || status.getStatus() == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up stealth prize (status={}); enabling Stealth toggle",
          ship, status.getStatus());
    }
    ed.setComponent(ship, new Stealth(true));
  }
}
