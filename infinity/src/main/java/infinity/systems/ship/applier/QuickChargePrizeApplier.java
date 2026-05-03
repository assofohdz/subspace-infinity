// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>INSTANT family.</b> "Full Charge" prize — refills the ship's live
 * {@code Health} pool back up to {@code Energy}. One-shot effect; no
 * persistent component is mutated. Delegates to
 * {@code EnergySystem.refillHealth(ship)} via the context.
 */
public final class QuickChargePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    ctx.energySystem().refillHealth(ship);
  }
}
