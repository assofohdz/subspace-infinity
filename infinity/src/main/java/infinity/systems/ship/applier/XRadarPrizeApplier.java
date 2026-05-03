// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip an {@code XRadarAvailable} marker on the
 * ship if the per-ship {@code XRadarStatus} setting permits it.
 */
public final class XRadarPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "XRadar prize not yet implemented (STATUS family pending)");
  }
}
