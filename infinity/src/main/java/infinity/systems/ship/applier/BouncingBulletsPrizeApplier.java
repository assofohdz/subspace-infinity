// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip a {@code BouncingBulletsAvailable} marker
 * on the ship; bullets thereafter bounce off walls instead of dissipating.
 */
public final class BouncingBulletsPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "BouncingBullets prize not yet implemented (STATUS family pending)");
  }
}
