// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB (timed buff variant). Apply a temporary
 * {@code Super} (invulnerability + extra damage) buff for {@code SuperTime}
 * (1/100s).
 *
 * <p>Subspace canonical knobs:
 * per-ship {@code SuperTime} (REFERENCE.md line 429) — Super duration in
 * centiseconds (×10 → ms at the loader boundary). The prize is named
 * {@code AllWeapons} in canon (REFERENCE.md {@code ## PrizeWeight} line 241,
 * client-facing display "Super!").
 */
public final class SuperPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Super prize not yet implemented (STATUS family — timed buff pending)");
  }
}
