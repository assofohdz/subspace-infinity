// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>INSTANT family.</b> "Full Charge" prize — refills the ship's live
 * {@code Health} pool back up to {@code Energy}. One-shot effect; no
 * persistent component is mutated. Delegates to
 * {@code EnergySystem.refillHealth(ship)} via the context.
 *
 * <p>Subspace canonical encoding (REFERENCE.md {@code ## PrizeWeight} lines
 * 235-236, VIE↔UI naming inversion): the class name
 * {@code QuickChargePrizeApplier} follows the player-facing Continuum UI
 * label "Quick Charge" / "Full Charge"; the behaviour (one-shot refill)
 * matches the VIE {@code .ini}-canonical name {@code Recharge} at line 235
 * ({@code Recharge (= "Full Charge", not Recharge — VIE naming quirk)}). The
 * {@code .ini}-labeled {@code QuickCharge} entry at line 236 is actually the
 * recharge-rate boost — see {@link RechargePrizeApplier} for that mirror
 * case. Infinity's class naming follows the player-facing UI labels because
 * that's what arena authors and players think; per-ship Groovy fragments
 * and {@code ## PrizeWeight} fragment authoring still use the VIE
 * {@code .ini}-canonical names.
 *
 * <p>Per-ship cap: {@code [Ship] MaximumEnergy} bounds the refill ceiling
 * (the live {@code Health} pool is refilled up to current {@code Energy}).
 */
public final class QuickChargePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    ctx.energySystem().refillHealth(ship);
  }
}
