// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip a {@code ProximityAvailable} marker on
 * the ship; bombs thereafter detonate when within proximity radius of an
 * enemy ship.
 */
public final class ProximityPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Proximity prize not yet implemented (STATUS family pending)");
  }
}
