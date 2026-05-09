// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * Strategy for applying one prize-pickup effect to a ship. One implementation
 * per Subspace prize type (BOMB, GUN, ENERGY, …) plus a {@link
 * CompositePrizeApplier} for compound prizes (ALLWEAPONS = bomb+burst+bullet+mine,
 * BOMB = bomb+mine).
 *
 * <p>Replaces the switch + 10 inline {@code handleAcquireX} methods that
 * previously lived directly in {@code PrizeSystem}. Lookup happens via the
 * registry built in {@code PrizeSystem.initialize()}; missing keys are
 * logged-and-skipped so unimplemented prize types stay visible (one missing
 * map entry per type) without growing the dispatch ladder.
 *
 * <p><strong>Implementation contract:</strong> implementations must cite
 * REFERENCE.md per {@code .claude/rules/prize-applier.md} — the lead-paragraph
 * Javadoc names the relevant section ({@code ## Prize}, {@code ## PrizeWeight},
 * {@code ## Bomb}, {@code ## Repel}, per-ship section, etc.) and identifies
 * the canonical Subspace knobs that drive behaviour. Knob-driven prizes get
 * rich anchors (knob list + units + encoding); stat-bump prizes get minimal
 * anchors (one-line section cite). Divergences from canon are flagged inline
 * as {@code <p>Note: ...} paragraphs.
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
