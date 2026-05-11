// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;

/**
 * <b>INSTANT family.</b> "Full Charge" prize — refills the ship's live
 * {@link Energy} pool back up to its current effective cap
 * ({@link EnergyStats#max()}). One-shot effect; no persistent stats
 * mutation.
 *
 * <p><b>ADR 0001 migration.</b> Was previously
 * {@code ctx.energySystem().refillHealth(ship)} which created a
 * {@code Buff + HealthChange} intent. Now emits the Change-entity
 * shape directly at the applier site per
 * {@code .claude/rules/replacement-as-mutation.md} — applier reads
 * the current {@code Energy} + {@code EnergyStats.max}, computes the
 * delta, and emits one {@link ChangeTarget#self(EntityId)} +
 * {@link EnergyChange} holder. No {@code Decay} = one-shot, drained
 * and destroyed by {@code EnergySystem} next tick. RaM rule #6 skip-
 * no-op is enforced by the canonical writer; the applier still
 * early-returns on delta == 0 to skip the holder-entity allocation.
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
 * (the live {@link Energy} pool is refilled up to current
 * {@code EnergyStats.max}).
 */
public final class QuickChargePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Energy current = ed.getComponent(ship, Energy.class);
    final EnergyStats stats = ed.getComponent(ship, EnergyStats.class);
    if (current == null || stats == null) {
      return;
    }
    final int delta = stats.max() - current.getEnergy();
    if (delta == 0) {
      return;
    }
    final EntityId changeId = ed.createEntity();
    ed.setComponents(changeId, ChangeTarget.self(ship), new EnergyChange(delta));
  }
}
