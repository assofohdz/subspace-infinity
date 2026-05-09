// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>INVENTORY family</b> — STUB. On pickup, bumps the ship's current
 * shrapnel count by {@code ShrapnelRate}, clamped at {@code ShrapnelMax}.
 * Each bomb the ship subsequently fires emits this many shrapnel pieces on
 * detonation.
 *
 * <p>Subspace canonical knobs (REFERENCE.md {@code ## Shrapnel} line 284):
 * <ul>
 *   <li>{@code ShrapnelSpeed} — shrapnel travel speed
 *   <li>{@code InactiveShrapDamage} — damage during first 1/4 sec of life
 *   <li>{@code ShrapnelDamagePercent} — % of normal damage (1000 = 100%
 *       of L1 bullet)
 *   <li>{@code Random} (0/1) — 0 = circular pattern, 1 = random pattern
 * </ul>
 * Per-ship inventory knobs (REFERENCE.md "Shrapnel &amp; burst" line 406):
 * {@code ShrapnelMax} (0..31, max from one bomb) and {@code ShrapnelRate}
 * (0..31, per-prize increment). See {@code ## PrizeWeight} line 241.
 *
 * <p>Note: REFERENCE.md does not list {@code InitialShrapnel} as a per-ship
 * knob — Subspace seeds the count at zero and grows it via this prize.
 */
public final class ShrapnelPrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Shrapnel prize not yet implemented (CAPABILITY family pending)");
  }
}
