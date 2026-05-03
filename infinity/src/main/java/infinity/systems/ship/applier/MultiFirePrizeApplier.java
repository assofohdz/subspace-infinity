// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip a {@code MultiFireAvailable} marker on
 * the ship; gun fire thereafter shoots three bullets at {@code MultiFireAngle}
 * spread.
 */
public final class MultiFirePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "MultiFire prize not yet implemented (STATUS family pending)");
  }
}
