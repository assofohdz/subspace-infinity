// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip a {@code AntiWarpAvailable} marker on the
 * ship if the per-ship {@code AntiWarpStatus} setting permits it (0=never,
 * 1=acquirable-via-prize, 2=starts-with).
 */
public final class AntiWarpPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "AntiWarp prize not yet implemented (STATUS family pending)");
  }
}
