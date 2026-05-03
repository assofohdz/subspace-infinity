// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * Compound prize applier — runs a fixed sequence of leaf appliers. Used for
 * Subspace's two compound prize types:
 *
 * <ul>
 *   <li>{@code BOMB} prize → {@link BombPrizeApplier} + {@link MinePrizeApplier}
 *       (a "bomb" prize also bumps the ship's mine level — Subspace tradition)
 *   <li>{@code ALLWEAPONS} prize → {@code Bomb + Burst + Gun + Mine}
 * </ul>
 *
 * <p>Children fire in declaration order. If a child throws (e.g. a stub
 * applier that's not yet implemented), the throw propagates and aborts the
 * remaining children — the registry-level {@code try/catch} in
 * {@code PrizeSystem.handlePrizeAcquisition} converts it to a log warning.
 * Today no compound includes a stub child, so this is a defensive guarantee.
 */
public final class CompositePrizeApplier implements PrizeApplier {

  private final PrizeApplier[] children;

  public CompositePrizeApplier(final PrizeApplier... children) {
    this.children = children;
  }

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    for (final PrizeApplier child : children) {
      child.apply(ship, ctx);
    }
  }
}
