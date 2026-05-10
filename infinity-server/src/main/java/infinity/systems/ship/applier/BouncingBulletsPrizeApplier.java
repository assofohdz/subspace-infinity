// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB. Flip a {@code BouncingBulletsAvailable} marker
 * on the ship; bullets thereafter bounce off walls instead of dissipating.
 *
 * <p>Subspace canon: REFERENCE.md {@code ## PrizeWeight} line 240 lists
 * {@code BouncingBullets} as a real Subspace prize, and {@code ## Cost}
 * line 62 lists {@code Bounce} as the per-prize point cost. There is
 * <em>no</em> per-ship {@code BounceBullets} (or similar) knob in
 * REFERENCE.md — Subspace treats bullet-bounce as a flat boolean
 * capability flipped on by this prize.
 *
 * <p>Note: {@code [Misc] BounceFactor} (REFERENCE.md line 165) tunes
 * <em>wall</em> bounciness for ship contacts and is unrelated to this
 * prize. Don't conflate.
 */
public final class BouncingBulletsPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "BouncingBullets prize not yet implemented (STATUS family pending)");
  }
}
