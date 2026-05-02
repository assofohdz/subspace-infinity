/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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
