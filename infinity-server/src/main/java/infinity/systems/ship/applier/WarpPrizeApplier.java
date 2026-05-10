// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>INSTANT family.</b> Teleports the ship via {@code WarpSystem}.
 *
 * <p>Subspace canonical behaviour is "warp to a random arena spawn point".
 * Today's WarpSystem only exposes {@code warpToCenter}; the WARP prize
 * uses that as a near-equivalent until a multi-spawn-point selector
 * lands. Documented Infinity divergence per the prize-applier rule —
 * Subspace players will see "warps to center" instead of "warps to a
 * random safe location".
 */
public final class WarpPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    ctx.warpSystem().warpToCenter(ship);
  }
}
