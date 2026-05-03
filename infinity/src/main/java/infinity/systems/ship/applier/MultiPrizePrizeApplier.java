// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>INSTANT family</b> — STUB. Apply {@code MultiPrizeCount} random other
 * prize types to the ship. Implementation will need access to the prize-type
 * weight distribution + the registry itself (recursive dispatch into other
 * appliers).
 */
public final class MultiPrizePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "MultiPrize prize not yet implemented (INSTANT family pending)");
  }
}
