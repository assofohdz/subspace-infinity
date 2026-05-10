// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.toggles.XRadar;
import infinity.es.ship.toggles.XRadarStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>STATUS family.</b> Activates the {@link XRadar} toggle on the ship
 * when the per-ship {@link XRadarStatus} permits it.
 *
 * <p>Subspace canonical encoding (REFERENCE.md "Ship abilities"):
 * {@code XRadarStatus} is tri-state — {@code 0} = forbidden (this prize
 * is a no-op), {@code 1} = acquirable via prize, {@code 2} = acquirable
 * + starts active at spawn. Apply respects that signal: if
 * {@code XRadarStatus} is missing or {@code 0}, no-op. Otherwise stamp
 * {@code XRadar(true)} so the {@code StatusDrainSystem} starts draining
 * energy at the per-ship rate from {@code XRadarEnergy}.
 */
public final class XRadarPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(XRadarPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final XRadarStatus status = ed.getComponent(ship, XRadarStatus.class);
    if (status == null || status.getStatus() == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up xradar prize (status={}); enabling XRadar toggle",
          ship, status.getStatus());
    }
    ed.setComponent(ship, new XRadar(true));
  }
}
