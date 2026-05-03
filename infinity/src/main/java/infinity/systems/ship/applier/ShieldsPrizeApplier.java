// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB (timed buff variant). Apply a temporary
 * {@code Shields} buff to the ship for {@code ShieldsTime} (1/100s).
 */
public final class ShieldsPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Shields prize not yet implemented (STATUS family — timed buff pending)");
  }
}
