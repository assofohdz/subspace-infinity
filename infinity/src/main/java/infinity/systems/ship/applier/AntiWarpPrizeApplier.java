// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.toggles.Antiwarp;
import infinity.es.ship.toggles.AntiwarpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>STATUS family.</b> Activates the {@link Antiwarp} toggle on the ship
 * when the per-ship {@link AntiwarpStatus} permits it.
 *
 * <p>Subspace canonical encoding (REFERENCE.md "Ship abilities"):
 * {@code AntiWarpStatus} is tri-state — {@code 0} = forbidden (this
 * prize is a no-op), {@code 1} = acquirable via prize, {@code 2} =
 * acquirable + starts active at spawn. Apply respects that signal: if
 * {@code AntiwarpStatus} is missing or {@code 0}, no-op. Otherwise
 * stamp {@code Antiwarp(true)} so the {@code StatusDrainSystem} starts
 * draining energy at the per-ship rate from {@code AntiwarpEnergy}.
 *
 * <p>Note: arena-global {@code [Toggle] AntiWarpPixels} (effective
 * range) and {@code [Misc] AntiWarpSettleDelay} (post-warp grace) are
 * separate concerns — deferred to the polish bag, not part of Slice 6b.
 */
public final class AntiWarpPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(AntiWarpPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final AntiwarpStatus status = ed.getComponent(ship, AntiwarpStatus.class);
    if (status == null || status.getStatus() == 0) {
      return;
    }
    log.info("Ship {} picked up antiwarp prize (status={}); enabling Antiwarp toggle",
        ship, status.getStatus());
    ed.setComponent(ship, new Antiwarp(true));
  }
}
