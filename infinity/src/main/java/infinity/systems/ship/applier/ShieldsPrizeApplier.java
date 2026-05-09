// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB (timed buff variant). Apply a temporary
 * {@code Shields} buff to the ship for {@code ShieldsTime} (1/100s).
 *
 * <p>Subspace canon: REFERENCE.md {@code ## PrizeWeight} line 241 lists
 * {@code Shields} as a real Subspace prize, and {@code ## Cost} line 62
 * lists {@code Shield} as the per-prize point cost. The duration knob
 * is per-ship — {@code [Ship] ShieldsTime} (centiseconds, REFERENCE.md
 * "Turret / misc" line 430). There is no dedicated {@code ## Shields}
 * section enumerating effect-side knobs.
 *
 * <p>Note: Infinity's eventual implementation should derive the buff
 * lifetime from per-ship {@code ShieldsTime} via the standard typed
 * {@code *Config} → component → {@code Decay} pipeline (see
 * {@code .claude/rules/decay-ttl.md}). The activation effect itself
 * (damage immunity? energy shield? mphys collision filter?) is not
 * specified in REFERENCE.md and will be an Infinity-flavour design call
 * documented when the STUB is replaced.
 */
public final class ShieldsPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Shields prize not yet implemented (STATUS family — timed buff pending)");
  }
}
