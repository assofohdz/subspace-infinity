// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * Strategy for applying one prize-pickup effect to a ship. One implementation
 * per Subspace prize type (BOMB, GUN, ENERGY, …) plus a {@link
 * CompositePrizeApplier} for compound prizes (ALLWEAPONS = bomb+burst+gun+mine,
 * BOMB = bomb+mine).
 *
 * <p>Replaces the switch + 10 inline {@code handleAcquireX} methods that
 * previously lived directly in {@code PrizeSystem}. Lookup happens via the
 * registry built in {@code PrizeSystem.initialize()}; missing keys are
 * logged-and-skipped so unimplemented prize types stay visible (one missing
 * map entry per type) without growing the dispatch ladder.
 */
@FunctionalInterface
public interface PrizeApplier {
  /**
   * Apply the prize effect to {@code ship}. Implementations are responsible
   * for their own no-op-when-already-at-cap and missing-component guards;
   * the dispatcher only handles missing-key (unimplemented prize) cases.
   */
  void apply(EntityId ship, PrizeApplierContext ctx);
}
