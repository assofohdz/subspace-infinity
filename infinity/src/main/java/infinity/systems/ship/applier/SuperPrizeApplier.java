// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB (timed buff variant). Apply a temporary
 * {@code Super} (invulnerability + extra damage) buff for {@code SuperTime}
 * (1/100s).
 */
public final class SuperPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Super prize not yet implemented (STATUS family — timed buff pending)");
  }
}
