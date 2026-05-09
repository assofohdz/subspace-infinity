// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip a {@code ProximityAvailable} marker on
 * the ship; bombs thereafter detonate when within proximity radius of an
 * enemy ship.
 *
 * <p>Subspace canonical knob (REFERENCE.md {@code ## Bomb} line 34):
 * {@code ProximityDistance} — radius of the proximity trigger in tiles.
 * Each Proximity pickup adds 1 tile to the effective radius (per-pickup
 * cumulative, capped per-ship). See REFERENCE.md {@code ## PrizeWeight}
 * line 240 ({@code Proximity}) for the prize-name registration.
 */
public final class ProximityPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Proximity prize not yet implemented (STATUS family pending)");
  }
}
