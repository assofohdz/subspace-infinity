// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>CAPABILITY family</b> — STUB. Bumps {@code Shrapnel} count by
 * {@code ShrapnelRate}, clamped at {@code ShrapnelMax}. Each bomb the ship
 * fires emits this many shrapnel pieces on detonation.
 */
public final class ShrapnelPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Shrapnel prize not yet implemented (CAPABILITY family pending)");
  }
}
