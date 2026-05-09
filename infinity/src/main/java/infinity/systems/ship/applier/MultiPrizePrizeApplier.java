// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>INSTANT family</b> — STUB. Apply {@code MultiPrizeCount} random other
 * prize types to the ship. Implementation will need access to the prize-type
 * weight distribution + the registry itself (recursive dispatch into other
 * appliers).
 *
 * <p>Subspace canonical knob (REFERENCE.md {@code ## Prize} line 218):
 * {@code MultiPrizeCount} — number of random "greens" granted by a single
 * MultiPrize pickup. The count is a global arena-wide knob, not per-ship.
 * See REFERENCE.md {@code ## PrizeWeight} line 242 ({@code MultiPrize}) for
 * the prize-name registration.
 */
public final class MultiPrizePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "MultiPrize prize not yet implemented (INSTANT family pending)");
  }
}
